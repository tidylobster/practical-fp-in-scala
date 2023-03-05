package com.guitarshop.storage

import com.guitarshop.auth.{Crypto, JwtExpire, Tokens}
import com.guitarshop.config.types._
import com.guitarshop.domain.brand._
import com.guitarshop.domain.category._
import com.guitarshop.domain.item._
import com.guitarshop.domain.cart._
import com.guitarshop.domain.ID
import com.guitarshop.domain.auth._
import com.guitarshop.generators._
import com.guitarshop.http.auth.users._
import com.guitarshop.services._
import com.guitarshop.suite.ResourceSuite

import cats.effect._
import cats.effect.kernel.Ref
import cats.implicits._
import dev.profunktor.auth.jwt._
import dev.profunktor.redis4cats._
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._
import org.typelevel.log4cats.noop.NoOpLogger
import pdi.jwt._

import java.util.UUID
import scala.concurrent.duration._

object RedisSuite extends ResourceSuite {
  implicit val logger = NoOpLogger[IO]

  type Res = RedisCommands[IO, String, String]

  override def sharedResource: Resource[IO, Res] =
    Redis[IO]
      .utf8("redis://localhost")
      .beforeAll(_.flushAll)

  val Exp = ShoppingCartExpiration(30.seconds)
  val tokenConfig = JwtAccessTokenKeyConfig("bar")
  val tokenExp = TokenExpiration(30.seconds)
  val jwtClaim = JwtClaim("test")
  val userJwtAuth = UserJwtAuth(JwtAuth.hmac("bar", JwtAlgorithm.HS256))

  test("ShoppingCart") { redis =>
    val gen = for {
      uid <- userIdGen
      it1 <- itemGen
      it2 <- itemGen
      qt1 <- quantityGen
      qt2 <- quantityGen
    } yield (uid, it1, it2, qt1, qt2)

    forall(gen) { case (uid, it1, it2, qt1, qt2) =>
      Ref.of[IO, Map[ItemId, Item]](Map(it1.id -> it1, it2.id -> it2)).flatMap { ref =>
        val items = new TestItems(ref)
        val c = ShoppingCart.make[IO](items, redis, Exp)
        for {
          x <- c.get(uid)
          _ <- c.add(uid, it1.id, qt1)
          _ <- c.add(uid, it2.id, qt1)
          y <- c.get(uid)
          _ <- c.removeItem(uid, it1.id)
          z <- c.get(uid)
          _ <- c.update(uid, Cart(Map(it2.id -> qt2)))
          w <- c.get(uid)
          _ <- c.delete(uid)
          v <- c.get(uid)
        } yield expect.all(
          x.items.isEmpty,
          y.items.size === 2,
          z.items.size === 1,
          v.items.isEmpty,
          w.items.headOption.fold(false)(_.quantity === qt2)
        )
      }
    }
  }

  test("Authentication") { redis =>
    val gen = for {
      un1 <- userNameGen
      un2 <- userNameGen
      pw <- passwordGen
    } yield (un1, un2, pw)

    forall(gen) { case (un1, un2, pw) =>
      for {
        t <- JwtExpire.make[IO].map {
          Tokens.make[IO](_, tokenConfig, tokenExp)
        }
        c <- Crypto.make[IO](PasswordSalt("test"))
        a = Auth.make(tokenExp, t, new TestUsers(un2), redis, c)
        u = UsersAuth.common[IO](redis)
        x <- u.findUser(JwtToken("invalid"))(jwtClaim)
        y <- a.login(un1, pw).attempt
        j <- a.newUser(un1, pw)
        e <- jwtDecode[IO](j, userJwtAuth.value).attempt
        k <- a.login(un2, pw).attempt
        w <- u.findUser(j)(jwtClaim)
        s <- redis.get(j.value)
        _ <- a.logout(j, un1)
        z <- redis.get(j.value)
      } yield expect.all(
        x.isEmpty,
        y == Left(UserNotFound(un1)),
        e.isRight,
        k == Left(InvalidPassword(un2)),
        w.fold(false)(_.value.name === un1),
        s.nonEmpty,
        z.isEmpty
      )
    }
  }
}

protected class TestItems(ref: Ref[IO, Map[ItemId, Item]]) extends Items[IO] {
  override def findAll: IO[List[Item]] = ref.get.map(_.values.toList)

  override def findBy(brand: BrandName): IO[List[Item]] = ref.get.map {
    _.values.filter(_.brand.name === brand).toList
  }

  override def findById(itemId: ItemId): IO[Option[Item]] = ref.get.map(_.get(itemId))

  override def create(item: CreateItem): IO[ItemId] =
    ID.make[IO, ItemId].flatMap { id =>
      val brand = Brand(item.brandId, BrandName("foo"))
      val category = Category(item.categoryId, CategoryName("bar"))
      val newItem = Item(id, item.name, item.description, item.price, brand, category)
      ref.update(_.updated(id, newItem)).as(id)
    }

  override def update(item: UpdateItem): IO[Unit] =
    ref.update { x =>
      x.get(item.id).fold(x) { i =>
        x.updated(item.id, i.copy(price = item.price))
      }
    }
}

protected class TestUsers(un: UserName) extends Users[IO] {
  override def find(username: UserName): IO[Option[UserWithPassword]] = IO.pure {
    (username === un)
      .guard[Option]
      .as {
        UserWithPassword(
          UserId(UUID.randomUUID()),
          un,
          EncryptedPassword("foo")
        )
      }
  }

  override def create(username: UserName, password: EncryptedPassword): IO[UserId] =
    ID.make[IO, UserId]
}
