package com.guitarshop.auth

import cats.effect._
import cats.implicits._
import com.guitarshop.config.types.TokenExpiration
import com.guitarshop.effects.JwtClock
import pdi.jwt.JwtClaim

trait JwtExpire[F[_]] {
  def expireIn(claim: JwtClaim, exp: TokenExpiration): F[JwtClaim]
}

object JwtExpire {
  def make[F[_]: Sync]: F[JwtExpire[F]] =
    JwtClock[F].utc.map { implicit jClock => (claim: JwtClaim, exp: TokenExpiration) =>
      Sync[F].delay(claim.issuedNow.expiresIn(exp.value.toMillis))
    }
}
