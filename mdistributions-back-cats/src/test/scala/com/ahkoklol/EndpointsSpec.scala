package com.ahkoklol

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.ahkoklol.domain.{User, UserError}
import com.ahkoklol.services.{JwtService, UserService}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.integ.cats.effect.CatsMonadError
import java.util.UUID

class EndpointsSpec extends AnyFlatSpec with Matchers with EitherValues {

  // 1. Mock Data
  val userId = UUID.randomUUID()
  val testUser = User(
    id = userId,
    passwordHash = "hashed_secret",
    firstName = Some("Test"),
    lastName = None,
    email = "test@example.com",
    customerDataSheetUrl = None
  )

  // 2. Mock UserService (We only implement login for this test)
  val mockUserService = new UserService {
    def register(email: String, pass: String, fn: Option[String], ln: Option[String]): IO[Either[UserError, User]] = ???
    
    def login(email: String, pass: String): IO[Either[UserError, User]] = 
      if (email == testUser.email && pass == "password") IO.pure(Right(testUser))
      else IO.pure(Left(UserError.InvalidCredentials))
      
    def update(user: User): IO[Either[UserError, User]] = ???
    def delete(id: UUID): IO[Unit] = ???
  }

  // 3. Real JwtService (Logic is pure)
  val jwtService = new JwtService("test-secret-key-for-unit-tests")

  // 4. Build the endpoints to test
  val userEndpoints = Endpoints.makeUserEndpoints(mockUserService, jwtService)

  it should "login successfully and return a token" in {
    // given: A backend stub serving our user endpoints
    val backendStub = TapirStubInterpreter(SttpBackendStub(new CatsMonadError[IO]()))
      .whenServerEndpointsRunLogic(userEndpoints)
      .backend()

    val loginReq = Endpoints.LoginRequest("test@example.com", "password")

    // when: We send a POST request
    val responseIO = basicRequest
      .post(uri"http://test.com/users/login")
      .body(loginReq.asJson.noSpaces) // Send JSON body
      .send(backendStub)

    // then: It should succeed
    val response = responseIO.unsafeRunSync()
    response.code shouldBe StatusCode.Ok
    
    // Check that we got a token back
    response.body.isRight shouldBe true
    val jsonBody = response.body.getOrElse("")
    jsonBody should include ("token")
    jsonBody should include (userId.toString)
  }

  it should "return 401 for invalid credentials" in {
    // given
    val backendStub = TapirStubInterpreter(SttpBackendStub(new CatsMonadError[IO]()))
      .whenServerEndpointsRunLogic(userEndpoints)
      .backend()

    val loginReq = Endpoints.LoginRequest("test@example.com", "wrong-password")

    // when
    val response = basicRequest
      .post(uri"http://test.com/users/login")
      .body(loginReq.asJson.noSpaces)
      .send(backendStub)
      .unsafeRunSync()

    // then
    response.code shouldBe StatusCode.Unauthorized
  }
}