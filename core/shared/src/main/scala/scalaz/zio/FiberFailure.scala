// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio
import scalaz.zio.Exit.Cause

final case class FiberFailure(cause: Cause[Any]) extends Throwable {
  override def getMessage: String = message(cause)

  private def message(cause: Cause[Any]): String = {
    def gen(t: Throwable): String =
      "The fiber was terminated by a defect: " + t.getMessage + "\n" + t.getStackTrace.mkString("\n")

    cause match {
      case Cause.Fail(t: Throwable) => "A checked error was not handled by a fiber: " + gen(t)
      case Cause.Fail(error)        => "A checked error was not handled by a fiber: " + error.toString
      case Cause.Die(t)             => "An unchecked error was produced by a fiber: " + gen(t)
      case Cause.Interrupt          => "The fiber was terminated by an interruption"
      case Cause.Then(left, right)  => "Both fibers terminated in sequence: \n" + message(left) + "\n" + message(right)
      case Cause.Both(left, right)  => "Both fibers terminated in parallel: \n" + message(left) + "\n" + message(right)
      case Cause.FinalizerErrors(c) =>
        "Further errors occurred during finalization after fiber failure: \n" + message(c)
    }
  }
}
