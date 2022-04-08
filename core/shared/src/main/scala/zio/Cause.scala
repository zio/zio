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
    (self eq that) || foldLeft[Boolean](false) { case (acc, cause) =>
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
  def failureTraceOption: Option[(E, ZTrace)] =
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
  final def failureTraceOrCause: Either[(E, ZTrace), Cause[Nothing]] = failureTraceOption match {
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

  /**
   * Folds over the cases of this cause with the specified functions.
   */
  final def fold[Z](
    empty: => Z,
    failCase: (E, ZTrace) => Z,
    dieCase: (Throwable, ZTrace) => Z,
    interruptCase: (FiberId, ZTrace) => Z
  )(thenCase: (Z, Z) => Z, bothCase: (Z, Z) => Z, stacklessCase: (Z, Boolean) => Z): Z = {

    sealed trait CauseCase

    case object BothCase                               extends CauseCase
    case object ThenCase                               extends CauseCase
    final case class StacklessCase(stackless: Boolean) extends CauseCase

    @tailrec
    def loop(in: List[Cause[E]], out: List[Either[CauseCase, Z]]): List[Z] =
      in match {
        case Empty :: causes                       => loop(causes, Right(empty) :: out)
        case Fail(e, trace) :: causes              => loop(causes, Right(failCase(e, trace)) :: out)
        case Die(t, trace) :: causes               => loop(causes, Right(dieCase(t, trace)) :: out)
        case Interrupt(fiberId, trace) :: causes   => loop(causes, Right(interruptCase(fiberId, trace)) :: out)
        case Both(left, right) :: causes           => loop(left :: right :: causes, Left(BothCase) :: out)
        case Then(left, right) :: causes           => loop(left :: right :: causes, Left(ThenCase) :: out)
        case Stackless(cause, stackless) :: causes => loop(cause :: causes, Left(StacklessCase(stackless)) :: out)
        case Nil =>
          out.foldLeft[List[Z]](List.empty) {
            case (acc, Right(causes)) => causes :: acc
            case (acc, Left(BothCase)) =>
              val left :: right :: causes = acc: @unchecked
              bothCase(left, right) :: causes
            case (acc, Left(ThenCase)) =>
              val left :: right :: causes = acc: @unchecked
              thenCase(left, right) :: causes
            case (acc, Left(StacklessCase(stackless))) =>
              val cause :: causes = acc
              stacklessCase(cause, stackless) :: causes
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
  final def isInterruptedOnly: Boolean =
    find {
      case Die(_, _)  => false
      case Fail(_, _) => false
    }.getOrElse(true)

  /**
   * Determines if the `Cause` is traced.
   */
  final def isTraced: Boolean =
    find {
      case Die(_, trace) if trace != ZTrace.none       => ()
      case Fail(_, trace) if trace != ZTrace.none      => ()
      case Interrupt(_, trace) if trace != ZTrace.none => ()
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
    flatMap(e => Fail(f(e), ZTrace.none))

  /**
   * Transforms the traces in this cause with the specified function.
   */
  final def mapTrace(f: ZTrace => ZTrace): Cause[E] =
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

    def renderCause(cause: Cause[E]): String = {
      def renderUnified(indent: Int, prefix: String, unified: Unified) = {
        val baseIndent  = "\t" * indent
        val traceIndent = baseIndent + "\t"

        s"${baseIndent}${prefix}${unified.className}: ${unified.message}\n" +
          unified.trace.map(trace => s"${traceIndent}at ${trace}").mkString("\n")
      }

      cause.unified.zipWithIndex.map {
        case (unified, 0) =>
          renderUnified(0, "Exception in thread \"" + unified.fiberId.threadName + "\" ", unified)
        case (unified, n) => renderUnified(n, s"Suppressed: ", unified)
      }.mkString("\n")
    }

    self.linearize.map(renderCause).mkString("\n")
  }

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
             "Interrupted by fibers: " + interruptors.flatMap(_.ids.toString()).map("#" + _).mkString(", ")
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
  def trace: ZTrace = traces.fold(ZTrace.none)(_ ++ _)

  /**
   * Grabs a list of execution traces from the cause.
   */
  final def traces: List[ZTrace] =
    self
      .foldLeft(List.empty[ZTrace]) {
        case (z, Die(_, trace))       => trace :: z
        case (z, Fail(_, trace))      => trace :: z
        case (z, Interrupt(_, trace)) => trace :: z
        case (z, _)                   => z
      }
      .reverse

  /**
   * Adds the specified execution trace to traces.
   */
  final def traced(trace: ZTrace): Cause[E] =
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
      def unifyFail(fail: Cause.Fail[E]): Unified =
        Unified(fail.trace.fiberId, fail.value.getClass.getName(), fail.value.toString(), fail.trace.toJava)

      def unifyDie(die: Cause.Die): List[Unified] = {

        @tailrec
        def loop(die: Cause.Die, result: List[Unified]): List[Unified] = {
          val extra =
            if (stackless) Chunk.empty else Chunk.fromArray(die.value.getStackTrace())

          val unified =
            Unified(die.trace.fiberId, die.value.getClass.getName(), die.value.getMessage(), extra ++ die.trace.toJava)

          if (die.value.getCause eq null) unified :: result
          else loop(Cause.Die(die.value.getCause, ZTrace.none), unified :: result)
        }

        loop(die, Nil)
      }

      def unifyInterrupt(interrupt: Cause.Interrupt): Unified = {
        val message = "Interrupted by thread \"" + interrupt.fiberId.threadName + "\""

        Unified(interrupt.trace.fiberId, classOf[InterruptedException].getName(), message, interrupt.trace.toJava)
      }

      causes match {
        case Nil => result

        case Empty :: more                       => loop(more, fiberId, stackless, result)
        case Both(left, right) :: more           => loop(left :: right :: more, fiberId, stackless, result)
        case Stackless(cause, stackless) :: more => loop(cause :: more, fiberId, stackless, result)
        case Then(left, right) :: more           => loop(left :: right :: more, fiberId, stackless, result)
        case (cause @ Fail(_, _)) :: more        => loop(more, fiberId, stackless, unifyFail(cause) :: result)
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
    mapTrace(_ => ZTrace.none)

  private def attachTrace(e: Throwable): Throwable = {
    val trace = Cause.FiberTrace(Cause.stackless(this).prettyPrint)
    e.addSuppressed(trace)
    e
  }
}

object Cause extends Serializable {
  val empty: Cause[Nothing]                                                    = Empty
  def die(defect: Throwable, trace: ZTrace = ZTrace.none): Cause[Nothing]      = Die(defect, trace)
  def fail[E](error: E, trace: ZTrace = ZTrace.none): Cause[E]                 = Fail(error, trace)
  def interrupt(fiberId: FiberId, trace: ZTrace = ZTrace.none): Cause[Nothing] = Interrupt(fiberId, trace)
  def stack[E](cause: Cause[E]): Cause[E]                                      = Stackless(cause, false)
  def stackless[E](cause: Cause[E]): Cause[E]                                  = Stackless(cause, true)

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

  case object Empty extends Cause[Nothing] { self =>
    override def equals(that: Any): Boolean = that match {
      case _: Empty.type      => true
      case Then(left, right)  => this == left && this == right
      case Both(left, right)  => this == left && this == right
      case meta: Stackless[_] => this == meta.cause
      case _                  => false
    }
  }

  final case class Fail[+E](value: E, override val trace: ZTrace) extends Cause[E] {
    override def equals(that: Any): Boolean = that match {
      case fail: Fail[_]      => value == fail.value
      case c @ Then(_, _)     => sym(empty)(this, c)
      case c @ Both(_, _)     => sym(empty)(this, c)
      case meta: Stackless[_] => this == meta.cause
      case _                  => false
    }
  }

  final case class Die(value: Throwable, override val trace: ZTrace) extends Cause[Nothing] {
    override def equals(that: Any): Boolean = that match {
      case die: Die           => value == die.value
      case c @ Then(_, _)     => sym(empty)(this, c)
      case c @ Both(_, _)     => sym(empty)(this, c)
      case meta: Stackless[_] => this == meta.cause
      case _                  => false
    }
  }

  final case class Interrupt(fiberId: FiberId, override val trace: ZTrace) extends Cause[Nothing] {
    override def equals(that: Any): Boolean =
      (this eq that.asInstanceOf[AnyRef]) || (that match {
        case interrupt: Interrupt => fiberId == interrupt.fiberId
        case c @ Then(_, _)       => sym(empty)(this, c)
        case c @ Both(_, _)       => sym(empty)(this, c)
        case meta: Stackless[_]   => this == meta.cause
        case _                    => false
      })
  }

  // Stackless is excluded completely from equals & hashCode
  final case class Stackless[+E](cause: Cause[E], stackless: Boolean) extends Cause[E] {
    override def hashCode: Int = cause.hashCode
    override def equals(obj: Any): Boolean = obj match {
      case stackless: Stackless[_] => cause == stackless.cause
      case _                       => cause == obj
    }
  }

  final case class Then[+E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
    override def equals(that: Any): Boolean = that match {
      case meta: Stackless[_] => self.equals(meta.cause)
      case other: Cause[_] =>
        eq(other) || sym(assoc)(other, self) || sym(dist)(self, other) || sym(empty)(self, other)
      case _ => false
    }
    override def hashCode: Int = Cause.hashCode(self)

    private def eq(that: Cause[Any]): Boolean = (self, that) match {
      case (tl: Then[_], tr: Then[_]) => tl.left == tr.left && tl.right == tr.right
      case _                          => false
    }

    private def assoc(l: Cause[Any], r: Cause[Any]): Boolean = (l, r) match {
      case (Then(Then(al, bl), cl), Then(ar, Then(br, cr))) => al == ar && bl == br && cl == cr
      case _                                                => false
    }

    private def dist(l: Cause[Any], r: Cause[Any]): Boolean = (l, r) match {
      case (Then(al, Both(bl, cl)), Both(ar, br)) if Then(al, bl) == ar && Then(al, cl) == br => true
      case (Then(Both(al, bl), cl), Both(ar, br)) if Then(al, cl) == ar && Then(bl, cl) == br => true
      case _                                                                                  => false
    }
  }

  final case class Both[+E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
    override def equals(that: Any): Boolean = that match {
      case stackless: Stackless[_] => self.equals(stackless.cause)
      case other: Cause[_] =>
        eq(other) || sym(assoc)(self, other) || sym(dist)(self, other) || comm(other) || sym(empty)(self, other)
      case _ => false
    }
    override def hashCode: Int = Cause.hashCode(self)

    private def eq(that: Cause[Any]) = (self, that) match {
      case (bl: Both[_], br: Both[_]) => bl.left == br.left && bl.right == br.right
      case _                          => false
    }

    private def assoc(l: Cause[Any], r: Cause[Any]): Boolean = (l, r) match {
      case (Both(Both(al, bl), cl), Both(ar, Both(br, cr))) => al == ar && bl == br && cl == cr
      case _                                                => false
    }

    private def dist(l: Cause[Any], r: Cause[Any]): Boolean = (l, r) match {
      case (Both(al, bl), Then(ar, Both(br, cr))) if al == Then(ar, br) && bl == Then(ar, cr) => true
      case (Both(al, bl), Then(Both(ar, br), cr)) if al == Then(ar, cr) && bl == Then(br, cr) => true
      case _                                                                                  => false
    }

    private def comm(that: Cause[Any]): Boolean = (self, that) match {
      case (Both(al, bl), Both(ar, br)) => al == br && bl == ar
      case _                            => false
    }
  }

  private def empty(l: Cause[Any], r: Cause[Any]): Boolean = (l, r) match {
    case (Then(a, Empty), b) => a == b
    case (Then(Empty, a), b) => a == b
    case (Both(a, Empty), b) => a == b
    case (Both(Empty, a), b) => a == b
    case _                   => false
  }

  private def sym(f: (Cause[Any], Cause[Any]) => Boolean): (Cause[Any], Cause[Any]) => Boolean =
    (l, r) => f(l, r) || f(r, l)

  private def hashCode(c: Cause[_]): Int = flatten(c) match {
    case Nil                         => Empty.hashCode
    case set :: Nil if set.size == 1 => set.head.hashCode
    case seq                         => seq.hashCode
  }

  /**
   * Flattens a cause to a sequence of sets of causes, where each set represents
   * causes that fail in parallel and sequential sets represent causes that fail
   * after each other.
   */
  private def flatten(c: Cause[_]): List[Set[Cause[_]]] = {

    @tailrec
    def loop(causes: List[Cause[_]], flattened: List[Set[Cause[_]]]): List[Set[Cause[_]]] = {
      val (parallel, sequential) = causes.foldLeft((Set.empty[Cause[_]], List.empty[Cause[_]])) {
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
  private def step(c: Cause[_]): (Set[Cause[_]], List[Cause[_]]) = {

    @tailrec
    def loop(
      cause: Cause[_],
      stack: List[Cause[_]],
      parallel: Set[Cause[_]],
      sequential: List[Cause[_]]
    ): (Set[Cause[_]], List[Cause[_]]) = cause match {
      case Empty =>
        if (stack.isEmpty) (parallel, sequential) else loop(stack.head, stack.tail, parallel, sequential)
      case Then(left, right) =>
        left match {
          case Empty      => loop(right, stack, parallel, sequential)
          case Then(l, r) => loop(Then(l, Then(r, right)), stack, parallel, sequential)
          case Both(l, r) =>
            loop(Both(Then(l, right), Then(r, right)), stack, parallel, sequential)
          case Stackless(c, _) => loop(Then(c, right), stack, parallel, sequential)
          case o               => loop(o, stack, parallel, right :: sequential)
        }
      case Both(left, right)   => loop(left, right :: stack, parallel, sequential)
      case Stackless(cause, _) => loop(cause, stack, parallel, sequential)
      case o =>
        if (stack.isEmpty) (parallel ++ Set(o), sequential)
        else loop(stack.head, stack.tail, parallel ++ Set(o), sequential)
    }

    loop(c, List.empty, Set.empty, List.empty)
  }

  private case class FiberTrace(trace: String) extends Throwable(null, null, true, false) {
    override final def getMessage: String = trace
  }
}
