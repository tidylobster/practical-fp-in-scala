package com.guitarshop.domain

import java.util.UUID
import javax.crypto.Cipher
import scala.util.control.NoStackTrace

import com.guitarshop.optics.uuid

import derevo.cats._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.refined._
import io.estatico.newtype.macros.newtype

object auth {

  @derive(encoder, decoder, show, eqv, uuid)
  @newtype case class UserId(value: UUID)

  @derive(decoder, encoder, show, eqv)
  @newtype case class UserName(value: String)

  @derive(decoder, encoder, eqv, show)
  @newtype
  case class Password(value: String)

  @derive(decoder, encoder, eqv, show)
  @newtype
  case class EncryptedPassword(value: String)

  @newtype case class EncryptCipher(value: Cipher)
  @newtype case class DecryptCipher(value: Cipher)

  // User Registration

  @derive(decoder, encoder)
  @newtype
  case class UserNameParam(value: NonEmptyString) {
    def toDomain: UserName = UserName(value.toLowerCase)
  }

  @derive(decoder, encoder)
  @newtype
  case class PasswordParam(value: NonEmptyString) {
    def toDomain: Password = Password(value)
  }

  @derive(decoder, encoder)
  case class CreateUser(
      username: UserNameParam,
      password: PasswordParam
  )

  case class UserNotFound(username: UserName) extends NoStackTrace
  case class InvalidPassword(username: UserName) extends NoStackTrace
  case class UserNameInUse(username: UserName) extends NoStackTrace

  // User Login

  @derive(decoder, encoder)
  case class LoginUser(
      username: UserNameParam,
      password: PasswordParam
  )

  // Admin Auth

  @newtype
  case class ClaimContent(value: UUID)

  object ClaimContent {
    implicit val jsonDecoder: Decoder[ClaimContent] =
      Decoder.forProduct1("value")(ClaimContent.apply)
  }

}
