package com.ahkoklol.repositories

import com.ahkoklol.IntegrationSpec
import com.ahkoklol.domain.{Email, User}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class EmailRepositorySpec extends IntegrationSpec {

  lazy val userRepo  = UserRepository.make(xa)
  lazy val emailRepo = EmailRepository.make(xa)

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

  // UPDATED: Now includes recipients list
  def randomEmail(userId: UUID): Email = {
    val id = UUID.randomUUID()
    Email(
      id = id,
      userId = userId,
      subject = s"Subject $id",
      body = "Body content",
      recipients = List("a@test.com", "b@test.com"), // <--- New Field
      createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
      sentAt = None,
      errorMessage = None
    )
  }

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
    // Verify the list was stored correctly
    fetched.get.recipients should contain theSameElementsAs List("a@test.com", "b@test.com")
  }

  it should "find all emails for a specific user" in {
    val user1 = createAndPersistUser()
    val user2 = createAndPersistUser()

    val e1 = randomEmail(user1.id)
    val e2 = randomEmail(user1.id)
    emailRepo.create(e1).unwrap
    emailRepo.create(e2).unwrap

    val e3 = randomEmail(user2.id)
    emailRepo.create(e3).unwrap

    val user1Emails = emailRepo.findAllByUser(user1.id).unwrap
    user1Emails should have size 2
    user1Emails.map(_.id) should contain theSameElementsAs List(e1.id, e2.id)

    val user2Emails = emailRepo.findAllByUser(user2.id).unwrap
    user2Emails should have size 1
    user2Emails.head.id shouldBe e3.id
  }

  it should "find only pending emails" in {
    val user = createAndPersistUser()

    val pending = randomEmail(user.id)
    emailRepo.create(pending).unwrap

    val sent = randomEmail(user.id).copy(sentAt = Some(Instant.now().truncatedTo(ChronoUnit.MICROS)))
    emailRepo.create(sent).unwrap

    val failed = randomEmail(user.id).copy(errorMessage = Some("SMTP Error"))
    emailRepo.create(failed).unwrap

    val pendingList = emailRepo.findPending.unwrap
    
    pendingList.map(_.id) should contain(pending.id)
    pendingList.map(_.id) should not contain(sent.id)
    pendingList.map(_.id) should not contain(failed.id)
  }

  it should "update an email" in {
    val user = createAndPersistUser()
    val email = randomEmail(user.id)
    emailRepo.create(email).unwrap

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