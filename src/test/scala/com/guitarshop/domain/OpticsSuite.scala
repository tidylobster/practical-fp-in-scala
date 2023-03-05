package com.guitarshop.domain

import com.guitarshop.domain.brand._
import com.guitarshop.domain.healthcheck.Status
import com.guitarshop.generators.brandIdGen
import com.guitarshop.optics.IsUUID
import monocle.law.discipline.IsoTests
import org.scalacheck.{Arbitrary, Cogen, Gen}
import weaver.discipline.Discipline
import weaver.FunSuite

import java.util.UUID

object OpticsSuite extends FunSuite with Discipline {

  implicit val arbStatus: Arbitrary[Status] =
    Arbitrary(Gen.oneOf(Status.Okay, Status.Unreachable))

  implicit val arbBrandId: Arbitrary[BrandId] =
    Arbitrary(brandIdGen)

  implicit val brandIdCogen: Cogen[BrandId] =
    Cogen[UUID].contramap[BrandId](_.value)

  checkAll("Iso[Status._Bool]", IsoTests(Status._Bool))

  checkAll("IsUUID[UUID]", IsoTests(IsUUID[UUID]._UUID))
  checkAll("IsUUID[BrandId]", IsoTests(IsUUID[BrandId]._UUID))
}
