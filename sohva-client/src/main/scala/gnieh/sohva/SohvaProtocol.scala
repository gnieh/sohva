/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva

import spray.json._

import java.util.Date
import java.net.URL
import java.text.SimpleDateFormat

import scala.util.Try

import mango.MangoProtocol

case class SohvaJsonException(msg: String, inner: Exception) extends Exception(msg, inner)

trait SohvaProtocol extends DefaultJsonProtocol with MangoProtocol with CouchFormatImpl {

  implicit val docUpdateFormat = jsonFormat3(DocUpdate)

  implicit val sizesFormat = jsonFormat3(Sizes)

  implicit val infoResultFormat = jsonFormat12(InfoResult)

  implicit val bulkSaveFormat = jsonFormat2(BulkSave)

  implicit val securityListFormat = jsonFormat2(SecurityList)

  implicit val securityDocFormat = jsonFormat2(SecurityDoc)

  implicit val viewDocFormat = jsonFormat2(ViewDoc)

  implicit val rewriteRuleFormat = jsonFormat4(RewriteRule)

  implicit val attachmentFormat = jsonFormat7(Attachment)

  implicit val designDocFormat = couchFormat[DesignDoc]

  implicit object DateFormat extends JsonFormat[Date] {

    val f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS")

    def read(value: JsValue): Date = value match {
      case JsString(str) =>
        Try(f.parse(str)).getOrElse(deserializationError(s"Malformed date: ${value.prettyPrint}"))
      case _ =>
        deserializationError(s"Malformed date: ${value.prettyPrint}")
    }

    def write(date: Date): JsValue =
      JsString(f.format(date))

  }

  implicit val okResultFormat = jsonFormat3(OkResult)

  implicit val errorResultFormat = jsonFormat3(ErrorResult)

  implicit val couchInfoFormat = jsonFormat2(CouchInfo)

  private[sohva] implicit val uuidsFormat = jsonFormat1(Uuids)

  implicit object DbResultFormat extends RootJsonFormat[DbResult] {

    def read(value: JsValue): DbResult =
      value match {
        case JsObject(fields) =>
          fields.get("ok") match {
            case Some(JsBoolean(_)) => value.convertTo[OkResult]
            case None               => value.convertTo[ErrorResult]
            case _                  => deserializationError(f"database result expected but got $value")
          }
        case _ =>
          deserializationError(f"database result expected but got $value")
      }

    def write(result: DbResult): JsValue = result match {
      case ok: OkResult       => ok.toJson
      case error: ErrorResult => error.toJson
    }

  }

  implicit val oauthDataFormat = jsonFormat2(OAuthData)

  implicit val userInfoFormat = jsonFormat2(UserInfo)

  implicit object CouchUserFormat extends CouchFormat[CouchUser] {

    def read(value: JsValue): CouchUser = value match {
      case JsObject(fields) =>
        val user = for {
          _id <- fields.get("_id").map(_.convertTo[String])
          _rev <- fields.get("_rev").map(_.convertTo[String])
          name <- fields.get("name").map(_.convertTo[String])
          roles <- fields.get("roles").map(_.convertTo[List[String]])
          oauth = fields.get("oauth").map(_.convertTo[OAuthData])
        } yield CouchUser(name, "???", roles, oauth)

        user.getOrElse(deserializationError(s"Malformed user object: ${value.prettyPrint}"))

      case json =>
        deserializationError(s"Malformed user object: ${json.prettyPrint}")
    }

    def write(user: CouchUser): JsObject = {
      val fields1 = Map(
        "_id" -> JsString(user._id),
        "name" -> JsString(user.name),
        "type" -> JsString("user"),
        "roles" -> JsArray(user.roles.map(JsString(_)): _*),
        "password" -> JsString(user.password))
      val fields2 = user._rev match {
        case Some(r) => fields1 + ("_rev" -> JsString(r))
        case None    => fields1
      }
      val fields3 = user.oauth match {
        case Some(oauth) => fields2 + ("oauth" -> oauth.toJson)
        case None        => fields2
      }
      JsObject(fields3)
    }

    def _id(t: CouchUser): String =
      t._id

    def _rev(t: CouchUser): Option[String] =
      t._rev

    def withRev(t: CouchUser, rev: Option[String]): CouchUser =
      t.withRev(rev)

  }

  implicit val revFormat = jsonFormat1(Rev)

