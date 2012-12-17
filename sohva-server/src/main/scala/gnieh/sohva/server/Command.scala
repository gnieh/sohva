package gnieh.sohva
package server

import net.liftweb.json._

object NewDDoc {
  def unapply(raw_cmd: List[JValue]): Option[(String, String, DDoc)] = raw_cmd match {
    case List(JString("ddoc"), JString(action), JString(name), ddoc) =>
      ddoc.extractOpt[DDoc] match {
        case Some(ddoc) => Some((action, name, ddoc))
        case None       => None
      }
    case _ => None
  }
}

object UpdateDDoc {
  def unapply(raw_cmd: List[JValue]): Option[(String, String, String, DDoc)] = raw_cmd match {
    case List(JString(id), JString(action), JString(name), ddoc) =>
      ddoc.extractOpt[DDoc] match {
        case Some(ddoc) => Some((id, action, name, ddoc))
        case None       => None
      }
    case _ => None
  }
}

object Reset {

  def unapply(raw_cmd: List[JValue]): Option[Map[String, Any]] = raw_cmd match {
    case List(JString("reset"))         => Some(Map())
    case List(JString("reset"), config) => Some(Extraction.extract[Map[String, Any]](config)) // TODO improve this
    case _                              => None
  }

}

case class AddFun()
case class AddLib()