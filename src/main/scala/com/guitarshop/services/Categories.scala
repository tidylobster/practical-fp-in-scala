package com.guitarshop.services

import cats.effect.Resource
import com.guitarshop.domain.category._
import com.guitarshop.sql.codecs._
import cats.effect._
import cats.implicits._
import com.guitarshop.domain.ID
import com.guitarshop.effects.GenUUID
import skunk._
import skunk.implicits._

trait Categories[F[_]] {
  def findAll: F[List[Category]]
  def create(name: CategoryName): F[CategoryId]
}

object Categories {
  def make[F[_]: GenUUID: MonadCancelThrow](postgres: Resource[F, Session[F]]): Categories[F] =
    new Categories[F] {
      import CategoriesSQL._

      override def findAll: F[List[Category]] =
        postgres.use(_.execute(selectAll))

      override def create(name: CategoryName): F[CategoryId] =
        postgres.use { session =>
          session.prepare(insertCategory).use { cmd =>
            ID.make[F, CategoryId].flatMap { id =>
              cmd.execute(Category(id, name)).as(id)
            }
          }
        }
    }
}

private object CategoriesSQL {

  val codec: Codec[Category] =
    (categoryId ~ categoryName).imap { case i ~ n =>
      Category(i, n)
    }(c => c.id ~ c.name)

  val selectAll: Query[Void, Category] =
    sql"""
         SELECT * FROM categories
       """.query(codec)

  val insertCategory: Command[Category] =
    sql"""
         INSERT INTO categories
         VALUES ($codec)
       """.command
}
