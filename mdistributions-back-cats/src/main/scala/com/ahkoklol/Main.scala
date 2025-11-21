package com.ahkoklol

import cats.effect.{ExitCode, IO, IOApp}
import com.ahkoklol.config.AppConfig
import com.ahkoklol.config.Postgres
import com.ahkoklol.integrations.SmtpMailer
import com.ahkoklol.repositories.{EmailRepository, UserRepository}
import com.ahkoklol.services.{EmailService, JwtService, UserService}
import com.ahkoklol.workers.EmailWorker
import com.comcast.ip4s.{Host, Port, port}
import org.flywaydb.core.Flyway
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import sttp.tapir.server.http4s.Http4sServerInterpreter

object Main extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    val config = AppConfig.load
    
    // --- 2. Run Database Migrations (Flyway) ---
    val flyway = Flyway.configure()
      .dataSource(
        "jdbc:postgresql://localhost:5433/mdistributions", 
        "postgres", 
        "password"
      )
      .baselineOnMigrate(true)
      .load()
    flyway.migrate()

    // 3. Connect to Database & Start App
    Postgres.makeTransactor(
      url = "jdbc:postgresql://localhost:5433/mdistributions",
      user = "postgres",
      pass = "password"
    ).use { xa =>
      
      // --- INITIALIZATION (Pure Code - No IO) ---
      // Move these OUTSIDE the for-comprehension
      val mailer     = SmtpMailer.make(config.smtp)
      val jwtService = new JwtService(config.jwtSecret)

      val userRepo   = UserRepository.make(xa)
      val emailRepo  = EmailRepository.make(xa)

      val userService  = UserService.make(userRepo)
      val emailService = EmailService.make(emailRepo)

      val userEndpoints  = Endpoints.makeUserEndpoints(userService, jwtService)
      val emailEndpoints = Endpoints.makeEmailEndpoints(emailService, jwtService)
      val docEndpoints   = Endpoints.makeDocEndpoints(userEndpoints ++ emailEndpoints)
      
      val allEndpoints   = userEndpoints ++ emailEndpoints ++ docEndpoints

      val routes = Http4sServerInterpreter[IO]().toRoutes(allEndpoints)

      val httpPort = sys.env.get("HTTP_PORT")
        .flatMap(_.toIntOption)
        .flatMap(Port.fromInt)
        .getOrElse(port"8080")

      // --- EXECUTION (Effectful Code - IO) ---
      // The for-loop handles the sequence of IO actions
      for {
        // 1. Start the Background Worker
        workerFiber <- EmailWorker.start(emailRepo, mailer).start

        // 2. Start the HTTP Server
        _ <- IO.println("Worker started in background...")
        _ <- EmberServerBuilder
          .default[IO]
          .withHost(Host.fromString("localhost").get)
          .withPort(httpPort)
          .withHttpApp(Router("/" -> routes).orNotFound)
          .build
          .use { server =>
            for {
              _ <- IO.println(s"Server started at http://localhost:${server.address.getPort}/docs")
              _ <- IO.println("Press ENTER key to stop...")
              _ <- IO.readLine
            } yield ()
          }
        
        // 3. Cleanup when server stops
        _ <- workerFiber.cancel 
        
      } yield ExitCode.Success
    }