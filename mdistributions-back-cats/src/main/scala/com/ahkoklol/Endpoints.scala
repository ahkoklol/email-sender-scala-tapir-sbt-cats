package com.ahkoklol

import sttp.tapir.*

import cats.effect.IO
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object Endpoints:
  case class User(name: String) extends AnyVal
  val helloEndpoint: PublicEndpoint[User, Unit, String, Any] = endpoint.get
    .in("hello")
    .in(query[User]("name"))
    .out(stringBody)
  val helloServerEndpoint: ServerEndpoint[Any, IO] = helloEndpoint.serverLogicSuccess(user => IO.pure(s"Hello ${user.name}"))

  val apiEndpoints: List[ServerEndpoint[Any, IO]] = List(helloServerEndpoint)

  val docEndpoints: List[ServerEndpoint[Any, IO]] = SwaggerInterpreter()
    .fromServerEndpoints[IO](apiEndpoints, "mdistributions-back-cats", "1.0.0")

  val all: List[ServerEndpoint[Any, IO]] = apiEndpoints ++ docEndpoints
