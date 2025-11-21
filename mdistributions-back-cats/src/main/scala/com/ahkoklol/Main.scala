package com.ahkoklol

import cats.effect.{ExitCode, IO, IOApp}
import com.ahkoklol.config.AppConfig
import com.ahkoklol.config.Postgres
import com.ahkoklol.integrations.{GoogleSheets, SmtpMailer}
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
      
      for {
        // --- Integrations & Auth ---
        sheets <- GoogleSheets.make("credentials.json")
        
        // FIXED: Split into separate lines and used '='
        mailer = SmtpMailer.make(config.smtp)
        jwtService = new JwtService(config.jwtSecret)

        // --- Repositories ---
        userRepo  = UserRepository.make(xa)
        emailRepo = EmailRepository.make(xa)

        // --- Services ---
        userService  = UserService.make(userRepo)
        emailService = EmailService.make(emailRepo)

        // --- Background Worker ---
        workerFiber <- EmailWorker.start(emailRepo, userRepo, sheets, mailer).start

        // --- HTTP Endpoints ---
        userEndpoints  = Endpoints.makeUserEndpoints(userService, jwtService)
        emailEndpoints = Endpoints.makeEmailEndpoints(emailService, jwtService)
        docEndpoints   = Endpoints.makeDocEndpoints(userEndpoints ++ emailEndpoints)
        
        allEndpoints   = userEndpoints ++ emailEndpoints ++ docEndpoints

        // --- Convert Tapir Endpoints to http4s Routes ---
        routes = Http4sServerInterpreter[IO]().toRoutes(allEndpoints)

        // --- Determine Port ---
        httpPort = sys.env.get("HTTP_PORT")
          .flatMap(_.toIntOption)
          .flatMap(Port.fromInt)
          .getOrElse(port"8080")

        // --- Start Server ---
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
        
        // --- Cleanup ---
        _ <- workerFiber.cancel 
        
      } yield ExitCode.Success
    }