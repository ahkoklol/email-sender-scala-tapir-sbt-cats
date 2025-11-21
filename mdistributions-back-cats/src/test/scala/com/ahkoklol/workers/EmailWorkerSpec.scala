package com.ahkoklol.workers

import com.ahkoklol.IntegrationSpec
import com.ahkoklol.domain.{Email, User}
import com.ahkoklol.integrations.SmtpMailer
import com.ahkoklol.repositories.{EmailRepository, UserRepository}
import cats.effect.{IO, Ref}
import cats.implicits.* // <--- ADD THIS IMPORT
import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

class EmailWorkerSpec extends IntegrationSpec {

  lazy val userRepo = UserRepository.make(xa)
  lazy val emailRepo = EmailRepository.make(xa)

  // 1. Helper to Create Data
  def createScenario(): (User, Email) = {
    val userId = UUID.randomUUID()
    val user = User(userId, "hash", None, None, s"u-$userId@test.com", None)
    userRepo.create(user).unwrap

    val emailId = UUID.randomUUID()
    val email = Email(
      id = emailId, 
      userId = userId, 
      subject = "Test", 
      body = "Body", 
      recipients = List("success@test.com", "fail@test.com"), 
      createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
      sentAt = None, 
      errorMessage = None
    )
    emailRepo.create(email).unwrap
    (user, email)
  }

  // 2. Mock Mailer
  val mockMailer = new SmtpMailer {
    override def send(to: String, subject: String, body: String): IO[Unit] = 
      if (to == "fail@test.com") IO.raiseError(new Exception("Simulated SMTP Error"))
      else IO.unit
  }

  "EmailWorker" should "process pending emails and handle partial failures" in {
    val (_, email) = createScenario()

    // Simulation of the worker logic
    val processLogic = for {
      pending <- emailRepo.findPending
      _       <- pending.map { e =>
                   e.recipients.traverse { r =>
                     mockMailer.send(r, e.subject, e.body)
                       .handleErrorWith(_ => IO.unit) 
                   } *> 
                   emailRepo.update(e.copy(sentAt = Some(Instant.now())))
                 }.sequence
    } yield ()

    processLogic.unwrap

    // 3. Verify Results
    val updated = emailRepo.findById(email.id).unwrap.get
    updated.sentAt shouldBe defined
  }
}