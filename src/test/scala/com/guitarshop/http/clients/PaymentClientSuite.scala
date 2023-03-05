package com.guitarshop.http.clients

import cats.data.Kleisli
import cats.effect.IO
import com.guitarshop.config.types._
import com.guitarshop.domain.order._
import com.guitarshop.domain.payment._
import com.guitarshop.generators._
import org.http4s._
import org.http4s.Method.POST
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.dsl.io._
import org.scalacheck.Gen
import eu.timepit.refined.auto._
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object PaymentClientSuite extends SimpleIOSuite with Checkers {

  val config = PaymentConfig(PaymentURI("http://localhost"))

  def routes(mkResponse: IO[Response[IO]]): Kleisli[IO, Request[IO], Response[IO]] =
    HttpRoutes
      .of[IO] { case POST -> Root / "payments" => mkResponse }
      .orNotFound

  val gen: Gen[(PaymentId, Payment)] = for {
    i <- paymentIdGen
    p <- paymentGen
  } yield i -> p

  test("Response Ok (200)") {
    forall(gen) { case (pid, payment) =>
      val client: Client[IO] = Client.fromHttpApp(routes(Ok(pid)))

      PaymentClient
        .make[IO](config, client)
        .process(payment)
        .map(expect.same(pid, _))
    }
  }

  test("Response Conflict (409)") {
    forall(gen) { case (pid, payment) =>
      val client: Client[IO] = Client.fromHttpApp(routes(Conflict(pid)))

      PaymentClient
        .make[IO](config, client)
        .process(payment)
        .map(expect.same(pid, _))
    }
  }

  test("Internal Server Error (500)") {
    forall(gen) { case (_, payment) =>
      val client: Client[IO] = Client.fromHttpApp(routes(InternalServerError()))

      PaymentClient
        .make[IO](config, client)
        .process(payment)
        .attempt
        .map {
          case Left(e) => expect.same(PaymentError("Internal Server Error"), e)
          case _       => failure("Expected payment error")
        }
    }
  }
}
