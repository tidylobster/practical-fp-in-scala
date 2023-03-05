package com.guitarshop.resources

import cats.effect._
import cats.implicits._
import cats.effect.std.Console
import com.guitarshop.config.types.{AppConfig, PostgreSQLConfig, RedisConfig}
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.effect.MkRedis
import eu.timepit.refined.auto._
import fs2.io.net.Network
import natchez.Trace.Implicits.noop
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import skunk._
import skunk.codec.all._
import skunk.implicits._

sealed abstract class AppResources[F[_]](
    val client: Client[F],
    val postgres: Resource[F, Session[F]],
    val redis: RedisCommands[F, String, String]
)

object AppResources {
  def make[F[_]: Concurrent: Console: Logger: MkHttpClient: Network: MkRedis](
      config: AppConfig
  ): Resource[F, AppResources[F]] = {

    def checkPostgresConnection(postgres: Resource[F, Session[F]]): F[Unit] =
      postgres.use { session =>
        session.unique(sql"select version();".query(text)).flatMap { v =>
          Logger[F].info(s"Connected to Postgres $v")
        }
      }

    def checkRedisConnection(redis: RedisCommands[F, String, String]): F[Unit] =
      redis.info.flatMap {
        _.get("redis_version").traverse_ { v =>
          Logger[F].info(s"Connected to Redis $v")
        }
      }

    def mkPostgreSqlResource(c: PostgreSQLConfig): SessionPool[F] =
      Session
        .pooled[F](
          host = c.host,
          port = c.port,
          user = c.user,
          password = Some(c.password.value),
          database = c.database,
          max = c.max
        )
        .evalTap(checkPostgresConnection)

    def mkRedisResource(c: RedisConfig): Resource[F, RedisCommands[F, String, String]] =
      Redis[F].utf8(c.uri.value).evalTap(checkRedisConnection)

    (
      MkHttpClient[F].newEmber(config.httpClientConfig),
      mkPostgreSqlResource(config.postgreSQL),
      mkRedisResource(config.redis)
    ).parMapN(new AppResources[F](_, _, _) {})
  }
}
