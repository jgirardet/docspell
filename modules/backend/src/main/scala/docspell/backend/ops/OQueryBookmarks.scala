/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.backend.ops

import cats.data.OptionT
import cats.effect._
import cats.implicits._

import docspell.common._
import docspell.query.ItemQuery
import docspell.store.AddResult
import docspell.store.Store
import docspell.store.UpdateResult
import docspell.store.records._

trait OQueryBookmarks[F[_]] {

  def getAll(account: AccountId): F[Vector[OQueryBookmarks.Bookmark]]

  def findOne(account: AccountId, nameOrId: String): F[Option[OQueryBookmarks.Bookmark]]

  def create(account: AccountId, bookmark: OQueryBookmarks.NewBookmark): F[AddResult]

  def update(
      account: AccountId,
      id: Ident,
      bookmark: OQueryBookmarks.NewBookmark
  ): F[UpdateResult]

  def delete(account: AccountId, bookmark: Ident): F[Unit]
}

object OQueryBookmarks {
  final case class NewBookmark(
      name: String,
      label: Option[String],
      query: ItemQuery,
      personal: Boolean
  )

  final case class Bookmark(
      id: Ident,
      name: String,
      label: Option[String],
      query: ItemQuery,
      personal: Boolean,
      created: Timestamp
  )

  def apply[F[_]: Sync](store: Store[F]): Resource[F, OQueryBookmarks[F]] =
    Resource.pure(new OQueryBookmarks[F] {
      def getAll(account: AccountId): F[Vector[Bookmark]] =
        store
          .transact(RQueryBookmark.allForUser(account))
          .map(_.map(convert.toModel))

      def findOne(
          account: AccountId,
          nameOrId: String
      ): F[Option[OQueryBookmarks.Bookmark]] =
        store
          .transact(RQueryBookmark.findByNameOrId(account, nameOrId))
          .map(_.map(convert.toModel))

      def create(account: AccountId, b: NewBookmark): F[AddResult] = {
        val record =
          RQueryBookmark.createNew(account, b.name, b.label, b.query, b.personal)
        store.transact(RQueryBookmark.insertIfNotExists(account, record))
      }

      def update(account: AccountId, id: Ident, b: NewBookmark): F[UpdateResult] =
        UpdateResult.fromUpdate(
          store.transact {
            (for {
              userId <- OptionT(RUser.findIdByAccount(account))
              n <- OptionT.liftF(
                RQueryBookmark.update(convert.toRecord(account, id, userId, b))
              )
            } yield n).getOrElse(0)
          }
        )

      def delete(account: AccountId, bookmark: Ident): F[Unit] =
        store.transact(RQueryBookmark.deleteById(account.collective, bookmark)).as(())
    })

  private object convert {

    def toModel(r: RQueryBookmark): Bookmark =
      Bookmark(r.id, r.name, r.label, r.query, r.isPersonal, r.created)

    def toRecord(
        account: AccountId,
        id: Ident,
        userId: Ident,
        b: NewBookmark
    ): RQueryBookmark =
      RQueryBookmark(
        id,
        b.name,
        b.label,
        if (b.personal) userId.some else None,
        account.collective,
        b.query,
        Timestamp.Epoch
      )

  }
}
