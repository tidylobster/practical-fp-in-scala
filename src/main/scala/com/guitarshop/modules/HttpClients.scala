package com.guitarshop.modules

import cats.effect.kernel.Temporal
import com.guitarshop.config.types.PaymentConfig
import com.guitarshop.http.clients.PaymentClient
import com.guitarshop.utils.Background
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

object HttpClients {
  def make[F[_]: Background: Logger: Temporal](
      paymentConfig: PaymentConfig,
      client: Client[F]
  ): HttpClients[F] =
    new HttpClients[F] {
      def payment: PaymentClient[F] =
        PaymentClient.make[F](paymentConfig, client)
    }
}

sealed trait HttpClients[F[_]] {
  def payment: PaymentClient[F]
}
