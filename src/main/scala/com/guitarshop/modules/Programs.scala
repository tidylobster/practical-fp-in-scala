package com.guitarshop.modules

import cats.effect.kernel.Temporal
import cats.implicits._
import com.guitarshop.config.types.CheckoutConfig
import com.guitarshop.programs.Checkout
import com.guitarshop.utils.Background
import org.typelevel.log4cats.Logger
import retry.RetryPolicies._
import retry.RetryPolicy

object Programs {
  def make[F[_]: Background: Logger: Temporal](
      checkoutConfig: CheckoutConfig,
      services: Services[F],
      clients: HttpClients[F]
  ): Programs[F] =
    new Programs[F](checkoutConfig, services, clients) {}
}

sealed abstract class Programs[F[_]: Background: Logger: Temporal] private (
    config: CheckoutConfig,
    services: Services[F],
    clients: HttpClients[F]
) {

  val retryPolicy: RetryPolicy[F] =
    limitRetries[F](config.retriesLimit.value) |+| exponentialBackoff[F](config.retriesBackoff)

  val checkout: Checkout[F] = Checkout[F](
    clients.payment,
    services.cart,
    services.orders,
    retryPolicy
  )
}
