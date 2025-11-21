package com.ahkoklol.services

import com.ahkoklol.domain.{User, UserError}
import com.ahkoklol.repositories.UserRepository
import cats.effect.IO
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import java.time.Instant

trait UserService:
  def register(
      email: String, 
      password: String, 
      firstName: Option[String], 
      lastName: Option[String]
  ): IO[Either[UserError, User]]

  def login(email: String, password: String): IO[Either[UserError, User]]
  
  def update(user: User): IO[Either[UserError, User]]
  
  def delete(userId: UUID): IO[Unit]

object UserService:
  def make(repo: UserRepository): UserService = new UserService:

    override def register(
        email: String,
        password: String,
        firstName: Option[String],
        lastName: Option[String]
    ): IO[Either[UserError, User]] =
      repo.findByEmail(email).flatMap {
        case Some(_) => 
          IO.pure(Left(UserError.EmailAlreadyExists(email)))
        case None =>
          // 1. Hash the password securely
          val hash = BCrypt.hashpw(password, BCrypt.gensalt())
          
          // 2. Create User Model
          val newUser = User(
            id = UUID.randomUUID(),
            passwordHash = hash,
            firstName = firstName,
            lastName = lastName,
            email = email,
            customerDataSheetUrl = None
          )

          // 3. Save to DB
          repo.create(newUser).map(saved => Right(saved))
      }

    override def login(email: String, password: String): IO[Either[UserError, User]] =
      repo.findByEmail(email).map {
        case None => Left(UserError.InvalidCredentials)
        case Some(user) =>
          // Check if the plain password matches the stored hash
          if (BCrypt.checkpw(password, user.passwordHash)) Right(user)
          else Left(UserError.InvalidCredentials)
      }

    override def update(user: User): IO[Either[UserError, User]] =
      repo.update(user).map {
        case Some(updated) => Right(updated)
        case None          => Left(UserError.UserNotFound)
      }

    override def delete(userId: UUID): IO[Unit] =
      repo.delete(userId).void