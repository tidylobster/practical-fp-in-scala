package com.guitarshop.domain

import com.guitarshop.domain.cart._
import com.guitarshop.domain.item._
import derevo.cats.{eqv, show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.estatico.newtype.macros.newtype
import com.guitarshop.optics.uuid
import squants.market.Money

import java.util.UUID
import scala.util.control.NoStackTrace

object order {

  @derive(encoder, decoder, show, eqv, uuid)
  @newtype case class OrderId(value: UUID)

  @derive(encoder, decoder, show, eqv, uuid)
  @newtype case class PaymentId(value: UUID)

  @derive(encoder, decoder)
  case class Order(id: OrderId, pid: PaymentId, items: Map[ItemId, Quantity], total: Money)

  @derive(show)
  sealed trait OrderOrPaymentError extends NoStackTrace {
    def cause: String
  }

  @derive(eqv, show)
  case class OrderError(cause: String) extends OrderOrPaymentError
  case class PaymentError(cause: String) extends OrderOrPaymentError

}
