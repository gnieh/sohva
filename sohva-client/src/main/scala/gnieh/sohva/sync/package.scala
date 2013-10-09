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

import scala.concurrent._
import duration._

package object sync {

  private[sync] def synced[T](result: async.AsyncResult[T]): T = Await.result(result, Duration.Inf) match {
    case Right(t) => t
    case Left((409, error)) =>
      throw new ConflictException(error)
    case Left((code, error)) =>
      throw new CouchException(code, error)
  }

}
