package com.guitarshop.services

import cats.data.NonEmptyList
import com.guitarshop.domain.ID
import com.guitarshop.domain.auth._
import com.guitarshop.domain.order._
import com.guitarshop.domain.cart._
import com.guitarshop.domain.item._
import com.guitarshop.effects.GenUUID
import com.guitarshop.sql.codecs._
import cats.effect._
import cats.syntax.all._
import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.circe.codec.all._
import skunk.implicits._
import squants.market._

trait Orders[F[_]] {
  def get(userId: UserId, orderId: OrderId): F[Option[Order]]
  def findBy(userId: UserId): F[List[Order]]
  def create(
      userId: UserId,
      paymentId: PaymentId,
      items: NonEmptyList[CartItem],
      total: Money
  ): F[OrderId]
}

object Orders {
  def make[F[_]: Concurrent: GenUUID](postgres: Resource[F, Session[F]]): Orders[F] =
    new Orders[F] {
      import OrdersSQL._

      override def get(userId: UserId, orderId: OrderId): F[Option[Order]] =
        postgres.use { session =>
          session.prepare(selectByUserIdAndOrderId).use { ps =>
            ps.option(userId ~ orderId)
          }
        }

      override def findBy(userId: UserId): F[List[Order]] =
        postgres.use { session =>
          session.prepare(selectByUserId).use { ps =>
            ps.stream(userId, 1024).compile.toList
          }
        }

      override def create(
          userId: UserId,
          paymentId: PaymentId,
          items: NonEmptyList[CartItem],
          total: Money
      ): F[OrderId] =
        postgres.use { session =>
          session.prepare(insertOrder).use { cmd =>
            ID.make[F, OrderId].flatMap { id =>
              val itMap = items.toList.map(x => x.item.id -> x.quantity).toMap
              val order = Order(id, paymentId, itMap, total)
              cmd.execute(userId ~ order).as(id)
            }
          }
        }
    }
}

private object OrdersSQL {
  val decoder: Decoder[Order] =
    (orderId ~ userId ~ paymentId ~ jsonb[Map[ItemId, Quantity]] ~ money).map {
      case o ~ _ ~ p ~ i ~ t => Order(o, p, i, t)
    }

  val selectByUserId: Query[UserId, Order] =
    sql"""
         SELECT * FROM orders
         WHERE user_id = $userId
       """.query(decoder)

  val selectByUserIdAndOrderId: Query[UserId ~ OrderId, Order] =
    sql"""
         SELECT * FORM orders
         WHERE user_id = $userId
           AND uuid = $orderId
       """.query(decoder)

  val encoder: Encoder[UserId ~ Order] =
    (orderId ~ userId ~ paymentId ~ jsonb[Map[ItemId, Quantity]] ~ money).contramap { case id ~ o =>
      o.id ~ id ~ o.pid ~ o.items ~ o.total
    }

  val insertOrder: Command[UserId ~ Order] =
    sql"""
         INSERT INTO orders
         VALUES ($encoder)
       """.command
}
