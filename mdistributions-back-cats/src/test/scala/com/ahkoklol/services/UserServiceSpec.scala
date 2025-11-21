package com.ahkoklol.services

import com.ahkoklol.IntegrationSpec
import com.ahkoklol.domain.{User, UserError}
import com.ahkoklol.repositories.UserRepository
import java.util.UUID

class UserServiceSpec extends IntegrationSpec {

  lazy val userRepo = UserRepository.make(xa)
  lazy val userService = UserService.make(userRepo)

  "UserService" should "register a new user with a hashed password" in {
    val email = s"test-${UUID.randomUUID()}@example.com"
    val password = "mySecretPassword123"

    val result = userService.register(email, password, Some("John"), Some("Doe")).unwrap

    result.isRight shouldBe true
    val user = result.toOption.get
    
    user.email shouldBe email
    user.firstName shouldBe Some("John")
    user.passwordHash should not be password 
    user.passwordHash.length should be > 0
  }

  it should "fail to register if email already exists" in {
    val email = s"duplicate-${UUID.randomUUID()}@example.com"
    userService.register(email, "pass1", None, None).unwrap.isRight shouldBe true

    val result = userService.register(email, "pass2", None, None).unwrap
    result shouldBe Left(UserError.EmailAlreadyExists(email))
  }

  it should "login successfully with correct credentials" in {
    val email = s"login-success-${UUID.randomUUID()}@example.com"
    val password = "correctPassword"
    userService.register(email, password, None, None).unwrap

    val result = userService.login(email, password).unwrap
    result.isRight shouldBe true
    result.toOption.get.email shouldBe email
  }

  it should "fail login with incorrect password" in {
    val email = s"login-fail-${UUID.randomUUID()}@example.com"
    val password = "correctPassword"
    userService.register(email, password, None, None).unwrap

    val result = userService.login(email, "wrongPassword").unwrap
    result shouldBe Left(UserError.InvalidCredentials)
  }

  it should "fail login for non-existent user" in {
    val result = userService.login("ghost@example.com", "anything").unwrap
    result shouldBe Left(UserError.InvalidCredentials)
  }

  it should "update an existing user" in {
    val email = s"update-${UUID.randomUUID()}@example.com"
    val user = userService.register(email, "pass", Some("OldName"), None).unwrap.toOption.get

    val toUpdate = user.copy(firstName = Some("NewName"))
    val result = userService.update(toUpdate).unwrap

    result.isRight shouldBe true
    result.toOption.get.firstName shouldBe Some("NewName")

    val fetched = userRepo.findByEmail(email).unwrap
    fetched.get.firstName shouldBe Some("NewName")
  }

  it should "fail to update a non-existent user" in {
    val fakeUser = User(
      id = UUID.randomUUID(),
      passwordHash = "hash",
      firstName = None,
      lastName = None,
      email = "fake@example.com",
      customerDataSheetUrl = None
    )

    val result = userService.update(fakeUser).unwrap
    result shouldBe Left(UserError.UserNotFound)
  }

  it should "delete a user" in {
    val email = s"delete-${UUID.randomUUID()}@example.com"
    val user = userService.register(email, "pass", None, None).unwrap.toOption.get

    userService.delete(user.id).unwrap

    val loginResult = userService.login(email, "pass").unwrap
    loginResult shouldBe Left(UserError.InvalidCredentials)
  }
}