package com.guitarshop.http.routes.admin

import com.guitarshop.http.auth.users._
import com.guitarshop.services.Brands
import com.guitarshop.domain.brand._
import com.guitarshop.ext.http4s.refined._

import cats.MonadThrow
import cats.implicits._
import io.circe.JsonObject
import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.JsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, Router}

final case class AdminBrandRoutes[F[_]: JsonDecoder: MonadThrow](brands: Brands[F])
    extends Http4sDsl[F] {
  private[admin] val prefixPath = "/brands"

  private val httpRoutes: AuthedRoutes[AdminUser, F] =
    AuthedRoutes.of { case ar @ POST -> Root as _ =>
      ar.req.decodeR[BrandParam] { bp =>
        brands.create(bp.toDomain).flatMap { id =>
          Created(JsonObject.singleton("brand_id", id.asJson))
        }
      }
    }

  def routes(authMiddleware: AuthMiddleware[F, AdminUser]): HttpRoutes[F] =
    Router(prefixPath -> authMiddleware(httpRoutes))
}
