package com.ahkoklol.domain

import java.util.UUID
import java.time.Instant

enum EmailStatus:
  case Draft, Queued, Sent, Failed

case class Email(
    id: UUID,
    userId: UUID,
    subject: String,
    body: String,
    createdAt: Instant,
    sentAt: Option[Instant],
    errorMessage: Option[String]
)