// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

object Errors {

  final case class UnhandledError(error: Any, defects: List[Throwable] = Nil)
      extends Exception("An error was not handled by a fiber: " + error.toString())

  final case class TerminatedFiber(defect: Throwable, defects: List[Throwable])
      extends Exception("The fiber was terminated by a defect")

  final case class InterruptedFiber(causes: List[Throwable], defects: List[Throwable])
      extends Exception("The fiber was terminated by an interruption")
}
