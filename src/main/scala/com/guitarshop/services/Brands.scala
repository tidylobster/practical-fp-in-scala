package com.guitarshop.services

import com.guitarshop.effects.GenUUID
import com.guitarshop.domain.ID
import com.guitarshop.domain.brand._
import com.guitarshop.sql.codecs._

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._

trait Brands[F[_]] {
  def findAll: F[List[Brand]]
  def create(name: BrandName): F[BrandId]
}

object Brands {
  def make[F[_]: GenUUID: MonadCancelThrow](postgres: Resource[F, Session[F]]): Brands[F] =
    new Brands[F] {
      import BrandsSQL._

      def findAll: F[List[Brand]] =
        postgres.use(_.execute(selectAll))

      def create(name: BrandName): F[BrandId] =
        postgres.use { session =>
          session.prepare(insertBrand).use { cmd =>
            ID.make[F, BrandId].flatMap { id =>
              cmd.execute(Brand(id, name)).as(id)
            }
          }
        }
    }
}

private object BrandsSQL {

  val codec: Codec[Brand] =
    (brandId ~ brandName).imap { case i ~ n =>
      Brand(i, n)
    }(b => b.id ~ b.name)

  val selectAll: Query[Void, Brand] =
    sql"""
         SELECT * FROM brands
       """.query(codec)

  val insertBrand: Command[Brand] =
    sql"""
         INSERT INTO brands
         VALUES ($codec)
       """.command
}
