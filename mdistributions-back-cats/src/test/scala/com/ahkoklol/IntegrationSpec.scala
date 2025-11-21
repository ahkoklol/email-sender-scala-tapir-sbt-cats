package com.ahkoklol

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName
import scala.concurrent.ExecutionContext

trait IntegrationSpec extends AnyFlatSpec 
  with Matchers 
  with BeforeAndAfterAll 
  with ForAllTestContainer {

  override val container: PostgreSQLContainer = PostgreSQLContainer(
    dockerImageNameOverride = DockerImageName.parse("postgres:15")
  )

  val ddl: ConnectionIO[Unit] = 
    for {
      
      _ <- sql"""
        CREATE TABLE users (
            id UUID PRIMARY KEY,
            password_hash VARCHAR(255) NOT NULL,
            first_name VARCHAR(100),
            last_name VARCHAR(100),
            email VARCHAR(255) NOT NULL UNIQUE,
            customer_data_sheet_url TEXT
        )
      """.update.run
      
      _ <- sql"""
        CREATE TABLE emails (
            id UUID PRIMARY KEY,
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            subject VARCHAR(255) NOT NULL,
            body TEXT NOT NULL,
            recipients TEXT[] NOT NULL,
            created_at TIMESTAMPTZ NOT NULL,
            sent_at TIMESTAMPTZ,
            error_message TEXT
        )
      """.update.run
    } yield ()

  private var closeTransactor: IO[Unit] = IO.unit

  lazy val xa: Transactor[IO] = {
    val (transactor, cleanup) = HikariTransactor.newHikariTransactor[IO](
      container.driverClassName,
      container.jdbcUrl,
      container.username,
      container.password,
      ExecutionContext.global
    ).allocated.unsafeRunSync()
    
    closeTransactor = cleanup

    (sql"DROP TABLE IF EXISTS emails CASCADE".update.run *>
     sql"DROP TABLE IF EXISTS users CASCADE".update.run *>
     ddl).transact(transactor).unsafeRunSync()

    transactor
  }

  override def afterAll(): Unit = {
    closeTransactor.unsafeRunSync() 
    super.afterAll()                
  }
  
  extension [A](io: IO[A]) def unwrap: A = io.unsafeRunSync()
}