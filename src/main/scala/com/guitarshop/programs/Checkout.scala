package com.guitarshop.programs

import com.guitarshop.domain.cart._
import com.guitarshop.domain.order._
import com.guitarshop.domain.payment._
import com.guitarshop.domain.auth._
import com.guitarshop.domain.checkout._
import com.guitarshop.services.ShoppingCart
import com.guitarshop.services.Orders
import com.guitarshop.http.clients._
import com.guitarshop.utils.{Background, Retriable, Retry}

import cats.data.NonEmptyList
import cats.MonadThrow
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import retry._
import squants.market.Money

import scala.concurrent.duration.DurationInt

final case class Checkout[F[_]: MonadThrow: Background: Retry: Logger](
    payments: PaymentClient[F],
    cart: ShoppingCart[F],
    orders: Orders[F],
    policy: RetryPolicy[F]
) {

  private def ensureNonEmpty[A](xs: List[A]): F[NonEmptyList[A]] =
    MonadThrow[F].fromOption(
      NonEmptyList.fromList(xs),
      EmptyCartError
    )

  private def processPayment(in: Payment): F[PaymentId] =
    Retry[F]
      .retry(policy, Retriable.Payments)(payments.process(in))
      .adaptError { case e =>
        PaymentError(
          Option(e.getMessage).getOrElse("Unknown")
        )
      }

  private def createOrder(
      userId: UserId,
      paymentId: PaymentId,
      items: NonEmptyList[CartItem],
      total: Money
  ): F[OrderId] = {
    val action =
      Retry[F]
        .retry(policy, Retriable.Orders)(
          orders.create(userId, paymentId, items, total)
        )
        .adaptError { case e =>
          OrderError(e.getMessage)
        }

    def bgAction(fa: F[OrderId]): F[OrderId] =
      fa.onError { case _ =>
        Logger[F].error(s"Failed to create order for: ${paymentId.show}") *>
          Background[F].schedule(bgAction(fa), 1.hour)
      }

    bgAction(action)
  }

  def process(userId: UserId, card: Card): F[OrderId] =
    cart.get(userId).flatMap { case CartTotal(items, total) =>
      for {
        its <- ensureNonEmpty(items)
        pid <- processPayment(Payment(userId, total, card))
        oid <- createOrder(userId, pid, its, total)
        _ <- cart.delete(userId).attempt.void
      } yield oid
    }
}
