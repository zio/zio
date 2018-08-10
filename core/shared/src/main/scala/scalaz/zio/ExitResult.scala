// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

/**
 * A description of the result of executing an `IO` value. The result is either
 * completed with a value, failed because of an uncaught `E`, or terminated
 * due to interruption or runtime error.
 */
sealed trait ExitResult[+E, +A] { self =>
  import ExitResult._

  final def succeeded: Boolean = self match {
    case Completed(_) => true
    case _            => false
  }

  final def map[B](f: A => B): ExitResult[E, B] = self match {
    case Completed(a) => Completed(f(a))
    case x            => x.asInstanceOf[ExitResult[E, B]]
  }

  final def mapError[E2, A1 >: A](f: E => ExitResult[E2, A1]): ExitResult[E2, A1] = self match {
    case ExitResult.Failed(e, _) => f(e)
    case x                       => x.asInstanceOf[ExitResult[E2, A1]]
  }

  final def failed: Boolean = !succeeded

  final def fold[Z](completed: A => Z,
                    failed: (E, List[Throwable]) => Z,
                    interrupted: (List[Throwable], List[Throwable]) => Z): Z =
    self match {
      case Completed(v)     => completed(v)
      case Failed(e, ts)    => failed(e, ts)
      case Terminated(e, d) => interrupted(e, d)
    }
}
object ExitResult {
  final case class Completed[E, A](value: A) extends ExitResult[E, A]

  /**
   * `defects` refer to exceptions thrown during finalization:
   * first element in list = first failure, last element in list = last failure.
   */
  final case class Failed[E, A](error: E, defects: List[Throwable] = Nil) extends ExitResult[E, A]

  /**
   * `causes` accretes interruption causes and exceptions thrown during finalization:
   * first element in list = first failure, last element in list = last failure.
   */
  final case class Terminated[E, A](causes: List[Throwable], defects: List[Throwable] = Nil) extends ExitResult[E, A]
}
