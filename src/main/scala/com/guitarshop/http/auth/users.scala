package com.guitarshop.http.auth

import derevo.cats.show
import derevo.derive
import io.estatico.newtype.macros.newtype
import com.guitarshop.domain.auth._
import derevo.circe.magnolia.{decoder, encoder}
import dev.profunktor.auth.jwt._

object users {

  @newtype case class AdminJwtAuth(value: JwtSymmetricAuth)
  @newtype case class UserJwtAuth(value: JwtSymmetricAuth)

  @derive(decoder, encoder, show)
  case class User(id: UserId, name: UserName)

  @derive(decoder, encoder)
  case class UserWithPassword(
      id: UserId,
      name: UserName,
      password: EncryptedPassword
  )

  @derive(decoder, encoder, show)
  @newtype
  case class CommonUser(value: User)

  @derive(decoder, encoder, show)
  @newtype
  case class AdminUser(value: User)

}
