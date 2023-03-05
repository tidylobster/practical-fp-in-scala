package com.guitarshop.http.routes

import cats.effect.IO
import cats.syntax.all._
import com.guitarshop.domain.brand._
import com.guitarshop.domain.item._
import com.guitarshop.domain.ID
import com.guitarshop.generators._
import com.guitarshop.services.Items
import com.guitarshop.suite.HttpSuite
import org.http4s._
import org.http4s.client.dsl.io._
import org.http4s.Method._
import org.http4s.implicits._
import org.scalacheck.Gen

object ItemRoutesSuite extends HttpSuite {

  def dataItems(items: List[Item]): Items[IO] = new TestItems {
    override def findAll: IO[List[Item]] = IO.pure(items)
    override def findBy(brand: BrandName): IO[List[Item]] =
      IO.pure(items.find(_.brand.name === brand).toList)
  }

  def failingItems(items: List[Item]): Items[IO] = new TestItems {
    override def findAll: IO[List[Item]] = IO.raiseError(DummyError) *> IO.pure(items)
    override def findBy(brand: BrandName): IO[List[Item]] = findAll
  }

  test("GET items succeeds") {
    forall(Gen.listOf(itemGen)) { items =>
      val req = GET(uri"/items")
      val routes = new ItemRoutes[IO](dataItems(items)).routes
      expectHttpBodyAndStatus(routes, req)(items, Status.Ok)
    }
  }

  test("GET items by brand succeeds") {
    val gen = for {
      i <- Gen.listOf(itemGen)
      b <- brandGen
    } yield i -> b

    forall(gen) { case (i, b) =>
      val req = GET(uri"/items".withQueryParam("brand", b.name.value))
      val routes = new ItemRoutes[IO](dataItems(i)).routes
      val expected = i.find(_.brand.name === b.name).toList
      expectHttpBodyAndStatus(routes, req)(expected, Status.Ok)
    }
  }

  test("GET items fails") {
    forall(Gen.listOf(itemGen)) { items =>
      val req = GET(uri"/items")
      val routes = new ItemRoutes[IO](failingItems(items)).routes
      expectHttpFailure(routes, req)
    }
  }
}

protected class TestItems() extends Items[IO] {
  override def findAll: IO[List[Item]] = IO.pure(List.empty)
  override def findBy(brand: BrandName): IO[List[Item]] = IO.pure(List.empty)
  override def findById(itemId: ItemId): IO[Option[Item]] = IO.pure(none[Item])
  override def create(item: CreateItem): IO[ItemId] = ID.make[IO, ItemId]
  override def update(item: UpdateItem): IO[Unit] = IO.unit
}
