package com.ahkoklol.services

import com.ahkoklol.domain.{Email, EmailError}
import com.ahkoklol.repositories.EmailRepository
import cats.effect.IO
import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.io.File 
import com.ahkoklol.utils.ExcelParser

trait EmailService:
  // def create(userId: UUID, subject: String, body: String): IO[Email] // for google sheets
  def create(userId: UUID, subject: String, body: String, file: File): IO[Email] // for imported excel
  def findAll(userId: UUID): IO[List[Email]]
  def find(userId: UUID, emailId: UUID): IO[Either[EmailError, Email]]
  def delete(userId: UUID, emailId: UUID): IO[Either[EmailError, Unit]]

object EmailService:
  def make(repo: EmailRepository): EmailService = new EmailService:

    override def create(userId: UUID, subject: String, body: String, file: File): IO[Email] =
      for {
        // 1. Parse File
        recipients <- ExcelParser.parse(file)
        
        // 2. Create Object
        newEmail = Email(
          id = UUID.randomUUID(),
          userId = userId,
          subject = subject,
          body = body,
          recipients = recipients, // Store them
          createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
          sentAt = None,
          errorMessage = None
        )
        
        // 3. Save
        saved <- repo.create(newEmail)
      } yield saved

    override def findAll(userId: UUID): IO[List[Email]] =
      repo.findAllByUser(userId)

    override def find(userId: UUID, emailId: UUID): IO[Either[EmailError, Email]] =
      repo.findById(emailId).map {
        case Some(email) if email.userId == userId => Right(email)
        case Some(_) => Left(EmailError.AccessDenied) // Exists, but belongs to someone else
        case None    => Left(EmailError.EmailNotFound)
      }

    override def delete(userId: UUID, emailId: UUID): IO[Either[EmailError, Unit]] =
      repo.findById(emailId).flatMap {
        case Some(email) if email.userId == userId =>
          repo.delete(emailId).map(_ => Right(()))
        case Some(_) => 
          IO.pure(Left(EmailError.AccessDenied))
        case None => 
          IO.pure(Left(EmailError.EmailNotFound))
      }