  implicit object ChangeFormat extends RootJsonFormat[Change] {

    def read(value: JsValue): Change = value match {
      case JsObject(fields) =>
        val c = for {
          seq <- fields.get("seq")
          id <- fields.get("id").map(_.convertTo[String])
          changes <- Some(fields("changes").convertTo[Vector[Rev]])
          deleted = fields.get("deleted").map(_.convertTo[Boolean]).getOrElse(false)
          doc = if (deleted) None else fields.get("doc").collect { case o: JsObject => o }
        } yield new Change(seq, id, changes, deleted, doc)

        c.getOrElse(deserializationError(s"Malformed change object: ${value.prettyPrint}"))
      case _ =>
        deserializationError(s"Malformed change object: ${value.prettyPrint}")
    }

    def write(change: Change): JsValue = change match {
      case Change(seq, id, changes, deleted, None) =>
        JsObject(Map("seq" -> seq, "id" -> id.toJson, "changes" -> changes.toJson, "deleted" -> deleted.toJson))
      case Change(seq, id, changes, deleted, Some(doc)) =>
        JsObject(Map("seq" -> seq, "id" -> id.toJson, "changes" -> changes.toJson, "deleted" -> deleted.toJson, "doc" -> doc))
    }

  }

  implicit val changesFormat = jsonFormat3(Changes)

  /** (De)Serialize a database reference (remote or local).
   *
   *  @author Lucas Satabin
   */
  implicit object DbRefFormat extends JsonFormat[DbRef] {

    def read(value: JsValue): DbRef = value match {
      case JsString(url(u)) =>
        RemoteDb(u)
      case JsString(name) =>
        LocalDb(name)
    }

    def write(ref: DbRef): JsString = ref match {
      case LocalDb(name) =>
        JsString(name)
      case RemoteDb(url) =>
        JsString(url.toString)
    }

    object url {
      def unapply(s: String): Option[URL] = try {
        Some(new URL(s))
      } catch {
        case _: Exception =>
          None
      }
    }

  }

  implicit val userCtxFormat = jsonFormat2(UserCtx)

  implicit val authInfoFormat = jsonFormat3(AuthInfo)

  implicit val authResultFormat = jsonFormat3(AuthResult)

  implicit val replicationFormat = couchFormat[Replication]

  implicit val membershipFormat = jsonFormat2(Membership)

  implicit object revDiffFormat extends JsonFormat[RevDiff] {

    def read(value: JsValue): RevDiff = value match {
      case JsObject(fields) =>
        val missing = fields.getOrElse("missing", deserializationError(f"expected a rev diff object but got $value")).convertTo[Vector[String]]
        val ancestors = fields.getOrElse("possible_ancestors", JsArray()).convertTo[Vector[String]]
        RevDiff(missing, ancestors)
      case _ =>
        deserializationError(f"expected a rev diff object but got $value")
    }

    def write(rd: RevDiff): JsObject =
      JsObject(Map("missing" -> rd.missing.toJson, "possible_ancestors" -> rd.possible_ancestors.toJson))

  }

  implicit object ConfigurationFormat extends RootJsonFormat[Configuration] {

    def read(value: JsValue): Configuration =
      Configuration(value.convertTo[Map[String, Map[String, String]]].withDefaultValue(Map()))

    def write(conf: Configuration): JsValue =
      conf.sections.toJson

  }

  implicit val rawRowFormat = jsonFormat4(RawRow)

  implicit object RawViewResultFormat extends RootJsonReader[RawViewResult] {

    def read(value: JsValue): RawViewResult = value match {
      case JsObject(fields) =>
        val offset = fields.get("offset").flatMap(o => Try(o.convertTo[Long]).toOption).getOrElse(0l)
        val rows = fields.get("rows") match {
          case Some(JsArray(rows)) =>
            for (row <- rows.toList)
              yield row.convertTo[RawRow]
          case _ =>
            Nil
        }
        val total_rows = fields.get("total_rows").map(_.convertTo[Long]).getOrElse(rows.size.toLong)
        val update_seq = fields.get("update_seq").map(_.convertTo[Long])
        RawViewResult(total_rows, offset, rows, update_seq)
      case _ =>
        deserializationError(s"Malformed view result object: ${value.prettyPrint}")
    }

  }

}

object SohvaProtocol extends SohvaProtocol
