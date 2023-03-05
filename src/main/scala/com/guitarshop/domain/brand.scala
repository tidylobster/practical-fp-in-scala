package com.guitarshop.domain

import java.util.UUID

import scala.util.control.NoStackTrace

import com.guitarshop.ext.http4s.queryParam
import com.guitarshop.ext.http4s.refined._
import com.guitarshop.optics.uuid

import derevo.cats._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.refined._
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype

object brand {

  @derive(decoder, encoder, show, eqv, uuid)
  @newtype
  case class BrandId(value: UUID)

  @derive(decoder, encoder, show, eqv)
  @newtype
  case class BrandName(value: String)

  @derive(encoder, decoder, show, eqv)
  case class Brand(id: BrandId, name: BrandName)

  @derive(encoder, decoder, show, eqv, queryParam)
  @newtype
  case class BrandParam(value: NonEmptyString) {
    def toDomain: BrandName = BrandName(value.toLowerCase.capitalize)
  }

}
