package com.ahkoklol.workers

import cats.effect.IO
import cats.implicits.*
import com.ahkoklol.domain.Email
import com.ahkoklol.repositories.EmailRepository
import com.ahkoklol.integrations.SmtpMailer
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

object EmailWorker:
  
  def start(
      emailRepo: EmailRepository,
      mailer: SmtpMailer
  ): IO[Unit] = 
    val process = for {
      pendingEmails <- emailRepo.findPending
      _             <- pendingEmails.traverse(email => processEmail(email, emailRepo, mailer))
    } yield ()

    fs2.Stream.fixedRate[IO](10.seconds).evalMap(_ => process).compile.drain

  private def processEmail(
      email: Email,
      emailRepo: EmailRepository,
      mailer: SmtpMailer
  ): IO[Unit] =
    (for {
      // 1. Use the list directly from the database
      _ <- email.recipients.traverse { recipient =>
        mailer.send(recipient, email.subject, email.body)
          .handleErrorWith(e => IO.println(s"Failed to send to $recipient: ${e.getMessage}"))
          .flatMap(_ => IO.sleep(2.seconds))
      }

      // 2. Mark as Sent
      updatedEmail = email.copy(sentAt = Some(Instant.now().truncatedTo(ChronoUnit.MICROS)))
      _ <- emailRepo.update(updatedEmail)
      
    } yield ()).handleErrorWith { err =>
      emailRepo.update(email.copy(errorMessage = Some(err.getMessage.take(255)))).void
    }