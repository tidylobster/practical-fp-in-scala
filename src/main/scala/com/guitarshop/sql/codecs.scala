package com.guitarshop.sql

import com.guitarshop.domain.auth.{EncryptedPassword, UserId, UserName}
import com.guitarshop.domain.brand._
import com.guitarshop.domain.category._
import com.guitarshop.domain.item._
import com.guitarshop.domain.order._
import skunk._
import skunk.codec.all._
import squants.market.{Money, USD}

object codecs {
  val brandId: Codec[BrandId] =
    uuid.imap[BrandId](BrandId.apply)(_.value)
  val brandName: Codec[BrandName] =
    varchar.imap[BrandName](BrandName.apply)(_.value)

  val categoryId: Codec[CategoryId] =
    uuid.imap[CategoryId](CategoryId.apply)(_.value)
  val categoryName: Codec[CategoryName] =
    varchar.imap[CategoryName](CategoryName.apply)(_.value)

  val itemId: Codec[ItemId] =
    uuid.imap[ItemId](ItemId.apply)(_.value)
  val itemName: Codec[ItemName] =
    varchar.imap[ItemName](ItemName.apply)(_.value)
  val itemDesc: Codec[ItemDescription] =
    varchar.imap[ItemDescription](ItemDescription.apply)(_.value)

  val orderId: Codec[OrderId] =
    uuid.imap[OrderId](OrderId.apply)(_.value)

  val userId: Codec[UserId] =
    uuid.imap[UserId](UserId.apply)(_.value)
  val userName: Codec[UserName] =
    varchar.imap[UserName](UserName.apply)(_.value)

  val paymentId: Codec[PaymentId] =
    uuid.imap[PaymentId](PaymentId.apply)(_.value)

  val encPassword: Codec[EncryptedPassword] =
    varchar.imap[EncryptedPassword](EncryptedPassword.apply)(_.value)

  val money: Codec[Money] =
    numeric.imap[Money](USD(_))(_.amount)
}
