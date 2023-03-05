package com.guitarshop.programs

import cats.data.NonEmptyList
import cats.effect.{IO, Ref}
import cats.implicits._
import com.guitarshop.domain.auth._
import com.guitarshop.domain.cart._
import com.guitarshop.domain.checkout._
import com.guitarshop.domain.item._
import com.guitarshop.domain.order._
import com.guitarshop.domain.payment._
import com.guitarshop.effects.{TestBackground, TestRetry}
import com.guitarshop.http.clients.PaymentClient
import com.guitarshop.services.{Orders, ShoppingCart}
import org.scalacheck.Gen
import org.typelevel.log4cats.noop.NoOpLogger
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry.RetryPolicies.limitRetries
import retry.RetryPolicy
import squants.market.{Money, USD}
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.control.NoStackTrace

object CheckoutSuite extends SimpleIOSuite with Checkers {
  implicit val bg = TestBackground.NoOp
  implicit val lg = NoOpLogger[IO]

  def successfulClient(pid: PaymentId): PaymentClient[IO] =
    _ => IO.pure(pid)

  val unreachableClient: PaymentClient[IO] =
    _ => IO.raiseError(PaymentError(""))

  def recoveringClient(attemptsSoFar: Ref[IO, Int], paymentId: PaymentId): PaymentClient[IO] =
    _ =>
      attemptsSoFar.get.flatMap {
        case n if n === 1 =>
          IO.pure(paymentId)
        case _ => attemptsSoFar.update(_ + 1) *> IO.raiseError(PaymentError(""))
      }

  def successfulCart(cartTotal: CartTotal): ShoppingCart[IO] =
    new TestCart {
      override def get(userId: UserId): IO[CartTotal] = IO.pure(cartTotal)
      override def delete(userId: UserId): IO[Unit] = IO.unit
    }

  val emptyCart: ShoppingCart[IO] =
    new TestCart {
      override def get(userId: UserId): IO[CartTotal] =
        IO.pure(CartTotal(List.empty, USD(0)))
    }

  def failingCart(cartTotal: CartTotal): ShoppingCart[IO] =
    new TestCart {
      override def get(userId: UserId): IO[CartTotal] = IO.pure(cartTotal)
      override def delete(userId: UserId): IO[Unit] = IO.raiseError(new NoStackTrace {})
    }

  def successfulOrders(oid: OrderId): Orders[IO] =
    new TestOrders {
      override def create(
          userId: UserId,
          paymentId: PaymentId,
          items: NonEmptyList[CartItem],
          total: Money
      ): IO[OrderId] = IO.pure(oid)
    }

  val failingOrders: Orders[IO] =
    new TestOrders {
      override def create(
          userId: UserId,
          paymentId: PaymentId,
          items: NonEmptyList[CartItem],
          total: Money
      ): IO[OrderId] =
        IO.raiseError(OrderError(""))
    }

  val MaxRetries = 3
  val retryPolicy: RetryPolicy[IO] =
    limitRetries[IO](MaxRetries)

  import com.guitarshop.generators._

  val gen: Gen[(UserId, PaymentId, OrderId, CartTotal, Card)] = for {
    uid <- userIdGen
    pid <- paymentIdGen
    oid <- orderIdGen
    crt <- cartTotalGen
    crd <- cardGen
  } yield (uid, pid, oid, crt, crd)

  test("successful checkout") {
    forall(gen) { case (uid, pid, oid, ct, card) =>
      Checkout[IO](successfulClient(pid), successfulCart(ct), successfulOrders(oid), retryPolicy)
        .process(uid, card)
        .map(expect.same(oid, _))
    }
  }

  test("empty cart") {
    forall(gen) { case (uid, pid, oid, _, card) =>
      Checkout[IO](successfulClient(pid), emptyCart, successfulOrders(oid), retryPolicy)
        .process(uid, card)
        .attempt
        .map {
          case Left(EmptyCartError) => success
          case _                    => failure("Cart was not empty as expected")
        }
    }
  }

  test("unreachable payment client") {
    forall(gen) { case (uid, _, oid, ct, card) =>
      Ref.of[IO, Option[GivingUp]](None).flatMap { retries =>
        implicit val rh = TestRetry.givingUp(retries)

        Checkout[IO](unreachableClient, successfulCart(ct), successfulOrders(oid), retryPolicy)
          .process(uid, card)
          .attempt
          .flatMap {
            case Left(PaymentError(_)) =>
              retries.get.map {
                case Some(g) => expect.same(g.totalRetries, MaxRetries)
                case None    => failure("Expected GivingUp")
              }
            case _ => IO.pure(failure("Expected payment error"))
          }
      }
    }
  }

  test("failing payment client succeeds after one retry") {
    forall(gen) { case (uid, pid, oid, ct, card) =>
      (
        Ref.of[IO, Option[WillDelayAndRetry]](None),
        Ref.of[IO, Int](0)
      ).tupled
        .flatMap { case (retries, cliRef) =>
          implicit val rh = TestRetry.recovering(retries)

          Checkout[IO](
            recoveringClient(cliRef, pid),
            successfulCart(ct),
            successfulOrders(oid),
            retryPolicy
          )
            .process(uid, card)
            .attempt
            .flatMap {
              case Right(id) =>
                retries.get.map {
                  case Some(w) => expect.same(id, oid) |+| expect.same(0, w.retriesSoFar)
                  case None    => failure("Expected one retry")
                }
              case Left(_) =>
                IO.pure(failure("Expected PaymentId"))
            }

        }
    }
  }

  test("cannot create order, run in the background") {
    forall(gen) { case (uid, pid, _, ct, card) =>
      (
        Ref.of[IO, (Int, FiniteDuration)](0 -> 0.seconds),
        Ref.of[IO, Option[GivingUp]](None)
      ).tupled
        .flatMap { case (acc, retries) =>
          implicit val bg = TestBackground.counter(acc)
          implicit val rh = TestRetry.givingUp(retries)

          Checkout[IO](
            successfulClient(pid),
            successfulCart(ct),
            failingOrders,
            retryPolicy
          ).process(uid, card)
            .attempt
            .flatMap {
              case Left(OrderError(_)) =>
                (acc.get, retries.get).mapN {
                  case (c, Some(g)) =>
                    expect.same(c, 1 -> 1.hour) |+| expect.same(g.totalRetries, MaxRetries)
                  case _ =>
                    failure(s"Expected $MaxRetries retries and reschedule")
                }
              case _ => IO.pure(failure("Expected order error"))
            }
        }
    }
  }

  test("failing to delete cart does not affect checkout") {
    forall(gen) { case (uid, pid, oid, ct, card) =>
      Checkout[IO](successfulClient(pid), failingCart(ct), successfulOrders(oid), retryPolicy)
        .process(uid, card)
        .map(expect.same(oid, _))
    }
  }
}

protected class TestCart() extends ShoppingCart[IO] {
  override def add(userId: UserId, itemId: ItemId, quantity: Quantity): IO[Unit] = ???
  override def get(userId: UserId): IO[CartTotal] = ???
  override def delete(userId: UserId): IO[Unit] = ???
  override def removeItem(userId: UserId, itemId: ItemId): IO[Unit] = ???
  override def update(userId: UserId, cart: Cart): IO[Unit] = ???
}

protected class TestOrders() extends Orders[IO] {
  override def get(userId: UserId, orderId: OrderId): IO[Option[Order]] = ???
  override def findBy(userId: UserId): IO[List[Order]] = ???
  override def create(
      userId: UserId,
      paymentId: PaymentId,
      items: NonEmptyList[CartItem],
      total: Money
  ): IO[OrderId] = ???
}
