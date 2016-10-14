package gnieh.sohva.test

import scala.reflect.ClassTag

/**
 * @author Matthew Pocock
 */
object CauseMatchers {

  //  def checkExpectedExceptionRecursively[T](f: => Any, clazz: Class[T], exceptionExpectedResourceName: String, stackDepth: Int): T = {
  //     val caught = try {
  //       f
  //       None
  //     }
  //     catch {
  //       case u: Throwable => {
  //
  //         def check(v: Throwable): Option[T] = {
  //           if(clazz.isAssignableFrom(v.getClass)) {
  //             Some(clazz.cast(v))
  //           } else if(v.getCause == null) {
  //             None
  //           } else check(v.getCause)
  //         }
  //
  //         check(u)
  //       }
  //     }
  //     caught match {
  //       case None =>
  //         val message = Resources(exceptionExpectedResourceName, clazz.getName)
  //         throw newAssertionFailedException(Some(message), None, stackDepth)
  //       case Some(e) => e
  //     }
  //   }

  def findExpectedExceptionRecursively[T](e: Throwable, clazz: Class[T]): Option[T] = {
    def check(v: Throwable): Option[T] = {
      if (clazz.isAssignableFrom(v.getClass)) {
        Some(clazz.cast(v))
      } else if (v.getCause == null) {
        None
      } else {
        check(v.getCause)
      }
    }

    check(e)
  }

  def findExpectedExceptionRecursively[T: ClassTag](e: Throwable): Option[T] = {
    val clazz = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    findExpectedExceptionRecursively(e, clazz)
  }
}
