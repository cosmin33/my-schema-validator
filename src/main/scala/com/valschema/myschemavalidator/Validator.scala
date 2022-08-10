package com.valschema.myschemavalidator

import cats.effect.Sync
import cats.{Inject, Monad}
import cats.implicits._
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.valschema.myschemavalidator.Validator.{SchemaId, Validity, jsonInvalid, jsonValid, schemaInvalid, schemaValid}
import com.valschema.myschemavalidator.algebras.KVStore
import io.circe._
import org.http4s.EntityEncoder
import org.http4s.circe._
import io.circe.parser._

import scala.util.matching.Regex

case class Validator[F[_]](kvs: KVStore[F, SchemaId, String]) {

  def addSchema(id: SchemaId, content: String)(implicit F: Monad[F]): F[Validity] =
    parse(content).fold(
      _ => schemaInvalid(id).pure[F],
      _ => kvs.put(id, content).as(schemaValid(id))
    )

  def getSchema(id: SchemaId): F[Option[String]] = kvs.get(id)

  def validateJson(id: SchemaId, json: Json)(implicit F: Sync[F]): F[Validity] =
    for {
      ostr <- kvs.get(id)
      osch <- ostr.traverse(stringToJsonNode)
      j <- stringToJsonNode(json.deepDropNullValues.noSpaces)
      rep = osch.map(s => rawValidate(s, j))
      v = rep.map(r => if (r.isSuccess) jsonValid(id) else jsonInvalid(id, r.toString))
    } yield v.getOrElse(jsonInvalid(id, s"Can't find schema with id '${id.id}'"))


  private def stringToJsonNode(s: String)(implicit F: Sync[F]): F[JsonNode] =
    Sync[F].delay {
      val om = new ObjectMapper()
      om.readTree(s)
    }

  private def rawValidate(schema: JsonNode, json: JsonNode): ProcessingReport = {
    val factory = JsonSchemaFactory.byDefault()
    val s = factory.getJsonSchema(schema)
    val report = s.validate(json)
    report
  }

}

object Validator {

  case class SchemaId(id: String)
  object SchemaId {
    def apply(id: String): Option[SchemaId] = new Regex("^([-[a-z][A-Z][0-9]]+)$").findFirstIn(id).map(new SchemaId(_))
    def unapply(id: String): Option[SchemaId] = apply(id)
  }

  val injectSchemaToFilename: Inject[SchemaId, String] = new Inject[SchemaId, String] {
    def inj = schema => s"SCHEMA-${schema.id}"
    def prj = string => new Regex("^SCHEMA-(.+)$").findFirstIn(string).flatMap(SchemaId(_))
  }

  sealed trait Validity
  case class IsValid(action: String, id: SchemaId, status: String) extends Validity
  case class IsInvalid(action: String, id: SchemaId, status: String, message: String) extends Validity

  implicit val validityEncoder: Encoder[Validity] = {
    case IsValid(a, id, s) => Json.obj (
      "action" -> Json.fromString(a),
      "id" -> Json.fromString(id.id),
      "status" -> Json.fromString(s)
    )
    case IsInvalid(a, id, s, m) => Json.obj (
      "action" -> Json.fromString(a),
      "id" -> Json.fromString(id.id),
      "status" -> Json.fromString(s),
      "message" -> Json.fromString(m)
    )
  }

  implicit def validityEntityEncoder[F[_]]: EntityEncoder[F, Validity] = jsonEncoderOf

  def schemaValid(id: SchemaId): Validity = IsValid("uploadSchema", id, "success")
  def schemaInvalid(id: SchemaId): Validity = IsInvalid("uploadSchema", id, "error", "Invalid JSON")
  def jsonValid(id: SchemaId): Validity = IsValid("validateDocument", id, "success")
  def jsonInvalid(id: SchemaId, message: String): Validity = IsInvalid("validateDocument", id, "error", message)

}