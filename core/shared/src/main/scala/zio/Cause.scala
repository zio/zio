/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

sealed abstract class Cause[+E] extends Product with Serializable { self =>
  import Cause._

  /**
   * Returns a cause that fails for this cause and the specified cause, in
   * parallel.
   */
  final def &&[E1 >: E](that: Cause[E1]): Cause[E1] = Both(self, that)

  /**
   * Returns a cause that fails for this cause and the specified cause, in
   * sequence.
   */
  final def ++[E1 >: E](that: Cause[E1]): Cause[E1] =
    if (self eq Empty) that else if (that eq Empty) self else Then(self, that)

  /**
   * Maps the error value of this cause to the specified constant value.
   */
  final def as[E1](e: => E1): Cause[E1] =
    map(_ => e)

  /**
   * Determines if this cause contains or is equal to the specified cause.
   */
  final def contains[E1 >: E](that: Cause[E1]): Boolean =
    (that == Empty) || (self eq that) || foldLeft[Boolean](false) { case (acc, cause) =>
      acc || (cause == that)
    }

  /**
   * Extracts a list of non-recoverable errors from the `Cause`.
   */
  final def defects: List[Throwable] =
    self
      .foldLeft(List.empty[Throwable]) { case (z, Die(v, _)) =>
        v :: z
      }
      .reverse

  /**
   * Returns the `Throwable` associated with the first `Die` in this `Cause` if
   * one exists.
   */
  final def dieOption: Option[Throwable] =
    find { case Die(t, _) => t }

  override def equals(that: Any): Boolean =
    that match {
      case that: Cause[_] => Cause.equals(self, that)
      case _              => false
    }

  /**
   * Returns the `E` associated with the first `Fail` in this `Cause` if one
   * exists.
   */
  def failureOption: Option[E] =
    find { case Fail(e, _) => e }

  /**
   * Returns the `E` associated with the first `Fail` in this `Cause` if one
   * exists, along with its (optional) trace.
   */
  def failureTraceOption: Option[(E, StackTrace)] =
    find { case Fail(e, trace) => (e, trace) }

  /**
   * Retrieve the first checked error on the `Left` if available, if there are
   * no checked errors return the rest of the `Cause` that is known to contain
   * only `Die` or `Interrupt` causes.
   */
  final def failureOrCause: Either[E, Cause[Nothing]] = failureOption match {
    case Some(error) => Left(error)
    case None        => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
  }

  /**
   * Retrieve the first checked error and its trace on the `Left` if available,
   * if there are no checked errors return the rest of the `Cause` that is known
   * to contain only `Die` or `Interrupt` causes.
   */
  final def failureTraceOrCause: Either[(E, StackTrace), Cause[Nothing]] = failureTraceOption match {
    case Some(errorAndTrace) => Left(errorAndTrace)
    case None                => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
  }

  /**
   * Produces a list of all recoverable errors `E` in the `Cause`.
   */
  final def failures: List[E] =
    self
      .foldLeft(List.empty[E]) { case (z, Fail(v, _)) =>
        v :: z
      }
      .reverse

  /**
   * Filters the specified causes out of this cause.
   */
  final def filter(p: Cause[E] => Boolean): Cause[E] = self.foldContext(())(Cause.Folder.Filter[E](p))

  /**
   * Finds something and extracts some details from it.
   */
  final def find[Z](f: PartialFunction[Cause[E], Z]): Option[Z] = {
    @tailrec
    def loop(cause: Cause[E], stack: List[Cause[E]]): Option[Z] =
      f.lift(cause) match {
        case Some(z) => Some(z)
        case None =>
          cause match {
            case Then(left, right)   => loop(left, right :: stack)
            case Both(left, right)   => loop(left, right :: stack)
            case Stackless(cause, _) => loop(cause, stack)
            case _ =>
              stack match {
                case hd :: tl => loop(hd, tl)
                case Nil      => None
              }
          }
      }
    loop(self, Nil)
  }

