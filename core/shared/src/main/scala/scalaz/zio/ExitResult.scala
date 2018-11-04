// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

/**
 * A description of the result of executing an `IO` value. The result is either
 * completed with a value, failed because of an uncaught `E`, or terminated
 * due to interruption or runtime error.
 */
sealed abstract class ExitResult[+E, +A] extends Product with Serializable { self =>
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

  final def fold[Z](
    completed: A => Z,
    failed: (E, List[Throwable]) => Z,
    interrupted: (List[Throwable], List[Throwable]) => Z,
    terminated: (Throwable, List[Throwable]) => Z
  ): Z =
    self match {
      case Completed(v)       => completed(v)
      case Failed(e, ts)      => failed(e, ts)
      case Interrupted(e, ts) => interrupted(e, ts)
      case Terminated(t, ts)  => terminated(t, ts)
    }

  final def zipWith[E1 >: E, B, C](that: ExitResult[E1, B])(f: (A, B) => C): ExitResult[E1, C] = (self, that) match {
    case (ExitResult.Completed(a), ExitResult.Completed(b))       => ExitResult.Completed(f(a, b))
    case (ExitResult.Failed(e, ts), rb)                           => ExitResult.Failed(e, combine(ts, rb))
    case (ExitResult.Interrupted(e, ts), rb)                      => ExitResult.Interrupted(e, combine(ts, rb))
    case (ExitResult.Terminated(t, ts), rb)                       => ExitResult.Terminated(t, combine(ts, rb))
    case (ExitResult.Completed(_), ExitResult.Failed(e, ts))      => ExitResult.Failed(e, ts)
    case (ExitResult.Completed(_), ExitResult.Interrupted(e, ts)) => ExitResult.Interrupted(e, ts)
    case (ExitResult.Completed(_), ExitResult.Terminated(t, ts))  => ExitResult.Terminated(t, ts)
  }

  private final def combine(ts: List[Throwable], r: ExitResult[_, _]): List[Throwable] = r match {
    case ExitResult.Failed(_, ts2)      => ts ++ ts2
    case ExitResult.Interrupted(e, ts2) => ts ++ e ++ ts2
    case ExitResult.Terminated(t, ts2)  => (t :: ts) ++ ts2
    case _                              => ts
  }
}
object ExitResult extends Serializable {
  final case class Completed[E, A](value: A) extends ExitResult[E, A]

  /**
   * `defects` refer to exceptions thrown during finalization:
   * first element in list = first failure, last element in list = last failure.
   */
  final case class Failed[E, A](error: E, defects: List[Throwable] = Nil) extends ExitResult[E, A]

  /**
   * `causes` accretes interruption causes, `defects` refer to exceptions thrown during finalization:
   * first element in list = first failure, last element in list = last failure.
   */
  final case class Interrupted[E, A](causes: List[Throwable], defects: List[Throwable]) extends ExitResult[E, A]

  /**
   * `defect` is the cause of termination 
   * `defects` refer to exceptions thrown during finalization:
   * first element in list = first failure, last element in list = last failure.
   */
  final case class Terminated[E, A](defect: Throwable, defects: List[Throwable]) extends ExitResult[E, A]
}
