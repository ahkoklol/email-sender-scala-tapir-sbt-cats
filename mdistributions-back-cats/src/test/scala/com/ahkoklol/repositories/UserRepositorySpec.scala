package com.ahkoklol.repositories

import com.ahkoklol.IntegrationSpec
import com.ahkoklol.domain.User
import java.util.UUID

class UserRepositorySpec extends IntegrationSpec {

  lazy val repo = UserRepository.make(xa)

  def randomUser(): User = {
    val id = UUID.randomUUID()
    User(
      id = id,
      passwordHash = "secret_hash",
      firstName = Some("John"),
      lastName = Some("Doe"),
      email = s"user_${id}@example.com",
      customerDataSheetUrl = None
    )
  }

  "UserRepository" should "create and find a user by email" in {
    val user = randomUser()

    val created = repo.create(user).unwrap
    created shouldBe user

    val fetched = repo.findByEmail(user.email).unwrap
    fetched shouldBe Some(user)
  }

  it should "return None when finding a non-existent email" in {
    repo.findByEmail("ghost@example.com").unwrap shouldBe None
  }

  it should "update an existing user" in {
    val user = randomUser()
    repo.create(user).unwrap

    val updatedUser = user.copy(
      firstName = Some("Jane"),
      passwordHash = "new_secret"
    )

    val result = repo.update(updatedUser).unwrap
    result shouldBe Some(updatedUser)

    val fetched = repo.findByEmail(user.email).unwrap
    fetched.get.firstName shouldBe Some("Jane")
    fetched.get.passwordHash shouldBe "new_secret"
  }

  it should "return None when updating a non-existent user" in {
    val ghostUser = randomUser()
    repo.update(ghostUser).unwrap shouldBe None
  }

  it should "delete a user" in {
    val user = randomUser()
    repo.create(user).unwrap

    repo.delete(user.id).unwrap shouldBe true
    repo.findByEmail(user.email).unwrap shouldBe None
  }

  it should "return false when deleting a non-existent user" in {
    repo.delete(UUID.randomUUID()).unwrap shouldBe false
  }
}