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

object Endpoints:

  // --- DTOs ---
  case class RegisterRequest(email: String, password: String, firstName: Option[String], lastName: Option[String])
  case class LoginRequest(email: String, password: String)
  case class LoginResponse(token: String, user: User)
  case class CreateEmailForm(subject: String, body: String, file: Part[TapirFile])

  // --- Base Endpoints ---

  val publicEndpoint = endpoint.errorOut(jsonBody[ApiError])

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
    
    publicEndpoint.post.in("users" / "register")
      .in(jsonBody[RegisterRequest])
      .out(jsonBody[User])
      .out(statusCode(sttp.model.StatusCode.Created))
      .serverLogic { req =>
        userService.register(req.email, req.password, req.firstName, req.lastName)
          .map(_.left.map(e => ApiError.fromUserError(e)._2))
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
            Left(ApiError.fromUserError(e)._2)
        }
      }
  )

  // --- Email Endpoints (Secured) ---

  def makeEmailEndpoints(emailService: EmailService, jwtService: JwtService): List[ServerEndpoint[Any, IO]] = List(
    
    // POST /emails - CHANGED to accept Multipart Form
    secureEndpoint(jwtService).post.in("emails")
      .in(multipartBody[CreateEmailForm]) // <--- Used here
      .out(jsonBody[Email])
      .serverLogic { userId => form =>
        // Extract the file from the form part
        emailService.create(userId, form.subject, form.body, form.file.body).map(Right(_))
      },

    secureEndpoint(jwtService).get.in("emails")
      .out(jsonBody[List[Email]])
      .serverLogic { userId => _ =>
        emailService.findAll(userId).map(Right(_))
      },

    secureEndpoint(jwtService).get.in("emails" / path[UUID]("emailId"))
      .out(jsonBody[Email])
      .serverLogic { userId => emailId =>
        emailService.find(userId, emailId).map(_.left.map(e => ApiError.fromEmailError(e)._2))
      },

    secureEndpoint(jwtService).delete.in("emails" / path[UUID]("emailId"))
      .out(statusCode(sttp.model.StatusCode.NoContent))
      .serverLogic { userId => emailId =>
        emailService.delete(userId, emailId).map(_.left.map(e => ApiError.fromEmailError(e)._2))
      }
  )

  def makeDocEndpoints(apiEndpoints: List[ServerEndpoint[Any, IO]]): List[ServerEndpoint[Any, IO]] = 
    SwaggerInterpreter().fromServerEndpoints[IO](apiEndpoints, "Marketing Email Sender", "1.0.0")