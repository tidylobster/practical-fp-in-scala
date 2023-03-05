package com.guitarshop.domain

import com.guitarshop.ext.refined._

import derevo.cats._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.api.Refined
import eu.timepit.refined.cats._
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.Size
import eu.timepit.refined.string.{MatchesRegex, ValidInt}
import io.circe.Decoder
import io.circe.refined._
import io.estatico.newtype.macros.newtype

object checkout {

  type Rgx = "^[a-zA-Z]+(([',. -][a-zA-Z ])?[a-zA-Z]*)*$"

  type CardNamePred = String Refined MatchesRegex[Rgx]
  type CardNumberPred = Long Refined Size[16]
  type CardExpirationPred = String Refined (Size[4] And ValidInt)
  type CardCVVPred = Int Refined Size[3]

  @derive(decoder, encoder, show)
  @newtype case class CardName(value: CardNamePred)

  @derive(decoder, encoder, show)
  @newtype case class CardNumber(value: CardNumberPred)

  @derive(decoder, encoder, show)
  @newtype case class CardExpiry(value: CardExpirationPred)

  @derive(decoder, encoder, show)
  @newtype case class CardCvv(value: CardCVVPred)

  @derive(encoder, decoder, show)
  case class Card(name: CardName, number: CardNumber, expiryDate: CardExpiry, cvv: CardCvv)

}
