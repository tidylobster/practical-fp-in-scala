package com.guitarshop

import com.guitarshop.config._
import com.guitarshop.modules._
import com.guitarshop.resources._

import cats.effect._
import cats.effect.std.Supervisor
import dev.profunktor.redis4cats.log4cats._
import eu.timepit.refined.auto._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {

  implicit val logger = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] =
    Config
      .load[IO]
      .flatMap { config =>
        Logger[IO].info(s"Loaded config $config") >>
          Supervisor[IO].use { implicit sp =>
            AppResources
              .make[IO](config)
              .evalMap { res =>
                Security
                  .make[IO](config, res.postgres, res.redis)
                  .map { security =>
                    val clients = HttpClients.make[IO](config.paymentConfig, res.client)
                    val services = Services.make[IO](res.redis, res.postgres, config.cartExpiration)
                    val programs = Programs.make[IO](config.checkoutConfig, services, clients)
                    val api = HttpApi.make[IO](services, programs, security)
                    config.httpServerConfig -> api.httpApp
                  }
              }
              .flatMap { case (config, httpApp) => MkHttpServer[IO].newEmber(config, httpApp) }
              .useForever
          }
      }
}
