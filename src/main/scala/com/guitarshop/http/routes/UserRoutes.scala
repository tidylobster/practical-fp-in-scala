package com.guitarshop.http.routes

import cats.MonadThrow
import cats.implicits._
import com.guitarshop.services.Auth
import com.guitarshop.domain.auth._
import com.guitarshop.domain._
import com.guitarshop.ext.http4s.refined._
import org.http4s.circe.JsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.server.Router

final case class UserRoutes[F[_]: JsonDecoder: MonadThrow](auth: Auth[F]) extends Http4sDsl[F] {
  private[routes] val prefixPath = "/auth"

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] { case req @ POST -> Root / "users" =>
      req.decodeR[CreateUser] { user =>
        auth
          .newUser(user.username.toDomain, user.password.toDomain)
          .flatMap(Created(_))
          .recoverWith { case UserNameInUse(u) =>
            Conflict(u.show)
          }
      }
    }

  val routes: HttpRoutes[F] = Router(prefixPath -> httpRoutes)
}
