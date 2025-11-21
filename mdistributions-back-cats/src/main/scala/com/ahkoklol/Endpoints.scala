package com.ahkoklol

import cats.effect.IO
import com.ahkoklol.domain.*
import com.ahkoklol.services.{EmailService, JwtService, UserService}
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import java.util.UUID

object Endpoints:

  // --- DTOs (Data Transfer Objects) ---
  case class RegisterRequest(email: String, password: String, firstName: Option[String], lastName: Option[String])
  case class LoginRequest(email: String, password: String)
  case class CreateEmailRequest(subject: String, body: String)
  case class LoginResponse(token: String, user: User)

  // --- Base Endpoints ---

  val publicEndpoint = endpoint.errorOut(jsonBody[ApiError])

  // FIXED: Removed explicit type annotation ": Endpoint[...]"
  // serverSecurityLogic returns a PartialServerEndpoint, which Scala will now infer correctly.
  def secureEndpoint(jwtService: JwtService) = 
    publicEndpoint
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode(sttp.model.StatusCode.Unauthorized))
      .serverSecurityLogic { token =>
        IO.pure(
          jwtService.validateToken(token)
            .left.map(err => ApiError(err))
        )
      }

  // --- User Endpoints ---

  def makeUserEndpoints(userService: UserService, jwtService: JwtService): List[ServerEndpoint[Any, IO]] = List(
    
    // POST /users/register
    publicEndpoint.post.in("users" / "register")
      .in(jsonBody[RegisterRequest])
      .out(jsonBody[User])
      .out(statusCode(sttp.model.StatusCode.Created))
      .serverLogic { req =>
        userService.register(req.email, req.password, req.firstName, req.lastName)
          .map(_.left.map(ApiError.fromUserError(_)._2))
      },

    // POST /users/login
    publicEndpoint.post.in("users" / "login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[LoginResponse])
      .serverLogic { req =>
        userService.login(req.email, req.password).map {
          case Right(user) =>
            val token = jwtService.generateToken(user.id)
            Right(LoginResponse(token, user))
          case Left(e) =>
            Left(ApiError.fromUserError(e)._2)
        }
      }
  )

  // --- Email Endpoints (Secured) ---

  def makeEmailEndpoints(emailService: EmailService, jwtService: JwtService): List[ServerEndpoint[Any, IO]] = List(
    
    // POST /emails
    secureEndpoint(jwtService).post.in("emails")
      .in(jsonBody[CreateEmailRequest])
      .out(jsonBody[Email])
      .serverLogic { userId => req =>
        emailService.create(userId, req.subject, req.body).map(Right(_))
      },

    // GET /emails
    secureEndpoint(jwtService).get.in("emails")
      .out(jsonBody[List[Email]])
      .serverLogic { userId => _ =>
        emailService.findAll(userId).map(Right(_))
      },

    // GET /emails/{id}
    secureEndpoint(jwtService).get.in("emails" / path[UUID]("emailId"))
      .out(jsonBody[Email])
      .serverLogic { userId => emailId =>
        emailService.find(userId, emailId).map(_.left.map(ApiError.fromEmailError(_)._2))
      },

    // DELETE /emails/{id}
    secureEndpoint(jwtService).delete.in("emails" / path[UUID]("emailId"))
      .out(statusCode(sttp.model.StatusCode.NoContent))
      .serverLogic { userId => emailId =>
        emailService.delete(userId, emailId).map(_.left.map(ApiError.fromEmailError(_)._2))
      }
  )

  def makeDocEndpoints(apiEndpoints: List[ServerEndpoint[Any, IO]]): List[ServerEndpoint[Any, IO]] = 
    SwaggerInterpreter().fromServerEndpoints[IO](apiEndpoints, "Marketing Email Sender", "1.0.0")