package com.valschema.myschemavalidator

import cats.effect.std.Semaphore
import cats.effect.{Async, Resource, Sync}
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger

import java.io.File
import Validator._
import com.valschema.myschemavalidator.algebras.KVStore

object MyschemavalidatorServer {

  def createFolderOrNop[F[_]: Sync](path: String): F[Unit] =
    Sync[F].defer {
      val dm = new File(path)
      if (!(dm.exists() && dm.isDirectory) && !dm.mkdir())
        Sync[F].raiseError[Unit](new RuntimeException(s"Cannot create folder ($path)"))
      else
        Sync[F].unit
    }

  def createKVStore[F[_]: Async](path: String): F[KVStore[F, SchemaId, String]] =
    Semaphore(1).map(s => FolderKVStore(path).withSemaphore(s).comapKey(injectSchemaToFilename))

  def stream[F[_]: Async]: Stream[F, Nothing] = {
    for {
      _ <- Stream.emit(())
      schemasFolder = "./schemas"
      _ <- Stream.eval(createFolderOrNop(schemasFolder))
      kvs <- Stream.eval(createKVStore(schemasFolder))
      validator <- Stream.emit(Validator(kvs))
      client <- Stream.resource(EmberClientBuilder.default[F].build)
      helloWorldAlg = HelloWorld.impl[F]
      jokeAlg = Jokes.impl[F](client)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract segments not checked
      // in the underlying routes.
      httpApp = (
        MyschemavalidatorRoutes.helloWorldRoutes[F](helloWorldAlg) <+>
        MyschemavalidatorRoutes.jokeRoutes[F](jokeAlg) <+>
          MyschemavalidatorRoutes.validatorRoutes[F](validator)
      ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- Stream.resource(
        EmberServerBuilder.default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(finalHttpApp)
          .build >>
        Resource.eval(Async[F].never)
      )
    } yield exitCode
  }.drain
}
