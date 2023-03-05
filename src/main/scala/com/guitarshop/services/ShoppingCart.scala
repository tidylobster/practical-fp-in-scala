package com.guitarshop.services

import com.guitarshop.domain.auth._
import com.guitarshop.domain.cart._
import com.guitarshop.domain.item._
import com.guitarshop.domain._
import com.guitarshop.effects.GenUUID
import cats.implicits._
import cats.MonadThrow
import com.guitarshop.config.types._
import com.guitarshop.domain.ID
import dev.profunktor.redis4cats.RedisCommands

trait ShoppingCart[F[_]] {
  def add(userId: UserId, itemId: ItemId, quantity: Quantity): F[Unit]
  def get(userId: UserId): F[CartTotal]
  def delete(userId: UserId): F[Unit]
  def removeItem(userId: UserId, itemId: ItemId): F[Unit]
  def update(userId: UserId, cart: Cart): F[Unit]
}

object ShoppingCart {
  def make[F[_]: GenUUID: MonadThrow](
      items: Items[F],
      redis: RedisCommands[F, String, String],
      exp: ShoppingCartExpiration
  ): ShoppingCart[F] =
    new ShoppingCart[F] {
      override def add(userId: UserId, itemId: ItemId, quantity: Quantity): F[Unit] = {
        redis.hSet(userId.show, itemId.show, quantity.show) *>
          redis.expire(userId.show, exp.value).void
      }

      override def get(userId: UserId): F[CartTotal] =
        redis.hGetAll(userId.show).flatMap {
          _.toList
            .traverseFilter { case (k, v) =>
              for {
                id <- ID.read[F, ItemId](k)
                qt <- MonadThrow[F].catchNonFatal(Quantity(v.toInt))
                rs <- items.findById(id).map(_.map(_.cart(qt)))
              } yield rs
            }
            .map { items =>
              CartTotal(items, items.foldMap(_.subTotal))
            }
        }

      override def delete(userId: UserId): F[Unit] =
        redis.hDel(userId.show).void

      override def removeItem(userId: UserId, itemId: ItemId): F[Unit] =
        redis.hDel(userId.show, itemId.show).void

      override def update(userId: UserId, cart: Cart): F[Unit] =
        redis.hGetAll(userId.show).flatMap {
          _.toList.traverse_ { case (k, _) =>
            ID.read[F, ItemId](k).flatMap { id =>
              cart.items.get(id).traverse_ { q =>
                redis.hSet(userId.show, k, q.show)
              }
            }
          }
        } *> redis.expire(userId.show, exp.value).void
    }
}
