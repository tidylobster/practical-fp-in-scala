package com.guitarshop.services

import com.guitarshop.domain.auth._
import com.guitarshop.http.auth.users._
import cats._
import cats.implicits._
import com.guitarshop.auth.{Crypto, Tokens}
import com.guitarshop.config.types.TokenExpiration
import com.guitarshop.domain._
import io.circe.parser.decode
import dev.profunktor.auth.jwt.JwtToken
import dev.profunktor.redis4cats.RedisCommands
import io.circe.syntax._
import pdi.jwt.JwtClaim

trait Auth[F[_]] {
  def newUser(username: UserName, password: Password): F[JwtToken]
  def login(userName: UserName, password: Password): F[JwtToken]
  def logout(token: JwtToken, userName: UserName): F[Unit]
}

trait UsersAuth[F[_], A] {
  def findUser(token: JwtToken)(claim: JwtClaim): F[Option[A]]
}

object Auth {
  def make[F[_]: MonadThrow](
      tokenExpiration: TokenExpiration,
      tokens: Tokens[F],
      users: Users[F],
      redis: RedisCommands[F, String, String],
      crypto: Crypto
  ): Auth[F] =
    new Auth[F] {
      override def newUser(username: UserName, password: Password): F[JwtToken] =
        users.find(username).flatMap {
          case Some(_) => UserNameInUse(username).raiseError[F, JwtToken]
          case None =>
            for {
              i <- users.create(username, crypto.encrypt(password))
              t <- tokens.create
              u = User(i, username).asJson.noSpaces
              _ <- redis.setEx(t.value, u, tokenExpiration.value)
              _ <- redis.setEx(username.show, t.value, tokenExpiration.value)
            } yield t
        }

      override def login(username: UserName, password: Password): F[JwtToken] =
        users.find(username).flatMap {
          case None => UserNotFound(username).raiseError[F, JwtToken]
          case Some(user) if user.password =!= crypto.encrypt(password) =>
            InvalidPassword(username).raiseError[F, JwtToken]
          case Some(user) =>
            redis.get(username.show).flatMap {
              case Some(t) => JwtToken(t).pure[F]
              case None =>
                tokens.create.flatMap { t =>
                  redis.setEx(t.value, user.asJson.noSpaces, tokenExpiration.value) *>
                    redis.setEx(username.show, t.value, tokenExpiration.value).as(t)
                }
            }
        }

      override def logout(token: JwtToken, username: UserName): F[Unit] =
        redis.del(token.show) *> redis.del(username.show).void
    }
}

object UsersAuth {
  def common[F[_]: Functor](redis: RedisCommands[F, String, String]): UsersAuth[F, CommonUser] =
    new UsersAuth[F, CommonUser] {
      override def findUser(token: JwtToken)(claim: JwtClaim): F[Option[CommonUser]] =
        redis
          .get(token.value)
          .map {
            _.flatMap { u =>
              decode[User](u).toOption.map(CommonUser.apply)
            }
          }
    }

  def admin[F[_]: Applicative](
      adminToken: JwtToken,
      adminUser: AdminUser
  ): UsersAuth[F, AdminUser] =
    new UsersAuth[F, AdminUser] {
      override def findUser(token: JwtToken)(claim: JwtClaim): F[Option[AdminUser]] =
        (token === adminToken)
          .guard[Option]
          .as(adminUser)
          .pure[F]
    }
}
