package com.guitarshop.domain

import com.guitarshop.domain.auth.UserId
import com.guitarshop.domain.item.{Item, ItemId}

import derevo.cats._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype
import squants.market.{Money, USD}

import scala.util.control.NoStackTrace

object cart {

  @derive(encoder, decoder, show, eqv)
  @newtype case class Quantity(value: Int)

  @derive(eqv, show)
  @newtype case class Cart(items: Map[ItemId, Quantity])
  object Cart {
    implicit val jsonEncoder: Encoder[Cart] =
      Encoder.forProduct1("items")(_.items)

    implicit val jsonDecoder: Decoder[Cart] =
      Decoder.forProduct1("items")(Cart.apply)
  }

  @derive(encoder, decoder, show)
  case class CartItem(item: Item, quantity: Quantity) {
    def subTotal: Money = USD(item.price.amount * quantity.value)
  }

  @derive(encoder, decoder, show)
  case class CartTotal(items: List[CartItem], total: Money)

  @derive(show)
  case object EmptyCartError extends NoStackTrace

  @derive(decoder, encoder)
  case class CartNotFound(userId: UserId) extends NoStackTrace

}
