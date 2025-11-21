package com.ahkoklol.repositories

import com.ahkoklol.IntegrationSpec
import com.ahkoklol.domain.{Email, User}
import java.time.Instant
import java.util.UUID
import java.time.temporal.ChronoUnit

class EmailRepositorySpec extends IntegrationSpec {

  lazy val userRepo  = UserRepository.make(xa)
  lazy val emailRepo = EmailRepository.make(xa)

    // Helper to generate random users and emails
  def randomUser(): User = {
    val id = UUID.randomUUID()
    User(
      id = id,
      passwordHash = "hash",
      firstName = Some("Test"),
      lastName = Some("User"),
      email = s"user-$id@example.com",
      customerDataSheetUrl = None
    )
  }

    // Helper to generate random emails
  def randomEmail(userId: UUID): Email = {
    val id = UUID.randomUUID()
    Email(
        id = id,
        userId = userId,
        subject = s"Subject $id",
        body = "Body content",
        // CHANGE: Truncate precision to match Postgres (Micros)
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
        sentAt = None,
        errorMessage = None
    )
    }

  // Helper to persist a parent user so we can attach emails to it
  def createAndPersistUser(): User = {
    val u = randomUser()
    userRepo.create(u).unwrap
    u
  }

  "EmailRepository" should "create and find an email by ID" in {
    val user = createAndPersistUser()
    val email = randomEmail(user.id)

    val created = emailRepo.create(email).unwrap
    created shouldBe email

    val fetched = emailRepo.findById(email.id).unwrap
    fetched shouldBe Some(email)
  }

  it should "find all emails for a specific user" in {
    val user1 = createAndPersistUser()
    val user2 = createAndPersistUser()

    // Create 2 emails for User 1
    val e1 = randomEmail(user1.id)
    val e2 = randomEmail(user1.id)
    emailRepo.create(e1).unwrap
    emailRepo.create(e2).unwrap

    // Create 1 email for User 2
    val e3 = randomEmail(user2.id)
    emailRepo.create(e3).unwrap

    // Fetch for User 1
    val user1Emails = emailRepo.findAllByUser(user1.id).unwrap
    user1Emails should have size 2
    user1Emails.map(_.id) should contain theSameElementsAs List(e1.id, e2.id)

    // Fetch for User 2
    val user2Emails = emailRepo.findAllByUser(user2.id).unwrap
    user2Emails should have size 1
    user2Emails.head.id shouldBe e3.id
  }

  it should "find only pending emails" in {
    val user = createAndPersistUser()

    // 1. Pending Email
    val pending = randomEmail(user.id)
    emailRepo.create(pending).unwrap

    // 2. Sent Email
    val sent = randomEmail(user.id).copy(sentAt = Some(Instant.now()))
    emailRepo.create(sent).unwrap

    // 3. Failed Email
    val failed = randomEmail(user.id).copy(errorMessage = Some("SMTP Error"))
    emailRepo.create(failed).unwrap

    // Check logic
    val pendingList = emailRepo.findPending.unwrap
    
    pendingList.map(_.id) should contain(pending.id)
    pendingList.map(_.id) should not contain(sent.id)
    pendingList.map(_.id) should not contain(failed.id)
  }

  it should "update an email" in {
    val user = createAndPersistUser()
    val email = randomEmail(user.id)
    emailRepo.create(email).unwrap

    // CHANGE: Truncate here as well
    val sentTime = Instant.now().truncatedTo(ChronoUnit.MICROS)
    
    val updatedEmail = email.copy(
        sentAt = Some(sentTime),
        subject = "Updated Subject"
    )

    val result = emailRepo.update(updatedEmail).unwrap
    result shouldBe Some(updatedEmail)

    val fetched = emailRepo.findById(email.id).unwrap
    fetched.get.sentAt shouldBe Some(sentTime)
    fetched.get.subject shouldBe "Updated Subject"
    }

  it should "delete an email" in {
    val user = createAndPersistUser()
    val email = randomEmail(user.id)
    emailRepo.create(email).unwrap

    emailRepo.delete(email.id).unwrap shouldBe true
    emailRepo.findById(email.id).unwrap shouldBe None
  }
}