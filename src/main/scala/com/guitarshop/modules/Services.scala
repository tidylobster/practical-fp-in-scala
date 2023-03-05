package com.guitarshop.modules

import cats.effect.kernel.{Resource, Temporal}
import com.guitarshop.config.types.ShoppingCartExpiration
import com.guitarshop.effects.GenUUID
import com.guitarshop.services._
import dev.profunktor.redis4cats.RedisCommands
import skunk.Session

object Services {
  def make[F[_]: GenUUID: Temporal](
      redis: RedisCommands[F, String, String],
      postgres: Resource[F, Session[F]],
      cartExpiration: ShoppingCartExpiration
  ): Services[F] = {
    val _items = Items.make[F](postgres)
    new Services(
      cart = ShoppingCart.make[F](_items, redis, cartExpiration),
      brands = Brands.make[F](postgres),
      categories = Categories.make[F](postgres),
      items = _items,
      orders = Orders.make[F](postgres),
      healthCheck = HealthCheck.make[F](postgres, redis)
    ) {}
  }
}

sealed abstract class Services[F[_]] private (
    val cart: ShoppingCart[F],
    val brands: Brands[F],
    val categories: Categories[F],
    val items: Items[F],
    val orders: Orders[F],
    val healthCheck: HealthCheck[F]
)
