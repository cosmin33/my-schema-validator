package com.valschema.myschemavalidator

import cats.effect.Sync
import cats.implicits._
import com.valschema.myschemavalidator.Validator.SchemaId
import org.http4s._
import io.circe.parser._
import org.http4s.dsl.Http4sDsl

object MyschemavalidatorRoutes {

  def jokeRoutes[F[_]: Sync](J: Jokes[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "joke" =>
        for {
          joke <- J.get
          resp <- Ok(joke)
        } yield resp
    }
  }

  def helloWorldRoutes[F[_]: Sync](H: HelloWorld[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }

  def validatorRoutes[F[_]: Sync](validator: Validator[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "schema" / SchemaId(schemaId) =>
        for {
          schemaOpt <- validator.getSchema(schemaId)
          resp <- schemaOpt.fold(ifEmpty = NotFound(s"Schema ID '${schemaId.id}' not found"))(schema => Ok(schema))
        } yield resp
      case req @ POST -> Root / "schema" / sid =>
        for {
          body <- req.body.map(_.toChar).compile.toList.map(_.mkString)
          schemaOpt = SchemaId(sid)
          resp <-
            schemaOpt.fold(
              ifEmpty = NotAcceptable("Invalid schema ID"))(
              schemaId => Created(validator.addSchema(schemaId, body))
            )
        } yield resp
      case req @ POST -> Root / "validate" / sid =>
        for {
          body <- req.body.map(_.toChar).compile.toList.map(_.mkString)
          j = parse(body)
          schemaOpt = SchemaId(sid)
          resp <- schemaOpt.fold(
            ifEmpty = NotAcceptable("Invalid schema ID")
          )(
            schemaId => j.fold(_ => NotAcceptable("Invalid Json"), json => Ok(validator.validateJson(schemaId, json)))
          )
        } yield resp


//      case req @ POST -> Root / "validate" / SchemaId(schemaId) =>
//        for {
//          body <- req.body.map(_.toChar).compile.toList.map(_.mkString)
//          j = parse(body)
//          resp <- j.fold(_ => NotAcceptable("Invalid Json"), json => Ok(validator.validateJson(schemaId, json)))
//        } yield resp
    }
  }

}