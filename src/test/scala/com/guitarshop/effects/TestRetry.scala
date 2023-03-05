package com.guitarshop.effects

import cats.effect.{IO, Ref}
import com.guitarshop.utils.{Retriable, Retry}
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry.{retryingOnAllErrors, RetryDetails, RetryPolicy}

import scala.annotation.nowarn

object TestRetry {

  def handleFor[A <: RetryDetails](
      ref: Ref[IO, Option[A]]
  ): Retry[IO] = new Retry[IO] {
    override def retry[T](policy: RetryPolicy[IO], retriable: Retriable)(fa: IO[T]): IO[T] = {
      @nowarn
      def onError(e: Throwable, details: RetryDetails): IO[Unit] =
        details match {
          case g: A => ref.set(Some(g))
          case _    => IO.unit
        }

      retryingOnAllErrors[T](policy, onError)(fa)
    }
  }

  def givingUp(ref: Ref[IO, Option[GivingUp]]): Retry[IO] =
    handleFor[GivingUp](ref)

  def recovering(ref: Ref[IO, Option[WillDelayAndRetry]]): Retry[IO] =
    handleFor[WillDelayAndRetry](ref)

}
