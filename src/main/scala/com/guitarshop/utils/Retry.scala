package com.guitarshop.utils

import cats.effect.Temporal
import cats.syntax.show._
import derevo.cats.show
import derevo.derive
import org.typelevel.log4cats.Logger
import retry._
import retry.RetryDetails._

trait Retry[F[_]] {
  def retry[A](policy: RetryPolicy[F], retriable: Retriable)(fa: F[A]): F[A]
}

object Retry {
  def apply[F[_]: Retry]: Retry[F] = implicitly

  implicit def forLoggerTemporal[F[_]: Logger: Temporal]: Retry[F] =
    new Retry[F] {
      def retry[A](policy: RetryPolicy[F], retriable: Retriable)(fa: F[A]): F[A] = {
        def onError(e: Throwable, details: RetryDetails): F[Unit] =
          details match {
            case WillDelayAndRetry(_, retriesSoFar, _) =>
              Logger[F].error(
                s"Failed on ${retriable.show}. We retried $retriesSoFar times."
              )
            case GivingUp(totalRetries, _) =>
              Logger[F].error(s"Giving up on ${retriable.show} after $totalRetries retries.")
          }

        retryingOnAllErrors[A](policy, onError)(fa)
      }
    }
}

@derive(show)
sealed trait Retriable

object Retriable {
  case object Orders extends Retriable
  case object Payments extends Retriable
}
