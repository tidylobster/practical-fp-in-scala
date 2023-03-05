package com.guitarshop.http.routes

import cats.MonadThrow
import cats.implicits._
import com.guitarshop.domain.cart._
import com.guitarshop.domain.checkout._
import com.guitarshop.domain.order._
import com.guitarshop.ext.http4s.refined._
import com.guitarshop.http.auth.users._
import com.guitarshop.programs.Checkout
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.JsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.server.{AuthMiddleware, Router}

final case class CheckoutRoutes[F[_]: JsonDecoder: MonadThrow](checkout: Checkout[F])
    extends Http4sDsl[F] {
  private[routes] val prefixPath = "/checkout"

  private val httpRoutes: AuthedRoutes[CommonUser, F] =
    AuthedRoutes.of { case ar @ POST -> Root as user =>
      ar.req.decodeR[Card] { card =>
        checkout
          .process(user.value.id, card)
          .flatMap(Created(_))
          .recoverWith {
            case CartNotFound(userId)   => NotFound(s"Cart not found for user ${userId.value}")
            case EmptyCartError         => BadRequest("Shopping cart is empty!")
            case e: OrderOrPaymentError => BadRequest(e.show)
          }
      }
    }

  def routes(authMiddleware: AuthMiddleware[F, CommonUser]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(httpRoutes)
  )
}
