package com.ahkoklol.repositories

import com.ahkoklol.domain.User
import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import java.util.UUID

trait UserRepository:
  def create(user: User): IO[User] // Returns the created user
  def findByEmail(email: String): IO[Option[User]] // Returns Some(user) if found, None if not found
  def update(user: User): IO[Option[User]] // Returns Some(user) if found & updated, None if not found
  def delete(userId: UUID): IO[Boolean] // Returns true if deleted, false if user didn't exist

object UserRepository:
  def make(xa: Transactor[IO]): UserRepository = new UserRepository:
    
    override def create(user: User): IO[User] =
      sql"""
        INSERT INTO users (id, password_hash, first_name, last_name, email, customer_data_sheet_url)
        VALUES (
          ${user.id}, 
          ${user.passwordHash}, 
          ${user.firstName}, 
          ${user.lastName}, 
          ${user.email}, 
          ${user.customerDataSheetUrl}
        )
      """.update.run.transact(xa).map(_ => user)

    override def findByEmail(email: String): IO[Option[User]] =
      sql"""
        SELECT id, password_hash, first_name, last_name, email, customer_data_sheet_url
        FROM users
        WHERE email = $email
      """.query[User].option.transact(xa)

    override def update(user: User): IO[Option[User]] =
      sql"""
        UPDATE users
        SET password_hash = ${user.passwordHash},
            first_name = ${user.firstName},
            last_name = ${user.lastName},
            email = ${user.email},
            customer_data_sheet_url = ${user.customerDataSheetUrl}
        WHERE id = ${user.id}
      """.update.run.transact(xa).map { affectedRows =>
        if (affectedRows > 0) Some(user) else None
      }

    override def delete(userId: UUID): IO[Boolean] =
      sql"""
        DELETE FROM users
        WHERE id = $userId
      """.update.run.transact(xa).map(affectedRows => affectedRows > 0)