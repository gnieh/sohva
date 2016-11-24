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

class CouchException(val status: Int, val detail: Option[ErrorResult])
  extends Exception("status: " + status + "\nbecause: " + detail)

class ConflictException(detail: Option[ErrorResult]) extends CouchException(409, detail)

object CouchException {

  def apply(status: Int, detail: Option[ErrorResult]): CouchException =
    if (status == 409)
      new ConflictException(detail)
    else
      new CouchException(status, detail)

  def unapply(exn: Throwable): Option[(Int, Option[ErrorResult])] = exn match {
    case exn: CouchException => Some(exn.status -> exn.detail)
    case _                   => None
  }

}

object ConflictException {

  def unapply(exn: Throwable): Option[Option[ErrorResult]] = exn match {
    case CouchException(409, detail) => Some(detail)
    case _                           => None
  }

}
