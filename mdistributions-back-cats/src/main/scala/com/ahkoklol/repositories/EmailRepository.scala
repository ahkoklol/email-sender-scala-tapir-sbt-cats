package com.ahkoklol.repositories

import com.ahkoklol.domain.Email
import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import java.util.UUID

trait EmailRepository:
  def create(email: Email): IO[Email]
  def findById(id: UUID): IO[Option[Email]]
  def findAllByUser(userId: UUID): IO[List[Email]]
  def findPending: IO[List[Email]]
  def update(email: Email): IO[Option[Email]]
  def delete(id: UUID): IO[Boolean]

object EmailRepository:
  def make(xa: Transactor[IO]): EmailRepository = new EmailRepository:

    override def create(email: Email): IO[Email] =
      sql"""
        INSERT INTO emails (id, user_id, subject, body, created_at, sent_at, error_message)
        VALUES (
          ${email.id},
          ${email.userId},
          ${email.subject},
          ${email.body},
          ${email.createdAt},
          ${email.sentAt},
          ${email.errorMessage}
        )
      """.update.run.transact(xa).map(_ => email)

    override def findById(id: UUID): IO[Option[Email]] =
      sql"""
        SELECT id, user_id, subject, body, created_at, sent_at, error_message
        FROM emails
        WHERE id = $id
      """.query[Email].option.transact(xa)

    override def findAllByUser(userId: UUID): IO[List[Email]] =
      sql"""
        SELECT id, user_id, subject, body, created_at, sent_at, error_message
        FROM emails
        WHERE user_id = $userId
        ORDER BY created_at DESC
      """.query[Email].to[List].transact(xa)

    override def findPending: IO[List[Email]] =
      sql"""
        SELECT id, user_id, subject, body, created_at, sent_at, error_message
        FROM emails
        WHERE sent_at IS NULL AND error_message IS NULL
      """.query[Email].to[List].transact(xa)

    override def update(email: Email): IO[Option[Email]] =
      sql"""
        UPDATE emails
        SET subject = ${email.subject},
            body = ${email.body},
            sent_at = ${email.sentAt},
            error_message = ${email.errorMessage}
        WHERE id = ${email.id}
      """.update.run.transact(xa).map { affectedRows =>
        if (affectedRows > 0) Some(email) else None
      }

    override def delete(id: UUID): IO[Boolean] =
      sql"""
        DELETE FROM emails
        WHERE id = $id
      """.update.run.transact(xa).map(affectedRows => affectedRows > 0)