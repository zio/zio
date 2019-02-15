package scalaz.zio.internal.impls

import java.util

import scalaz.zio.Exit.Cause
import scalaz.zio.IO
import scalaz.zio.internal.{ Env => IEnv, Executor }

import scala.concurrent.ExecutionContext.Implicits

object Env {

  /**
   * Creates a new default environment.
   */
  final def newDefaultEnv(reportFailure0: Cause[_] => IO[Nothing, _]): IEnv =
    new IEnv {
      val executor = Executor.fromExecutionContext(1024)(Implicits.global)

      def nonFatal(t: Throwable): Boolean =
        !t.isInstanceOf[VirtualMachineError]

      def reportFailure(cause: Cause[_]): IO[Nothing, _] =
        reportFailure0(cause)

      def newWeakHashMap[A, B](): util.Map[A, B] =
        new util.HashMap[A, B]()
    }
}