  /**
   * Transforms each error value in this cause to a new cause with the specified
   * function and then flattens the nested causes into a single cause.
   */
  final def flatMap[E2](f: E => Cause[E2]): Cause[E2] =
    fold[Cause[E2]](
      Empty,
      (e, trace) => f(e).traced(trace),
      (t, trace) => Die(t, trace),
      (fiberId, trace) => Interrupt(fiberId, trace)
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Flattens a nested cause.
   */
  final def flatten[E1](implicit ev: E <:< Cause[E1]): Cause[E1] =
    self.flatMap(ev)

  final def fold[Z](
    empty0: => Z,
    failCase0: (E, StackTrace) => Z,
    dieCase0: (Throwable, StackTrace) => Z,
    interruptCase0: (FiberId, StackTrace) => Z
  )(thenCase0: (Z, Z) => Z, bothCase0: (Z, Z) => Z, stacklessCase0: (Z, Boolean) => Z): Z =
    foldContext[Unit, E, Z](())(new Cause.Folder[Unit, E, Z] {
      def empty(u: Unit)                                              = empty0
      def failCase(u: Unit, e: E, trace: StackTrace)                  = failCase0(e, trace)
      def dieCase(u: Unit, t: Throwable, trace: StackTrace)           = dieCase0(t, trace)
      def interruptCase(u: Unit, fiberId: FiberId, trace: StackTrace) = interruptCase0(fiberId, trace)
      def bothCase(u: Unit, a: Z, b: Z)                               = bothCase0(a, b)
      def thenCase(u: Unit, a: Z, b: Z)                               = thenCase0(a, b)
      def stacklessCase(u: Unit, a: Z, b: Boolean)                    = stacklessCase0(a, b)
    })

  /**
   * Folds over the cases of this cause with the specified functions.
   */
  final def foldContext[C, E1 >: E, Z](context: C)(
    folder: Folder[C, E1, Z]
  ): Z = {
    import folder._
    sealed trait CauseCase

    case object BothCase                               extends CauseCase
    case object ThenCase                               extends CauseCase
    final case class StacklessCase(stackless: Boolean) extends CauseCase

    @tailrec
    def loop(in: List[Cause[E]], out: List[Either[CauseCase, Z]]): List[Z] =
      in match {
        case Fail(e, trace) :: causes              => loop(causes, Right(failCase(context, e, trace)) :: out)
        case Die(t, trace) :: causes               => loop(causes, Right(dieCase(context, t, trace)) :: out)
        case Interrupt(fiberId, trace) :: causes   => loop(causes, Right(interruptCase(context, fiberId, trace)) :: out)
        case Both(left, right) :: causes           => loop(left :: right :: causes, Left(BothCase) :: out)
        case Then(left, right) :: causes           => loop(left :: right :: causes, Left(ThenCase) :: out)
        case Stackless(cause, stackless) :: causes => loop(cause :: causes, Left(StacklessCase(stackless)) :: out)
        case _ :: causes                           => loop(causes, Right(empty(context)) :: out)
        case Nil =>
          out.foldLeft[List[Z]](List.empty) {
            case (acc, Right(causes)) => causes :: acc
            case (acc, Left(BothCase)) =>
              val left :: right :: causes = (acc: @unchecked)
              bothCase(context, left, right) :: causes
            case (acc, Left(ThenCase)) =>
              val left :: right :: causes = (acc: @unchecked)
              thenCase(context, left, right) :: causes
            case (acc, Left(StacklessCase(stackless))) =>
              val cause :: causes = acc
              stacklessCase(context, cause, stackless) :: causes
          }
      }
    loop(List(self), List.empty).head
  }

  /**
   * Folds over the cause to statefully compute a value.
   */
  final def foldLeft[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z = {
    @tailrec
    def loop(z: Z, cause: Cause[E], stack: List[Cause[E]]): Z =
      (f.applyOrElse[(Z, Cause[E]), Z](z -> cause, _ => z), cause) match {
        case (z, Then(left, right))   => loop(z, left, right :: stack)
        case (z, Both(left, right))   => loop(z, left, right :: stack)
        case (z, Stackless(cause, _)) => loop(z, cause, stack)
        case (z, _) =>
          stack match {
            case hd :: tl => loop(z, hd, tl)
            case Nil      => z
          }
      }
    loop(z, self, Nil)
  }

  override def hashCode: Int =
    Cause.flatten(self).hashCode

  /**
   * Returns a set of interruptors, fibers that interrupted the fiber described
   * by this `Cause`.
   */
  final def interruptors: Set[FiberId] =
    foldLeft[Set[FiberId]](Set()) { case (acc, Interrupt(fiberId, _)) =>
      acc + fiberId
    }

  final def interruptOption: Option[FiberId] =
    find { case Interrupt(fiberId, _) => fiberId }

  final def isDie: Boolean =
    dieOption.isDefined

  /**
   * Determines if the `Cause` is empty.
   */
  final def isEmpty: Boolean =
    (self eq Empty) || foldLeft(true) {
      case (acc, _: Empty.type) => acc
      case (_, Die(_, _))       => false
      case (_, Fail(_, _))      => false
      case (_, Interrupt(_, _)) => false
    }

  final def isFailure: Boolean =
    failureOption.isDefined

  /**
   * Determines if the `Cause` contains an interruption.
   */
  final def isInterrupted: Boolean =
    find { case Interrupt(_, _) => () }.isDefined

  /**
   * Determines if the `Cause` contains only interruptions and not any `Die` or
   * `Fail` causes.
   */
  final def isInterruptedOnly: Boolean = foldContext(())(Folder.IsInterruptedOnly)

  /**
   * Determines if the `Cause` is traced.
   */
  final def isTraced: Boolean =
    find {
      case Die(_, trace) if trace != StackTrace.none       => ()
      case Fail(_, trace) if trace != StackTrace.none      => ()
      case Interrupt(_, trace) if trace != StackTrace.none => ()
    }.isDefined

  /**
   * Remove all `Fail` and `Interrupt` nodes from this `Cause`, return only
   * `Die` cause/finalizer defects.
   */
  final def keepDefects: Option[Cause[Nothing]] =
    fold[Option[Cause[Nothing]]](
      None,
      (_, _) => None,
      (t, trace) => Some(Die(t, trace)),
      (_, _) => None
    )(
      {
        case (Some(l), Some(r)) => Some(Then(l, r))
        case (Some(l), None)    => Some(l)
        case (None, Some(r))    => Some(r)
        case (None, None)       => None
      },
      {
        case (Some(l), Some(r)) => Some(Both(l, r))
        case (Some(l), None)    => Some(l)
        case (None, Some(r))    => Some(r)
        case (None, None)       => None
      },
      (causeOption, stackless) => causeOption.map(Stackless(_, stackless))
    )

  /**
   * Linearizes this cause to a set of parallel causes where each parallel cause
   * contains a linear sequence of failures.
   */
  def linearize[E1 >: E]: Set[Cause[E1]] =
    fold[Set[Cause[E1]]](
      Set.empty,
      (e, trace) => Set(Fail(e, trace)),
      (t, trace) => Set(Die(t, trace)),
      (fiberId, trace) => Set(Interrupt(fiberId, trace))
    )(
      (left, right) => left.flatMap(l => right.map(r => l ++ r)),
      (left, right) => left | right,
      (cause, stackless) => cause.map(Stackless(_, stackless))
    )

  /**
   * Transforms the error type of this cause with the specified function.
   */
  final def map[E1](f: E => E1): Cause[E1] =
    flatMap(e => Fail(f(e), StackTrace.none))

  /**
   * Transforms the traces in this cause with the specified function.
   */
  final def mapTrace(f: StackTrace => StackTrace): Cause[E] =
    fold[Cause[E]](
      Empty,
      (e, trace) => Fail(e, f(trace)),
      (t, trace) => Die(t, f(trace)),
      (fiberId, trace) => Interrupt(fiberId, f(trace))
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Returns a `String` with the cause pretty-printed.
   */
  final def prettyPrint: String = {
    import Cause.Unified

    val builder = ChunkBuilder.make[String]()
    var size    = 0

    def append(string: String): Unit =
      if (size <= 1024) {
        builder += string
        size += 1
      }

    def appendCause(cause: Cause[E]): Unit =
      cause.unified.zipWithIndex.foreach {
        case (unified, 0) =>
          appendUnified(0, "Exception in thread \"" + unified.fiberId.threadName + "\" ", unified)
        case (unified, n) =>
          appendUnified(n, s"Suppressed: ", unified)
      }

    def appendUnified(indent: Int, prefix: String, unified: Unified): Unit = {
      val baseIndent  = "\t" * indent
      val traceIndent = baseIndent + "\t"

      append(s"${baseIndent}${prefix}${unified.className}: ${unified.message}")
      unified.trace.foreach(trace => append(s"${traceIndent}at ${trace}"))
    }

    self.linearize.foreach(appendCause)
    builder.result.mkString("\n")
  }

  def size: Int = self.foldContext(())(Cause.Folder.Size)

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the "most
   * important" `Throwable`.
   */
  final def squash(implicit ev: E IsSubtypeOfError Throwable): Throwable =
    squashWith(ev)

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the "most
   * important" `Throwable`.
   */
  final def squashWith(f: E => Throwable): Throwable =
    failureOption.map(f) orElse
      (if (isInterrupted)
         Some(
           new InterruptedException(
             "Interrupted by fibers: " + interruptors.flatMap(_.ids).map("#" + _).mkString(", ")
           )
         )
       else None) orElse
      defects.headOption getOrElse (new InterruptedException)

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the "most
   * important" `Throwable`. In addition, appends a new element to the
   * suppressed exceptions of the `Throwable`, with this `Cause` "pretty
   * printed" (in stackless mode) as the message.
   */
  final def squashTrace(implicit ev: E IsSubtypeOfError Throwable): Throwable =
    squashTraceWith(ev)

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the "most
   * important" `Throwable`. In addition, appends a new element the to
   * `Throwable`s "caused by" chain, with this `Cause` "pretty printed" (in
   * stackless mode) as the message.
   */
  final def squashTraceWith(f: E => Throwable): Throwable =
    attachTrace(squashWith(f))

  /**
   * Discards all typed failures kept on this `Cause`.
   */
  final def stripFailures: Cause[Nothing] =
    fold[Cause[Nothing]](
      Empty,
      (e, trace) => Empty,
      (t, trace) => Die(t, trace),
      (fiberId, trace) => Interrupt(fiberId, trace)
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Remove all `Die` causes that the specified partial function is defined at,
   * returning `Some` with the remaining causes or `None` if there are no
   * remaining causes.
   */
  final def stripSomeDefects(pf: PartialFunction[Throwable, Any]): Option[Cause[E]] =
    fold[Option[Cause[E]]](
      Some(Empty),
      (e, trace) => Some(Fail(e, trace)),
      (t, trace) => if (pf.isDefinedAt(t)) None else Some(Die(t, trace)),
      (fiberId, trace) => Some(Interrupt(fiberId, trace))
    )(
      {
        case (Some(l), Some(r)) => Some(Then(l, r))
        case (Some(l), None)    => Some(l)
        case (None, Some(r))    => Some(r)
        case (None, None)       => None
      },
      {
        case (Some(l), Some(r)) => Some(Both(l, r))
        case (Some(l), None)    => Some(l)
        case (None, Some(r))    => Some(r)
        case (None, None)       => None
      },
      (causeOption, stackless) => causeOption.map(Stackless(_, stackless))
    )

  /**
   * Grabs a complete, linearized trace for the cause. Note: This linearization
   * may be misleading in the presence of parallel errors.
   */
  def trace: StackTrace = traces.fold(StackTrace.none)(_ ++ _)

  /**
   * Grabs a list of execution traces from the cause.
   */
  final def traces: List[StackTrace] =
    self
      .foldLeft(List.empty[StackTrace]) {
        case (z, Die(_, trace))       => trace :: z
        case (z, Fail(_, trace))      => trace :: z
        case (z, Interrupt(_, trace)) => trace :: z
        case (z, _)                   => z
      }
      .reverse

  /**
   * Adds the specified execution trace to traces.
   */
  final def traced(trace: StackTrace): Cause[E] =
    mapTrace(_ ++ trace)

  /**
   * Returns a homogenized list of failures for the cause. This homogenization
   * process throws away key information, but it is useful for interop with
   * traditional stack traces.
   */
  final def unified: List[Unified] = {
    @tailrec
    def loop(
      causes: List[Cause[E]],
      fiberId: FiberId,
      stackless: Boolean,
      result: List[Unified]
    ): List[Unified] = {
      def unifyThrowable(throwable: Throwable, trace: StackTrace): List[Unified] = {

        @tailrec
        def loop(throwable: Throwable, trace: StackTrace, result: List[Unified]): List[Unified] = {
          val extra =
            if (stackless) Chunk.empty
            else Chunk.fromArray(throwable.getStackTrace.takeWhile(!_.getClassName.startsWith("zio.")))

          val unified =
            Unified(trace.fiberId, throwable.getClass.getName(), throwable.getMessage(), extra ++ trace.toJava)

          if (throwable.getCause eq null) unified :: result
          else loop(throwable.getCause, StackTrace.none, unified :: result)
        }

        loop(throwable, trace, Nil)
      }

      def unifyFail(fail: Cause.Fail[E]): List[Unified] =
        fail.value match {
          case throwable: Throwable => unifyThrowable(throwable, fail.trace)
          case value                => List(Unified(fail.trace.fiberId, value.getClass.getName(), value.toString(), fail.trace.toJava))
        }

      def unifyDie(die: Cause.Die): List[Unified] =
        unifyThrowable(die.value, die.trace)

      def unifyInterrupt(interrupt: Cause.Interrupt): Unified = {
        val message = "Interrupted by thread \"" + interrupt.fiberId.threadName + "\""

        Unified(interrupt.trace.fiberId, classOf[InterruptedException].getName(), message, interrupt.trace.toJava)
      }

      causes match {
        case Nil => result

        case (_: Empty.type) :: more             => loop(more, fiberId, stackless, result)
        case Both(left, right) :: more           => loop(left :: right :: more, fiberId, stackless, result)
        case Stackless(cause, stackless) :: more => loop(cause :: more, fiberId, stackless, result)
        case Then(left, right) :: more           => loop(left :: right :: more, fiberId, stackless, result)
        case (cause @ Fail(_, _)) :: more        => loop(more, fiberId, stackless, unifyFail(cause) ::: result)
        case (cause @ Die(_, _)) :: more         => loop(more, fiberId, stackless, unifyDie(cause) ::: result)
        case (cause @ Interrupt(_, _)) :: more =>
          loop(more, fiberId, stackless, unifyInterrupt(cause) :: result)
      }
    }

    loop(self :: Nil, FiberId.None, false, Nil).reverse
  }

  /**
   * Returns a `Cause` that has been stripped of all tracing information.
   */
  final def untraced: Cause[E] =
    mapTrace(_ => StackTrace.none)

  private def attachTrace(e: Throwable): Throwable = {
    val trace = Cause.FiberTrace(Cause.stackless(this).prettyPrint)
    e.addSuppressed(trace)
    e
  }
}

object Cause extends Serializable {
  val empty: Cause[Nothing]                                                            = Empty
  def die(defect: Throwable, trace: StackTrace = StackTrace.none): Cause[Nothing]      = Die(defect, trace)
  def fail[E](error: E, trace: StackTrace = StackTrace.none): Cause[E]                 = Fail(error, trace)
  def interrupt(fiberId: FiberId, trace: StackTrace = StackTrace.none): Cause[Nothing] = Interrupt(fiberId, trace)
  def stack[E](cause: Cause[E]): Cause[E]                                              = Stackless(cause, false)
  def stackless[E](cause: Cause[E]): Cause[E]                                          = Stackless(cause, true)

  trait Folder[-Context, -E, Z] {
    def empty(context: Context): Z
    def failCase(context: Context, error: E, stackTrace: StackTrace): Z
    def dieCase(context: Context, t: Throwable, stackTrace: StackTrace): Z
    def interruptCase(context: Context, fiberId: FiberId, stackTrace: StackTrace): Z

    def bothCase(context: Context, left: Z, right: Z): Z
    def thenCase(context: Context, left: Z, right: Z): Z
    def stacklessCase(context: Context, value: Z, stackless: Boolean): Z
  }
  object Folder {
    case object Size extends Folder[Any, Any, Int] {
      def empty(context: Any): Int                                                   = 0
      def failCase(context: Any, error: Any, stackTrace: StackTrace): Int            = 1
      def dieCase(context: Any, t: Throwable, stackTrace: StackTrace): Int           = 1
      def interruptCase(context: Any, fiberId: FiberId, stackTrace: StackTrace): Int = 1

      def bothCase(context: Any, left: Int, right: Int): Int               = left + right
      def thenCase(context: Any, left: Int, right: Int): Int               = left + right
      def stacklessCase(context: Any, value: Int, stackless: Boolean): Int = value
    }

    case object IsInterruptedOnly extends Folder[Any, Any, Boolean] {
      def empty(context: Any): Boolean                                                   = true
      def failCase(context: Any, error: Any, stackTrace: StackTrace): Boolean            = false
      def dieCase(context: Any, t: Throwable, stackTrace: StackTrace): Boolean           = false
      def interruptCase(context: Any, fiberId: FiberId, stackTrace: StackTrace): Boolean = true

      def bothCase(context: Any, left: Boolean, right: Boolean): Boolean           = left && right
      def thenCase(context: Any, left: Boolean, right: Boolean): Boolean           = left && right
      def stacklessCase(context: Any, value: Boolean, stackless: Boolean): Boolean = value
    }

    final case class Filter[E](p: Cause[E] => Boolean) extends Folder[Any, E, Cause[E]] {
      def empty(context: Any): Cause[E] = Cause.empty

      def failCase(context: Any, error: E, stackTrace: StackTrace): Cause[E] = Cause.Fail(error, stackTrace)

      def dieCase(context: Any, t: Throwable, stackTrace: StackTrace): Cause[E] = Cause.Die(t, stackTrace)

      def interruptCase(context: Any, fiberId: FiberId, stackTrace: StackTrace): Cause[E] =
        Cause.Interrupt(fiberId, stackTrace)

      def bothCase(context: Any, left: Cause[E], right: Cause[E]): Cause[E] =
        if (p(left)) {
          if (p(right)) Cause.Both(left, right)
          else left
        } else if (p(right)) right
        else Cause.empty

      def thenCase(context: Any, left: Cause[E], right: Cause[E]): Cause[E] =
        if (p(left)) {
          if (p(right)) Cause.Then(left, right)
          else left
        } else if (p(right)) right
        else Cause.empty

      def stacklessCase(context: Any, value: Cause[E], stackless: Boolean): Cause[E] = Stackless(value, stackless)
    }
  }

  final case class Unified(fiberId: FiberId, className: String, message: String, trace: Chunk[StackTraceElement]) {
    def toThrowable: Throwable =
      new Throwable(null, null, false, false) {
        override final def getMessage(): String = message

        override final def getStackTrace(): Array[StackTraceElement] = trace.toArray
      }
  }

  /**
   * Converts the specified `Cause[Option[E]]` to an `Option[Cause[E]]` by
   * recursively stripping out any failures with the error `None`.
   */
  def flipCauseOption[E](cause: Cause[Option[E]]): Option[Cause[E]] =
    cause.fold[Option[Cause[E]]](
      Some(Empty),
      (failureOption, trace) => failureOption.map(Fail(_, trace)),
      (t, trace) => Some(Die(t, trace)),
      (fiberId, trace) => Some(Interrupt(fiberId, trace))
    )(
      {
        case (Some(l), Some(r)) => Some(Then(l, r))
        case (None, Some(r))    => Some(r)
        case (Some(l), None)    => Some(l)
        case (None, None)       => None
      },
      {
        case (Some(l), Some(r)) => Some(Both(l, r))
        case (None, Some(r))    => Some(r)
        case (Some(l), None)    => Some(l)
        case (None, None)       => None
      },
      (causeOption, stackless) => causeOption.map(Stackless(_, stackless))
    )

  case object Empty extends Cause[Nothing]

  final case class Fail[+E](value: E, override val trace: StackTrace) extends Cause[E]

  final case class Die(value: Throwable, override val trace: StackTrace) extends Cause[Nothing]

  final case class Interrupt(fiberId: FiberId, override val trace: StackTrace) extends Cause[Nothing]

  final case class Stackless[+E](cause: Cause[E], stackless: Boolean) extends Cause[E]

  final case class Then[+E](left: Cause[E], right: Cause[E]) extends Cause[E]

  final case class Both[+E](left: Cause[E], right: Cause[E]) extends Cause[E]

  private def equals(left: Cause[Any], right: Cause[Any]): Boolean = {

    @tailrec
    def loop(left: List[Cause[Any]], right: List[Cause[Any]]): Boolean = {
      val (leftParallel, leftSequential) = left.foldLeft((Set.empty[Any], List.empty[Cause[Any]])) {
        case ((parallel, sequential), cause) =>
          val (set, seq) = step(cause)
          (parallel ++ set, sequential ++ seq)
      }
      val (rightParallel, rightSequential) = right.foldLeft((Set.empty[Any], List.empty[Cause[Any]])) {
        case ((parallel, sequential), cause) =>
          val (set, seq) = step(cause)
          (parallel ++ set, sequential ++ seq)
      }
      if (leftParallel != rightParallel) false
      else if (leftSequential.isEmpty && rightSequential.isEmpty) true
      else loop(leftSequential, rightSequential)
    }

    loop(List(left), List(right))
  }

  /**
   * Flattens a cause to a sequence of sets of causes, where each set represents
   * causes that fail in parallel and sequential sets represent causes that fail
   * after each other.
   */
  private def flatten(c: Cause[Any]): List[Set[Any]] = {

    @tailrec
    def loop(causes: List[Cause[Any]], flattened: List[Set[Any]]): List[Set[Any]] = {
      val (parallel, sequential) = causes.foldLeft((Set.empty[Any], List.empty[Cause[Any]])) {
        case ((parallel, sequential), cause) =>
          val (set, seq) = step(cause)
          (parallel ++ set, sequential ++ seq)
      }
      val updated = if (parallel.nonEmpty) parallel :: flattened else flattened
      if (sequential.isEmpty) updated.reverse
      else loop(sequential, updated)
    }

    loop(List(c), List.empty)
  }

  /**
   * Takes one step in evaluating a cause, returning a set of causes that fail
   * in parallel and a list of causes that fail sequentially after those causes.
   */
  private def step(c: Cause[Any]): (Set[Any], List[Cause[Any]]) = {

    @tailrec
    def loop(
      cause: Cause[Any],
      stack: List[Cause[Any]],
      parallel: Set[Any],
      sequential: List[Cause[Any]]
    ): (Set[Any], List[Cause[Any]]) = cause match {
      case Fail(e, _) =>
        if (stack.isEmpty) (parallel + e, sequential)
        else loop(stack.head, stack.tail, parallel + e, sequential)
      case Die(t, _) =>
        if (stack.isEmpty) (parallel + t, sequential)
        else loop(stack.head, stack.tail, parallel + t, sequential)
      case Interrupt(fiberId, _) =>
        if (stack.isEmpty) (parallel + fiberId, sequential)
        else loop(stack.head, stack.tail, parallel + fiberId, sequential)
      case Then(left, right) =>
        left match {
          case (_: Empty.type) => loop(right, stack, parallel, sequential)
          case Then(l, r)      => loop(Then(l, Then(r, right)), stack, parallel, sequential)
          case Both(l, r) =>
            loop(Both(Then(l, right), Then(r, right)), stack, parallel, sequential)
          case Stackless(c, _) => loop(Then(c, right), stack, parallel, sequential)
          case o               => loop(o, stack, parallel, right :: sequential)
        }
      case Both(left, right) =>
        loop(left, right :: stack, parallel, sequential)
      case Stackless(cause, _) =>
        loop(cause, stack, parallel, sequential)
      case _ =>
        if (stack.isEmpty) (parallel, sequential)
        else loop(stack.head, stack.tail, parallel, sequential)
    }

    loop(c, List.empty, Set.empty, List.empty)
  }

  private case class FiberTrace(trace: String) extends Throwable(null, null, true, false) {
    override final def getMessage: String = trace
  }
}
