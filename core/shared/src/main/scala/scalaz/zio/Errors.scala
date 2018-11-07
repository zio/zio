// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio
import scalaz.zio.ExitResult.Cause

object Errors {

  final case class FiberFailure(cause: Cause[Any]) extends Throwable {
    override def getMessage: String = message(cause)

    def message(cause: Cause[Any]): String = cause match {
      case Cause.Checked(error)    => "An error was not handled by a fiber: " + error.toString
      case Cause.Unchecked(_)      => "The fiber was terminated by a defect"
      case Cause.Interruption(_)   => "The fiber was terminated by an interruption"
      case Cause.Then(left, right) => "Both fibers terminated in sequence: \n" + message(left) + "\n" + message(right)
      case Cause.Both(left, right) => "Both fibers terminated in parallel: \n" + message(left) + "\n" + message(right)
    }
  }
}
