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

import javax.crypto
import java.nio.charset.Charset
import akka.http.scaladsl.model.{
  HttpEntity,
  MediaTypes,
  ContentType,
  HttpCharsets,
  HttpRequest
}
import akka.http.scaladsl.model.headers.RawHeader
import akka.parboiled2.util.Base64
import scala.collection.immutable.TreeMap
import java.net.URLEncoder

object OAuth {

  def oAuthAuthorizer(consumerKey: String, consumerSecret: String, token: String, secret: String): HttpRequest => HttpRequest = {
    // construct the key and cryptographic entity
    val SHA1 = "HmacSHA1"
    val keyString = percentEncode(List(consumerSecret, secret))
    val key = new crypto.spec.SecretKeySpec(bytes(keyString), SHA1)
    val mac = crypto.Mac.getInstance(SHA1)

    { httpRequest: HttpRequest =>
      val timestamp = (System.currentTimeMillis / 1000).toString
      // nonce is unique enough for our purposes here
      val nonce = System.nanoTime.toString

      // pick out x-www-form-urlencoded body
      val (requestParams, newEntity) = httpRequest.entity match {
        case HttpEntity.Strict(ContentType(MediaTypes.`application/x-www-form-urlencoded`, cs), data) =>
          val charset = cs.getOrElse(HttpCharsets.`UTF-8`)
          val params = data.decodeString(charset.value).split("&")
          val pairs = params.map { param =>
            val p = param.split("=")
            p(0) -> percentEncode(p(1))
          }
          (pairs.toMap, HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`, charset), "%s=%s" format (pairs(0)._1, pairs(0)._2)))
        case e => (Map[String, String](), e)
      }

      // prepare the OAuth parameters
      val oauthParams = Map("oauth_consumer_key" -> consumerKey,
        "oauth_signature_method" -> "HMAC-SHA1",
        "oauth_timestamp" -> timestamp,
        "oauth_nonce" -> nonce,
        "oauth_token" -> token,
        "oauth_version" -> "1.0"
      )

      // construct parts of the signature base string
      val encodedOrderedParams = (TreeMap[String, String]() ++ oauthParams ++ requestParams) map { case (k, v) => k + "=" + v } mkString "&"
      val url = httpRequest.uri.toString()
      // construct the signature base string
      val signatureBaseString = percentEncode(List(httpRequest.method.value, url, encodedOrderedParams))

      mac.init(key)
      val sig = Base64.rfc2045().encodeToString(mac.doFinal(bytes(signatureBaseString)), false)
      mac.reset()

      val oauth = TreeMap[String, String]() ++ (oauthParams + ("oauth_signature" -> percentEncode(sig))).map { case (k, v) => "%s=\"%s\"" format (k, v) } mkString ", "

      // return the signed request
      httpRequest.withHeaders(List(RawHeader("Authorization", "OAuth " + oauth))).withEntity(newEntity)
    }
  }

  private def percentEncode(str: String): String = URLEncoder.encode(str, "UTF-8").replace("+", "%20").replace("%7E", "~")
  private def percentEncode(s: Seq[String]): String = s.map(percentEncode).mkString("&")
  private def bytes(str: String) = str.getBytes(Charset.forName("UTF-8"))

}

