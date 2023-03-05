package com.guitarshop.services

import com.guitarshop.domain.ID
import com.guitarshop.domain.item._
import com.guitarshop.domain.brand._
import com.guitarshop.domain.category._
import com.guitarshop.effects.GenUUID
import com.guitarshop.sql.codecs._

import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.implicits._

trait Items[F[_]] {
  def findAll: F[List[Item]]
  def findBy(brand: BrandName): F[List[Item]]
  def findById(itemId: ItemId): F[Option[Item]]
  def create(item: CreateItem): F[ItemId]
  def update(item: UpdateItem): F[Unit]
}

object Items {
  def make[F[_]: Concurrent: GenUUID](postgres: Resource[F, Session[F]]): Items[F] =
    new Items[F] {
      import ItemsSQL._

      override def findAll: F[List[Item]] =
        postgres.use(_.execute(selectAll))

      override def findBy(brand: BrandName): F[List[Item]] =
        postgres.use { session =>
          session.prepare(selectByBrand).use { ps =>
            ps.stream(brand, 1024).compile.toList
          }
        }

      override def findById(itemId: ItemId): F[Option[Item]] =
        postgres.use { session =>
          session.prepare(selectById).use { ps =>
            ps.option(itemId)
          }
        }

      override def create(item: CreateItem): F[ItemId] =
        postgres.use { session =>
          session.prepare(insertItem).use { cmd =>
            ID.make[F, ItemId].flatMap { id =>
              cmd.execute(id ~ item).as(id)
            }
          }
        }

      override def update(item: UpdateItem): F[Unit] =
        postgres.use { session =>
          session.prepare(updateItem).use { cmd =>
            cmd.execute(item).void
          }
        }
    }
}

private object ItemsSQL {

  val decoder: Decoder[Item] =
    (itemId ~ itemName ~ itemDesc ~ money ~ brandId ~ brandName ~ categoryId ~ categoryName).map {
      case i ~ n ~ d ~ p ~ bi ~ bn ~ ci ~ cn =>
        Item(i, n, d, p, Brand(bi, bn), Category(ci, cn))
    }

  val selectAll: Query[Void, Item] =
    sql"""
         SELECT i.uuid, i.name, i.description, i.price,
                b.uuid, b.name, c.uuid, c.name
         FROM items AS i
         INNER JOIN brands AS b ON i.brand_id = b.uuid
         INNER JOIN categories AS c ON i.category_id = c.uuid
       """.query(decoder)

  val selectByBrand: Query[BrandName, Item] =
    sql"""
         SELECT i.uuid, i.name, i.description, i.price,
                b.uuid, b.name, c.uuid, c.name
         FROM items AS i
         INNER JOIN brands AS b ON i.brand_id = b.uuid
         INNER JOIN categories AS c ON i.category_id = c.uuid
         WHERE b.name LIKE $brandName
       """.query(decoder)

  val selectById: Query[ItemId, Item] =
    sql"""
         SELECT i.uuid, i.name, i.description, i.price,
                b.uuid, b.name, c.uuid, c.name
         FROM items AS i
         INNER JOIN brands AS b ON i.brand_id = b.uuid
         INNER JOIN categories AS c ON i.category_id = c.uuid
         WHERE i.uuid = $itemId
       """.query(decoder)

  val insertItem: Command[ItemId ~ CreateItem] =
    sql"""
         INSERT INTO items
         VALUES ($itemId, $itemName, $itemDesc, $money, $brandId, $categoryId)
       """.command.contramap { case id ~ (i: CreateItem) =>
      id ~ i.name ~ i.description ~ i.price ~ i.brandId ~ i.categoryId
    }

  val updateItem: Command[UpdateItem] =
    sql"""
         UPDATE items
         SET price = $money
         WHERE uuid = $itemId
       """.command.contramap { (i: UpdateItem) => i.price ~ i.id }
}
