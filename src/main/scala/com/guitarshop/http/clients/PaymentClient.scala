package com.guitarshop.http.clients

import cats.effect.MonadCancelThrow
import com.guitarshop.domain.payment._
import com.guitarshop.domain.order._
import cats.implicits._
import com.guitarshop.config.types.PaymentConfig
import org.http4s.circe.{toMessageSyntax, JsonDecoder}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method.POST
import org.http4s.{Status, Uri}
import org.http4s.circe.CirceEntityCodec._

trait PaymentClient[F[_]] {
  def process(payment: Payment): F[PaymentId]
}

object PaymentClient {
  def make[F[_]: JsonDecoder: MonadCancelThrow](
      config: PaymentConfig,
      client: Client[F]
  ): PaymentClient[F] =
    new PaymentClient[F] with Http4sClientDsl[F] {
      override def process(payment: Payment): F[PaymentId] =
        Uri
          .fromString(config.uri + "/payments")
          .liftTo[F]
          .flatMap { uri =>
            client.run(POST.apply(payment, uri)).use { resp =>
              resp.status match {
                case Status.Ok | Status.Conflict =>
                  resp.asJsonDecode[PaymentId]
                case st =>
                  PaymentError(
                    Option(st.reason).getOrElse("unknown")
                  ).raiseError[F, PaymentId]
              }
            }
          }
    }
}
