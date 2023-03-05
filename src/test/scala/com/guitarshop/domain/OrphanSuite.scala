package com.guitarshop.domain

import com.guitarshop.generators.moneyGen

import cats.kernel.laws.discipline.MonoidTests
import org.scalacheck.Arbitrary
import squants.market.Money
import weaver.FunSuite
import weaver.discipline.Discipline

trait OrphanSuite extends FunSuite with Discipline {

  implicit val arbMoney: Arbitrary[Money] = Arbitrary(moneyGen)

  checkAll("Monoid[Money]", MonoidTests[Money].monoid)

}
