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

/** Object defining methods for automatic resources management
 *
 *  @author Lucas Satabin
 *
 */
object Arm {

  /** Something that can be closed */
  type Closeable = { def close() }

  /** Alias for PartialFunction */
  type ==>[Param, Ret] = PartialFunction[Param, Ret]

  /** default handler: rethrow the exception */
  implicit val defaultHandler: Throwable ==> Nothing = null

  /** Using environment
   *  Example:
   *  {{{
   *  using(new FileOutputStream("test.txt")) {fos =>
   *    fos.write(...)
   *  } {
   *    case e: FileNotFoundException => ...
   *  }
   *  }}}
   */
  def using[T, U >: Null <: Closeable](cl: => U)(body: U => T)(implicit handler: PartialFunction[Throwable, T]): T = {
    // XXX `truie' is needed because the value of a call-by-name parameter is recomputed at each access
    // so each time we use `cl', we may receive a new instance of U
    // Using a `lazy parameter' (see https://lampsvn.epfl.ch/trac/scala/ticket/240) here is not possible
    // We cannot use `lazy val truie = cl' because we access `truie' in the finally block.
    // If the call to `cl' raises an exception, the same error will be raised in the finally block
    // because `truie' was not initialized, and the initialization will be done again, raising the same
    // exception.
    var truie: U = null
    try {
      truie = cl
      body(truie)
    } catch {
      case t if (handler != null && handler.isDefinedAt(t)) =>
        handler(t)
    } finally {
      if (truie != null)
        truie.close
    }
  }

}