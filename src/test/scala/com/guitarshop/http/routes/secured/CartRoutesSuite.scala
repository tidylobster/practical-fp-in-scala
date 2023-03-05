package com.guitarshop.http.routes.secured

import cats.data.Kleisli
import cats.effect.IO
import com.guitarshop.domain.auth._
import com.guitarshop.domain.cart._
import com.guitarshop.domain.item._
import com.guitarshop.generators._
import com.guitarshop.http.auth.users._
import com.guitarshop.http.routes.CartRoutes
import com.guitarshop.services.ShoppingCart
import com.guitarshop.suite.HttpSuite
import org.http4s.server._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.dsl.io._
import org.http4s.Method._
import org.http4s.circe.CirceEntityCodec._
import squants.market.USD

object CartRoutesSuite extends HttpSuite {

  def authMiddleware(authUser: CommonUser): AuthMiddleware[IO, CommonUser] =
    AuthMiddleware(Kleisli.pure(authUser))

  def dataCart(cartTotal: CartTotal): ShoppingCart[IO] =
    new TestShoppingCart {
      override def get(userId: UserId): IO[CartTotal] = IO.pure(cartTotal)
    }

  test("GET shopping cart succeeds") {
    val gen = for {
      u <- commonUserGen
      c <- cartTotalGen
    } yield u -> c

    forall(gen) { case (user, ct) =>
      val req = GET(uri"/cart")
      val routes = CartRoutes[IO](dataCart(ct)).routes(authMiddleware(user))
      expectHttpBodyAndStatus(routes, req)(ct, Status.Ok)
    }
  }

  test("POST add item to a shopping cart") {
    val gen = for {
      u <- commonUserGen
      c <- cartTotalGen
    } yield u -> c

    forall(gen) { case (user, ct) =>
      val req = POST(ct, uri"/cart")
      val routes = CartRoutes[IO](new TestShoppingCart).routes(authMiddleware(user))
      expectHttpStatus(routes, req)(Status.Created)
    }
  }
}

protected class TestShoppingCart() extends ShoppingCart[IO] {
  override def add(userId: UserId, itemId: ItemId, quantity: Quantity): IO[Unit] =
    IO.unit
  override def get(userId: UserId): IO[CartTotal] = IO.pure(CartTotal(List.empty, USD(0)))
  override def delete(userId: UserId): IO[Unit] = IO.unit
  override def removeItem(userId: UserId, itemId: ItemId): IO[Unit] = IO.unit
  override def update(userId: UserId, cart: Cart): IO[Unit] = IO.unit
}
