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
import sttp.model.Part
import sttp.tapir.TapirFile
import sttp.model.StatusCode

object Endpoints:

  // --- DTOs ---
  case class RegisterRequest(email: String, password: String, firstName: Option[String], lastName: Option[String])
  case class LoginRequest(email: String, password: String)
  case class LoginResponse(token: String, user: User)
  case class CreateEmailForm(subject: String, body: String, file: Part[TapirFile])

  // --- Base Endpoints ---

  // 1. Update: map error to (StatusCode, ApiError)
  val publicEndpoint = endpoint.errorOut(statusCode.and(jsonBody[ApiError]))

  def secureEndpoint(jwtService: JwtService) = 
    publicEndpoint
      .securityIn(auth.bearer[String]())
      // 2. Update: Remove the explicit .errorOut(statusCode(Unauthorized)) 
      //    because publicEndpoint now handles dynamic status codes.
      .serverSecurityLogic { token =>
        IO.pure(
          jwtService.validateToken(token)
            // 3. Update: Return the tuple (StatusCode, ApiError)
            .left.map(err => (StatusCode.Unauthorized, ApiError(err)))
        )
      }

  // --- User Endpoints ---

  def makeUserEndpoints(userService: UserService, jwtService: JwtService): List[ServerEndpoint[Any, IO]] = List(
    
    publicEndpoint.post.in("users" / "register")
      .in(jsonBody[RegisterRequest])
      .out(jsonBody[User])
      .out(statusCode(StatusCode.Created))
      .serverLogic { req =>
        userService.register(req.email, req.password, req.firstName, req.lastName)
          // 4. Update: Remove ._2 to return the full (StatusCode, ApiError) pair
          .map(_.left.map(e => ApiError.fromUserError(e)))
      },

    publicEndpoint.post.in("users" / "login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[LoginResponse])
      .serverLogic { req =>
        userService.login(req.email, req.password).map {
          case Right(user) =>
            val token = jwtService.generateToken(user.id)
            Right(LoginResponse(token, user))
          case Left(e) =>
            // 5. Update: Return the full (StatusCode, ApiError) pair
            Left(ApiError.fromUserError(e))
        }
      }
  )

  // --- Email Endpoints (Secured) ---

  def makeEmailEndpoints(emailService: EmailService, jwtService: JwtService): List[ServerEndpoint[Any, IO]] = List(
    
    secureEndpoint(jwtService).post.in("emails")
      .in(multipartBody[CreateEmailForm])
      .out(jsonBody[Email])
      .serverLogic { userId => form =>
        val filename = form.file.fileName.getOrElse("unknown.xlsx")
        emailService.create(userId, form.subject, form.body, form.file.body, filename)
          .map(Right(_))
      },

    secureEndpoint(jwtService).get.in("emails")
      .out(jsonBody[List[Email]])
      .serverLogic { userId => _ =>
        emailService.findAll(userId).map(Right(_))
      },

    secureEndpoint(jwtService).get.in("emails" / path[UUID]("emailId"))
      .out(jsonBody[Email])
      .serverLogic { userId => emailId =>
        // 6. Update: Remove ._2
        emailService.find(userId, emailId).map(_.left.map(e => ApiError.fromEmailError(e)))
      },

    secureEndpoint(jwtService).delete.in("emails" / path[UUID]("emailId"))
      .out(statusCode(StatusCode.NoContent))
      .serverLogic { userId => emailId =>
        // 7. Update: Remove ._2
        emailService.delete(userId, emailId).map(_.left.map(e => ApiError.fromEmailError(e)))
      }
  )

  def makeDocEndpoints(apiEndpoints: List[ServerEndpoint[Any, IO]]): List[ServerEndpoint[Any, IO]] = 
    SwaggerInterpreter().fromServerEndpoints[IO](apiEndpoints, "Marketing Email Sender", "1.0.0")