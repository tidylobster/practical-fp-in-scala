package com.guitarshop.suite

import cats.effect.{IO, Resource}
import cats.implicits._
import weaver.{Expectations, IOSuite}
import weaver.scalacheck.{CheckConfig, Checkers}

abstract class ResourceSuite extends IOSuite with Checkers {
  override def checkConfig: CheckConfig =
    CheckConfig.default.copy(minimumSuccessful = 1)

  implicit class SharedResOps(res: Resource[IO, Res]) {
    def beforeAll(f: Res => IO[Unit]): Resource[IO, Res] = res.evalTap(f)
    def afterAll(f: Res => IO[Unit]): Resource[IO, Res] =
      res.flatTap(x => Resource.make(IO.unit)(_ => f(x)))
  }

  type TestDefinition = String => (Res => IO[Expectations]) => Unit

  def testBeforeAfterEach(before: Res => IO[Unit], after: Res => IO[Unit]): TestDefinition =
    name => fa => test(name)(res => before(res) >> fa(res).guarantee(after(res)))

  def testBeforeEach(before: Res => IO[Unit]): TestDefinition =
    testBeforeAfterEach(before, _ => IO.unit)

  def testAfterEach(after: Res => IO[Unit]): TestDefinition =
    testBeforeAfterEach(_ => IO.unit, after)
}
