package com.guitarshop.http.routes.admin

import com.guitarshop.domain.item._
import com.guitarshop.ext.http4s.refined._
import com.guitarshop.http.auth.users.AdminUser
import com.guitarshop.services.Items

import cats.MonadThrow
import cats.syntax.all._
import io.circe.JsonObject
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe.JsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server._

final case class AdminItemRoutes[F[_]: JsonDecoder: MonadThrow](items: Items[F])
    extends Http4sDsl[F] {
  private[admin] val prefixPath = "/items"

  private val httpRoutes: AuthedRoutes[AdminUser, F] =
    AuthedRoutes.of {

      // Create new item
      case ar @ POST -> Root as _ =>
        ar.req.decodeR[CreateItemParam] { item =>
          items.create(item.toDomain).flatMap { id =>
            Created(JsonObject.singleton("item_id", id.asJson))
          }
        }

      // Update item
      case ar @ PUT -> Root as _ =>
        ar.req.decodeR[UpdateItemParam] { item =>
          items.update(item.toDomain) >> Ok()
        }
    }

  def routes(authMiddleware: AuthMiddleware[F, AdminUser]): HttpRoutes[F] =
    Router(prefixPath -> authMiddleware(httpRoutes))
}
