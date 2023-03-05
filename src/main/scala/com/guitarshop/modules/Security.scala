package com.guitarshop.modules

import com.guitarshop.auth._
import com.guitarshop.config.types._
import com.guitarshop.domain.auth._
import com.guitarshop.http.auth.users._
import com.guitarshop.services._

import cats.ApplicativeThrow
import cats.effect._
import cats.syntax.all._
import dev.profunktor.auth.jwt._
import dev.profunktor.redis4cats.RedisCommands
import eu.timepit.refined.auto._
import io.circe.parser.{decode => jsonDecode}
import pdi.jwt._
import skunk.Session

object Security {
  def make[F[_]: Sync](
      config: AppConfig,
      postgres: Resource[F, Session[F]],
      redis: RedisCommands[F, String, String]
  ): F[Security[F]] = {

    val adminJwtAuth: AdminJwtAuth =
      AdminJwtAuth(
        JwtAuth.hmac(
          config.adminJwtConfig.secretKey.value.secret,
          JwtAlgorithm.HS256
        )
      )

    val userJwtAuth: UserJwtAuth =
      UserJwtAuth(
        JwtAuth.hmac(
          config.tokenConfig.value.secret,
          JwtAlgorithm.HS256
        )
      )

    val adminToken = JwtToken(config.adminJwtConfig.adminToken.value.secret)

    for {
      adminClaim <- jwtDecode[F](adminToken, adminJwtAuth.value)
      content <- ApplicativeThrow[F].fromEither(
        jsonDecode[ClaimContent](adminClaim.content)
      )
      adminUser = AdminUser(User(UserId(content.value), UserName("admin")))
      tokens <- JwtExpire
        .make[F]
        .map(Tokens.make[F](_, config.tokenConfig.value, config.tokenExpiration))
      crypto <- Crypto.make[F](config.passwordSalt.value)
      users = Users.make[F](postgres)
      auth = Auth.make[F](config.tokenExpiration, tokens, users, redis, crypto)
      adminAuth = UsersAuth.admin[F](adminToken, adminUser)
      userAuth = UsersAuth.common[F](redis)
    } yield new Security[F](auth, adminAuth, userAuth, adminJwtAuth, userJwtAuth) {}
  }
}

sealed abstract class Security[F[_]] private (
    val auth: Auth[F],
    val adminAuth: UsersAuth[F, AdminUser],
    val usersAuth: UsersAuth[F, CommonUser],
    val adminJwtAuth: AdminJwtAuth,
    val userJwtAuth: UserJwtAuth
)
