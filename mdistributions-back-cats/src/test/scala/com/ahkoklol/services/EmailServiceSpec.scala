package com.ahkoklol.services

import com.ahkoklol.IntegrationSpec
import com.ahkoklol.domain.{Email, EmailError}
import com.ahkoklol.repositories.{EmailRepository, UserRepository}
import java.util.UUID

class EmailServiceSpec extends IntegrationSpec {

  lazy val userRepo = UserRepository.make(xa)
  lazy val emailRepo = EmailRepository.make(xa)
  lazy val emailService = EmailService.make(emailRepo)

  // Helper to create a dummy user for testing
  def createUser(): UUID = {
    val id = UUID.randomUUID()
    val u = com.ahkoklol.domain.User(
      id, "hash", None, None, s"user-$id@example.com", None
    )
    userRepo.create(u).unwrap
    id
  }

  "EmailService" should "create and retrieve an email" in {
    val userId = createUser()
    val subject = "Marketing Campaign #1"
    val body = "Hello World"

    // Create
    val email = emailService.create(userId, subject, body).unwrap
    email.userId shouldBe userId
    email.subject shouldBe subject
    email.sentAt shouldBe None

    // Retrieve
    val fetched = emailService.find(userId, email.id).unwrap
    fetched shouldBe Right(email)
  }

  it should "list all emails for a user" in {
    val userId = createUser()
    emailService.create(userId, "Subj 1", "Body 1").unwrap
    emailService.create(userId, "Subj 2", "Body 2").unwrap

    val list = emailService.findAll(userId).unwrap
    list should have size 2
  }

  it should "prevent accessing someone else's email (AccessDenied)" in {
    val aliceId = createUser()
    val bobId = createUser()

    // Alice creates an email
    val aliceEmail = emailService.create(aliceId, "Secret", "Body").unwrap

    // Bob tries to read it
    val result = emailService.find(bobId, aliceEmail.id).unwrap
    result shouldBe Left(EmailError.AccessDenied)
  }

  it should "return EmailNotFound for non-existent ID" in {
    val userId = createUser()
    val result = emailService.find(userId, UUID.randomUUID()).unwrap
    result shouldBe Left(EmailError.EmailNotFound)
  }

  it should "delete an email only if owned by user" in {
    val userId = createUser()
    val email = emailService.create(userId, "To Delete", "Body").unwrap

    // Success case
    emailService.delete(userId, email.id).unwrap shouldBe Right(())
    
    // Verify it's gone
    emailService.find(userId, email.id).unwrap shouldBe Left(EmailError.EmailNotFound)
  }

  it should "prevent deleting someone else's email" in {
    val aliceId = createUser()
    val bobId = createUser()
    val aliceEmail = emailService.create(aliceId, "Alice's Data", "Body").unwrap

    // Bob tries to delete
    val result = emailService.delete(bobId, aliceEmail.id).unwrap
    result shouldBe Left(EmailError.AccessDenied)

    // Verify it still exists
    emailService.find(aliceId, aliceEmail.id).unwrap.isRight shouldBe true
  }
}