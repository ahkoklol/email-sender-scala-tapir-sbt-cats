package com.ahkoklol.services

import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import java.time.Clock
import java.util.UUID
import scala.util.{Failure, Success, Try}

class JwtService(secretKey: String):
  private val algo = JwtAlgorithm.HS256
  // Use UTC clock for consistent expiration times
  private implicit val clock: Clock = Clock.systemUTC()

  // Generates a token valid for 24 hours
  def generateToken(userId: UUID): String =
    val claim = JwtClaim(
      content = s"""{"userId":"$userId"}""",
      expiration = Some(java.time.Instant.now().plusSeconds(86400).getEpochSecond),
      issuedAt = Some(java.time.Instant.now().getEpochSecond)
    )
    JwtCirce.encode(claim, secretKey, algo)

  // Validates token and extracts UserId
  def validateToken(token: String): Either[String, UUID] =
    JwtCirce.decode(token, secretKey, Seq(algo)) match
      case Success(claim) =>
        io.circe.parser.parse(claim.content).flatMap(_.hcursor.get[UUID]("userId")) match
          case Right(id) => Right(id)
          case Left(_)   => Left("Invalid token content")
      case Failure(_) => Left("Invalid or expired token")