// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

object Errors {
  final case class LostRace(loser: Either[Fiber[_, _], Fiber[_, _]])
      extends Exception("Lost a race to " + loser.fold(_ => "right", _ => "left"))

  final case class TerminatedException(value: Any)
      extends Exception("The action was interrupted due to a user-defined error: " + value.toString())

  final case class UnhandledError(error: Any)
      extends Exception("An error was not handled by a fiber: " + error.toString())
}
