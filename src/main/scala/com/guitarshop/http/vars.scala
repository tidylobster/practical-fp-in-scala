package com.guitarshop.http

import com.guitarshop.domain.item.ItemId
import com.guitarshop.domain.order.OrderId

import cats.implicits._
import java.util.UUID

object vars {
  protected class UUIDVar[A](f: UUID => A) {
    def unapply(str: String): Option[A] =
      Either.catchNonFatal(f(UUID.fromString(str))).toOption
  }

  object ItemIdVar extends UUIDVar(ItemId.apply)
  object OrderIdVar extends UUIDVar(OrderId.apply)
}
