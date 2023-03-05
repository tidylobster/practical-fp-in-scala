package com.guitarshop.domain

import com.guitarshop.domain.auth._
import com.guitarshop.domain.checkout._
import derevo.cats.show
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import squants.market.Money

object payment {

  @derive(encoder, decoder, show)
  case class Payment(id: UserId, total: Money, card: Card)

}
