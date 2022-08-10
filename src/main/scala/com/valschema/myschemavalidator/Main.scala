package com.valschema.myschemavalidator

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    MyschemavalidatorServer.stream[IO].compile.drain.as(ExitCode.Success)
}
