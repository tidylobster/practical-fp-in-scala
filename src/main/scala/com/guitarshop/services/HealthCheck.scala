package com.guitarshop.services

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import com.guitarshop.domain.healthcheck.{AppStatus, PostgresStatus, RedisStatus, Status}
import dev.profunktor.redis4cats.RedisCommands
import skunk._
import skunk.codec.all._
import skunk.implicits._

import scala.concurrent.duration._

trait HealthCheck[F[_]] {
  def status: F[AppStatus]
}

object HealthCheck {
  def make[F[_]: Temporal](
      postgres: Resource[F, Session[F]],
      redis: RedisCommands[F, String, String]
  ): HealthCheck[F] =
    new HealthCheck[F] {
      val redisHealth: F[RedisStatus] =
        redis.ping
          .map(_.nonEmpty)
          .timeout(1.second)
          .map(Status._Bool.reverseGet)
          .orElse(Status.Unreachable.pure[F].widen)
          .map(RedisStatus.apply)

      val q: Query[Void, Int] =
        sql"""SELECT pid FROM pg_stat_activity""".query(int4)

      val postgresHealth: F[PostgresStatus] =
        postgres
          .use(_.execute(q))
          .map(_.nonEmpty)
          .timeout(1.second)
          .map(Status._Bool.reverseGet)
          .orElse(Status.Unreachable.pure[F].widen)
          .map(PostgresStatus.apply)

      override def status: F[AppStatus] =
        (redisHealth, postgresHealth).parMapN(AppStatus.apply)
    }
}
