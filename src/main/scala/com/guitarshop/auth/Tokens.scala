package com.guitarshop.auth

import cats.Monad
import cats.syntax.all._
import com.guitarshop.config.types._
import com.guitarshop.effects.GenUUID
import dev.profunktor.auth.jwt._
import io.circe.syntax._
import pdi.jwt._

trait Tokens[F[_]] {
  def create: F[JwtToken]
}

object Tokens {
  def make[F[_]: GenUUID: Monad](
      jwtExpire: JwtExpire[F],
      config: JwtAccessTokenKeyConfig,
      exp: TokenExpiration
  ): Tokens[F] =
    new Tokens[F] {
      override def create: F[JwtToken] =
        for {
          uuid <- GenUUID[F].make
          claim <- jwtExpire.expireIn(JwtClaim(uuid.asJson.noSpaces), exp)
          secretKey = JwtSecretKey(config.secret.value)
          token <- jwtEncode[F](claim, secretKey, JwtAlgorithm.HS256)
        } yield token
    }
}
