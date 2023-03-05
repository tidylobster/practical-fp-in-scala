package com.guitarshop.http.routes

import com.guitarshop.services.Orders
import com.guitarshop.domain.order._
import com.guitarshop.http.auth.users._
import com.guitarshop.http.vars.OrderIdVar

import cats.Monad
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl
import org.http4s._
import org.http4s.server._

final case class OrderRoutes[F[_]: Monad](
    orders: Orders[F]
) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/orders"

  private val httpRoutes: AuthedRoutes[CommonUser, F] =
    AuthedRoutes.of {
      case GET -> Root as user                       => Ok(orders.findBy(user.value.id))
      case GET -> Root / OrderIdVar(orderId) as user => Ok(orders.get(user.value.id, orderId))
    }

  def routes(authMiddleware: AuthMiddleware[F, CommonUser]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(httpRoutes)
  )
}
