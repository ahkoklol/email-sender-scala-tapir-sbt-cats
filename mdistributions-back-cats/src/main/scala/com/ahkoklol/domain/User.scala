package com.ahkoklol.domain

import java.util.UUID
import java.time.Instant

case class User(
    id: UUID,
    passwordHash: String, 
    firstName: Option[String],
    lastName: Option[String],
    email: String,
    customerDataSheetUrl: Option[String], 
)