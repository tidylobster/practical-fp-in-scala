package com.guitarshop

import com.guitarshop.domain.auth._
import com.guitarshop.domain.brand._
import com.guitarshop.domain.cart._
import com.guitarshop.domain.category._
import com.guitarshop.domain.checkout._
import com.guitarshop.domain.item._
import com.guitarshop.domain.order._
import com.guitarshop.domain.payment._
import com.guitarshop.http.auth.users._

import eu.timepit.refined.api.Refined
import org.scalacheck.Gen
import squants.Money
import squants.market.USD

import java.util.UUID

object generators {

  val nonEmptyStringGen: Gen[String] = Gen
    .chooseNum(21, 40)
    .flatMap { n =>
      Gen.buildableOfN[String, Char](n, Gen.alphaChar)
    }

  def nesGen[A](f: String => A): Gen[A] =
    nonEmptyStringGen.map(f)

  def idGen[A](f: UUID => A): Gen[A] =
    Gen.uuid.map(f)

  val brandIdGen: Gen[BrandId] =
    idGen(BrandId.apply)

  val brandNameGen: Gen[BrandName] =
    nesGen(BrandName.apply)

  val brandGen: Gen[Brand] =
    for {
      i <- brandIdGen
      n <- brandNameGen
    } yield Brand(i, n)

  val categoryIdGen: Gen[CategoryId] =
    idGen(CategoryId.apply)

  val categoryNameGen: Gen[CategoryName] =
    nesGen(CategoryName.apply)

  val categoryGen: Gen[Category] =
    for {
      i <- categoryIdGen
      n <- categoryNameGen
    } yield Category(i, n)

  val moneyGen: Gen[Money] =
    Gen.posNum[Long].map { n => USD(BigDecimal(n)) }

  val itemIdGen: Gen[ItemId] =
    idGen(ItemId.apply)

  val itemNameGen: Gen[ItemName] =
    nesGen(ItemName.apply)

  val itemDescGen: Gen[ItemDescription] =
    nesGen(ItemDescription.apply)

  val itemGen: Gen[Item] =
    for {
      i <- itemIdGen
      n <- itemNameGen
      d <- itemDescGen
      p <- moneyGen
      b <- brandGen
      c <- categoryGen
    } yield Item(i, n, d, p, b, c)

  val quantityGen: Gen[Quantity] =
    Gen.posNum[Int].map(Quantity.apply)

  val cartItemGen: Gen[CartItem] =
    for {
      i <- itemGen
      q <- quantityGen
    } yield CartItem(i, q)

  val cartTotalGen: Gen[CartTotal] =
    for {
      i <- Gen.nonEmptyListOf(cartItemGen)
      t <- moneyGen
    } yield CartTotal(i, t)

  val itemMapGen: Gen[(ItemId, Quantity)] =
    for {
      i <- itemIdGen
      q <- quantityGen
    } yield i -> q

  val cartGen: Gen[Cart] =
    Gen.nonEmptyMap(itemMapGen).map(Cart.apply)

  val cardNameGen: Gen[CardName] =
    Gen
      .stringOf(
        Gen.oneOf(('a' to 'z') ++ ('A' to 'Z'))
      )
      .map { x =>
        CardName(Refined.unsafeApply(x))
      }

  private def sized(size: Int): Gen[Long] = {
    def go(s: Int, acc: String): Gen[Long] =
      Gen.oneOf(1 to 9).flatMap { n =>
        if (s == size) acc.toLong
        else go(s + 1, acc + n.toString)
      }

    go(0, "")
  }

  val cardGen: Gen[Card] =
    for {
      n <- cardNameGen
      u <- sized(16).map(x => CardNumber(Refined.unsafeApply(x)))
      x <- sized(4).map(x => CardExpiry(Refined.unsafeApply(x.toString)))
      c <- sized(3).map(x => CardCvv(Refined.unsafeApply(x.toInt)))
    } yield Card(n, u, x, c)

  val userIdGen: Gen[UserId] =
    idGen(UserId.apply)

  val userNameGen: Gen[UserName] =
    nesGen(UserName.apply)

  val userGen: Gen[User] =
    for {
      i <- userIdGen
      n <- userNameGen
    } yield User(i, n)

  val commonUserGen: Gen[CommonUser] =
    userGen.map(CommonUser.apply)

  val paymentIdGen: Gen[PaymentId] =
    idGen(PaymentId.apply)

  val paymentGen: Gen[Payment] =
    for {
      i <- userIdGen
      t <- moneyGen
      c <- cardGen
    } yield Payment(i, t, c)

  val orderIdGen: Gen[OrderId] =
    idGen(OrderId.apply)

  val passwordGen: Gen[Password] =
    nesGen(Password.apply)

  val encryptedPasswordGen: Gen[EncryptedPassword] =
    nesGen(EncryptedPassword.apply)
}
