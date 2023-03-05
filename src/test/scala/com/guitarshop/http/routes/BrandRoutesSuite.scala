package com.guitarshop.http.routes

import cats.effect.IO
import com.guitarshop.domain.brand
import com.guitarshop.domain.brand.Brand
import com.guitarshop.generators.brandGen
import com.guitarshop.services.Brands
import com.guitarshop.suite.HttpSuite
import org.http4s._
import org.http4s.client.dsl.io._
import org.http4s.Method._
import org.http4s.implicits._
import org.scalacheck.Gen

object BrandRoutesSuite extends HttpSuite {

  def dataBrands(brands: List[Brand]): Brands[IO] = new TestBrands {
    override def findAll: IO[List[Brand]] = IO.pure(brands)
  }

  def failingBrands(brands: List[Brand]): Brands[IO] = new TestBrands {
    override def findAll: IO[List[Brand]] =
      IO.raiseError(DummyError) *> IO.pure(brands)
  }

  test("GET brands succeeds") {
    forall(Gen.listOf(brandGen)) { b =>
      val req = GET(uri"/brands")
      val routes = new BrandRoutes[IO](dataBrands(b)).routes
      expectHttpBodyAndStatus(routes, req)(b, Status.Ok)
    }
  }

  test("GET brands fails") {
    forall(Gen.listOf(brandGen)) { b =>
      val req = GET(uri"/brands")
      val routes = new BrandRoutes[IO](failingBrands(b)).routes
      expectHttpFailure(routes, req)
    }
  }
}

protected class TestBrands() extends Brands[IO] {
  override def findAll: IO[List[brand.Brand]] = ???
  override def create(name: brand.BrandName): IO[brand.BrandId] = ???
}
