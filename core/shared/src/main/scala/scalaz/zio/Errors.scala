// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

object Errors {

  final case class UnhandledError(error: Any, defects: List[Throwable] = Nil)
      extends Exception("An error was not handled by a fiber: " + error.toString())

  final object TerminatedFiber extends Exception("The fiber was terminated either by a defect or an interruption")
}
