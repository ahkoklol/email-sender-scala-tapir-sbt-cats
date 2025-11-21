package com.ahkoklol.config

import cats.effect.*
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*

object Postgres:
  def makeTransactor(
    driver: String = "org.postgresql.Driver",
    url: String = "jdbc:postgresql://localhost:5432/mdistributions",
    user: String = "postgres",
    pass: String = "password"
  ): Resource[IO, Transactor[IO]] =
    HikariTransactor.newHikariTransactor[IO](
      driver, url, user, pass,
      scala.concurrent.ExecutionContext.global
    )