package com.ahkoklol.domain

import io.circe.generic.auto.*

case class ApiError(error: String)

object ApiError:
  def fromUserError(e: UserError): (sttp.model.StatusCode, ApiError) = e match
    case UserError.EmailAlreadyExists(email) => (sttp.model.StatusCode.Conflict, ApiError(s"Email $email already exists"))
    case UserError.UserNotFound              => (sttp.model.StatusCode.NotFound, ApiError("User not found"))
    case UserError.InvalidCredentials        => (sttp.model.StatusCode.Unauthorized, ApiError("Invalid credentials"))

  def fromEmailError(e: EmailError): (sttp.model.StatusCode, ApiError) = e match
    case EmailError.EmailNotFound => (sttp.model.StatusCode.NotFound, ApiError("Email not found"))
    case EmailError.AccessDenied  => (sttp.model.StatusCode.Forbidden, ApiError("Access denied"))