// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

object Errors {
  final object NothingRaced extends Exception("Nothing was raced")

  final case class LostRace(loser: Either[Fiber[_, _], Fiber[_, _]])
      extends Exception("Lost a race to " + loser.fold(_ => "right", _ => "left"))

  final case class TerminatedException(value: Any)
      extends Exception("The action was interrupted due to a user-defined error: " + value.toString())

  final case class UnhandledError(error: Any)
      extends Exception("An error was not handled by a fiber: " + error.toString())

  final object InterruptedFiber extends Exception("The fiber was interrupted by a user-defined action or its supervisor")
}
