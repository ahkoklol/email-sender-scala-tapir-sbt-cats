package com.ahkoklol.domain

enum UserError extends Exception:
  case EmailAlreadyExists(email: String)
  case UserNotFound
  case InvalidCredentials