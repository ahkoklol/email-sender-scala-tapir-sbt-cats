package com.ahkoklol.workers

import cats.effect.{IO, Temporal}
import cats.implicits.*
import com.ahkoklol.domain.{Email, User}
import com.ahkoklol.repositories.{EmailRepository, UserRepository}
import com.ahkoklol.integrations.{GoogleSheets, SmtpMailer}
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

object EmailWorker:
  
  def start(
      emailRepo: EmailRepository,
      userRepo: UserRepository,
      sheets: GoogleSheets,
      mailer: SmtpMailer
  ): IO[Unit] = 
    val process = for {
      pendingEmails <- emailRepo.findPending
      _             <- pendingEmails.traverse(email => processEmail(email, emailRepo, userRepo, sheets, mailer))
    } yield ()

    // Run every 10 seconds
    fs2.Stream.fixedRate[IO](10.seconds)
      .evalMap(_ => process)
      .compile
      .drain

  private def processEmail(
      email: Email,
      emailRepo: EmailRepository,
      userRepo: UserRepository,
      sheets: GoogleSheets,
      mailer: SmtpMailer
  ): IO[Unit] =
    (for {
      // 1. Get the user who owns the campaign (Now using the proper findById)
      userOpt <- userRepo.findById(email.userId)
      user    <- IO.fromOption(userOpt)(new Exception(s"User ${email.userId} not found"))

      // 2. Get Emails from Sheet
      sheetUrl <- IO.fromOption(user.customerDataSheetUrl)(new Exception("No Sheet URL provided"))
      recipients <- sheets.fetchEmails(sheetUrl)

      // 3. Send Emails
      // We handle individual send errors here so one bad email doesn't crash the whole batch
      _ <- recipients.traverse { recipient =>
        mailer.send(recipient, email.subject, email.body)
          .handleErrorWith(e => IO.println(s"Failed to send to $recipient: ${e.getMessage}"))
      }

      // 4. Mark as Sent
      // Truncate to MICROS to ensure compatibility with Postgres TIMESTAMP precision
      updatedEmail = email.copy(sentAt = Some(Instant.now().truncatedTo(ChronoUnit.MICROS)))
      _ <- emailRepo.update(updatedEmail)
      
    } yield ()).handleErrorWith { err =>
      // 5. Handle Job Failure (Update DB with error message)
      IO.println(s"Job failed for email ${email.id}: ${err.getMessage}") *>
      emailRepo.update(email.copy(errorMessage = Some(err.getMessage.take(255)))).void
    }