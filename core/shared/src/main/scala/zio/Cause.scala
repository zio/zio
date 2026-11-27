/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.Cause.Both
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.PrintWriter
import scala.annotation.tailrec
import scala.collection.mutable
import scala.runtime.AbstractFunction2

/**
 * A `Cause[E]` describes why an effect failed, and can hold several reasons at
 * once. Its leaves are the typed failures of type `E` (`Fail`), the defects,
 * which are unexpected `Throwable`s (`Die`), and the interruptions, which
 * record the id of the interrupting fiber (`Interrupt`). `Then` composes two
 * causes that happened in sequence, such as a failure followed by a failing
 * finalizer, and `Both` composes two causes that happened in parallel, such as
 * the failures of two `zipPar` branches. `Empty` is the cause with nothing in
 * it, and `Stackless` wraps a cause to control how much of the JVM stack trace
 * `prettyPrint` shows.
 *
 * `++` combines two causes in sequence and `&&` combines them in parallel.
 * Equality ignores traces, spans, annotations and `Stackless` wrappers: `&&` is
 * commutative and associative, `++` is associative, `++` distributes over `&&`,
 * and `Empty` is the identity of both.
 *
 * {{{
 * val timeout: Cause[String] = Cause.fail("timeout")
 * val crash: Cause[Nothing]  = Cause.die(new IllegalStateException("crash"))
 *
 * val parallel: Cause[String]   = timeout && crash
 * val sequential: Cause[String] = timeout ++ Cause.fail("retry failed")
 *
 * val failures: List[String] = sequential.failures
 * // List(timeout, retry failed)
 * val defects: List[String] = parallel.defects.map(_.getMessage)
 * // List(crash)
 * val commutes: Boolean = (timeout && crash) == (crash && timeout)
 * // true
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/core/cause/ Cause]]
 */
sealed abstract class Cause[+E] extends Product with Serializable { self =>
  import Cause._

  /**
   * Returns a cause that fails for this cause and the specified cause, in
   * parallel.
   */
  final def &&[E1 >: E](that: Cause[E1]): Cause[E1] = Both(self, that)

  /**
   * Returns a cause that fails for this cause and the specified cause, in
   * sequence. When either cause is `Cause.empty`, returns the other one.
   */
  final def ++[E1 >: E](that: Cause[E1]): Cause[E1] =
    if (self eq Empty) that else if (that eq Empty) self else Then(self, that)

  /**
   * Adds the specified annotations.
   */
  final def annotated(anns: Map[String, String]): Cause[E] =
    if (anns.isEmpty) self else mapAnnotations(_ ++ anns)

  /**
   * Grabs the annotations from the cause.
   */
  def annotations: Map[String, String] =
    self.foldLeft(Map.empty[String, String]) {
      case (z, die @ Die(_, _))             => z ++ die.annotations
      case (z, fail @ Fail(_, _))           => z ++ fail.annotations
      case (z, interrupt @ Interrupt(_, _)) => z ++ interrupt.annotations
      case (z, _)                           => z
    }

  private[zio] final def applyAll(
    trace: StackTrace,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Cause[E] = {
    val isEmptyTrace = trace.isEmpty
    val isEmptySpans = spans.isEmpty
    val isEmptyAnns  = annotations.isEmpty

    if (isEmptyTrace && isEmptySpans && isEmptyAnns) self
    else
      mapAll(
        if (isEmptyTrace) ZIO.identityFn else _ ++ trace,
        if (isEmptySpans) ZIO.identityFn else _ ::: spans,
        if (isEmptyAnns) ZIO.identityFn else _ ++ annotations
      )
  }

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
   * Extracts a list of non-recoverable errors from the `Cause`, in the order
   * they appear from left to right.
   */
  final def defects: List[Throwable] =
    self
      .foldLeft(List.empty[Throwable]) { case (z, Die(v, _)) => v :: z }
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
   * exists, along with its stack trace, which is `StackTrace.none` when the
   * failure was not traced.
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
   * Produces a list of all recoverable errors `E` in the `Cause`, in the order
   * they appear from left to right.
   *
   * {{{
   * val cause: Cause[String] = (Cause.fail("timeout") ++ Cause.fail("retry failed")) && Cause.interrupt(FiberId.None)
   *
   * val errors: List[String] = cause.failures
   * // List(timeout, retry failed)
   * }}}
   */
  final def failures: List[E] =
    self
      .foldLeft(List.empty[E]) { case (z, Fail(v, _)) =>
        v :: z
      }
      .reverse

  /**
   * Keeps the parts of this cause that satisfy `p`. `p` is tested on each
   * `Fail`, `Die` and `Interrupt` node, and then on each `Then`, `Both` and
   * `Stackless` node rebuilt from the parts kept below it. A node for which `p`
   * returns `false` becomes `Cause.empty`, and a `Then` or `Both` node with an
   * empty side becomes its other side without testing `p`. The leaves that are
   * kept lose their log spans and annotations.
   */
  final def filter(p: Cause[E] => Boolean): Cause[E] = self.foldContext(())(Cause.Folder.Filter[E](p))

  /**
   * Returns the result of `f` for the first node of this cause at which `f` is
   * defined, or `None`. The nodes are visited depth-first from left to right,
   * starting with this cause itself, so composite nodes (`Then`, `Both`,
   * `Stackless`) are tested before their children.
   */
  final def find[Z](f: PartialFunction[Cause[E], Z]): Option[Z] = {
    @tailrec
    def loop(cause: Cause[E], stack: List[Cause[E]]): Option[Z] = {
      val out = f.lift(cause)
      if (out.isDefined) out
      else {
        cause match {
          case Then(left, right)   => loop(left, right :: stack)
          case Both(left, right)   => loop(left, right :: stack)
          case Stackless(cause, _) => loop(cause, stack)
          case _ if stack.nonEmpty => loop(stack.head, stack.tail)
          case _                   => None
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
    foldLog[Cause[E2]](
      Empty,
      (e, trace, spans, annotations) => f(e).applyAll(trace, spans, annotations),
      (t, trace, spans, annotations) => Die(t, trace, spans, annotations),
      (fiberId, trace, spans, annotations) => Interrupt(fiberId, trace, spans, annotations)
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  private[zio] final def continue(
    firstCause: Cause[?]
  ): Cause[E] = {
    val trace       = firstCause.trace
    val spans       = firstCause.spans
    val annotations = firstCause.annotations

    val isEmptyTrace = trace.isEmpty
    val isEmptySpans = spans.isEmpty
    val isEmptyAnns  = annotations.isEmpty

    if (isEmptyTrace && isEmptySpans && isEmptyAnns) self
    else
      mapAll(
        if (isEmptyTrace) ZIO.identityFn else trace ++ _,
        if (isEmptySpans) ZIO.identityFn else spans ::: _,
        if (isEmptyAnns) ZIO.identityFn else annotations ++ _
      )
  }

  /**
   * Flattens a nested cause.
   */
  final def flatten[E1](implicit ev: E <:< Cause[E1]): Cause[E1] =
    self.flatMap(ev)

  /**
   * Folds this cause into a `Z` from the leaves up. `empty0`, `failCase0`,
   * `dieCase0` and `interruptCase0` turn each leaf into a `Z`, and `thenCase0`,
   * `bothCase0` and `stacklessCase0` combine the results for the children of
   * each `Then`, `Both` and `Stackless` node.
   *
   * {{{
   * val cause: Cause[String] = Cause.fail("disk full") && Cause.die(new RuntimeException("out of memory"))
   *
   * val leaves: Int =
   *   cause.fold[Int](0, (_, _) => 1, (_, _) => 1, (_, _) => 1)(_ + _, _ + _, (n, _) => n)
   * // 2
   * }}}
   */
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
   * Folds over the cases of this cause with the specified `Folder`, passing
   * `context` to each of its methods.
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
        case (fail @ Fail(e, trace)) :: causes =>
          loop(causes, Right(failCase(context, e, trace, fail.spans, fail.annotations)) :: out)
        case (die @ Die(t, trace)) :: causes =>
          loop(causes, Right(dieCase(context, t, trace, die.spans, die.annotations)) :: out)
        case (interrupt @ Interrupt(fiberId, trace)) :: causes =>
          loop(causes, Right(interruptCase(context, fiberId, trace, interrupt.spans, interrupt.annotations)) :: out)
        case Both(left, right) :: causes =>
          loop(left :: right :: causes, Left(BothCase) :: out)
        case Then(left, right) :: causes =>
          loop(left :: right :: causes, Left(ThenCase) :: out)
        case Stackless(cause, stackless) :: causes =>
          loop(cause :: causes, Left(StacklessCase(stackless)) :: out)
        case _ :: causes =>
          loop(causes, Right(empty(context)) :: out)
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
    if (self eq Empty) empty(context)
    else loop(List(self), List.empty).head
  }

  /**
   * Folds over the cause to statefully compute a value.
   */
  final def foldLeft[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z = {
    @tailrec
    def loop(z0: Z, cause: Cause[E], stack: List[Cause[E]]): Z = {
      val z = f.applyOrElse[(Z, Cause[E]), Z](z0 -> cause, _._1)
      cause match {
        case Then(left, right)   => loop(z, left, right :: stack)
        case Both(left, right)   => loop(z, left, right :: stack)
        case Stackless(cause, _) => loop(z, cause, stack)
        case _ if stack.nonEmpty => loop(z, stack.head, stack.tail)
        case _                   => z
      }
    }

    if (self eq Empty) z
    else loop(z, self, Nil)
  }

  /**
   * Folds this cause into a `Z` like `fold`, but the functions for the `Fail`,
   * `Die` and `Interrupt` leaves also receive the log spans and annotations of
   * the leaf.
   */
  final def foldLog[Z](
    empty0: => Z,
    failCase0: (E, StackTrace, List[LogSpan], Map[String, String]) => Z,
    dieCase0: (Throwable, StackTrace, List[LogSpan], Map[String, String]) => Z,
    interruptCase0: (FiberId, StackTrace, List[LogSpan], Map[String, String]) => Z
  )(thenCase0: (Z, Z) => Z, bothCase0: (Z, Z) => Z, stacklessCase0: (Z, Boolean) => Z): Z =
    foldContext[Unit, E, Z](())(new Cause.Folder[Unit, E, Z] {
      def empty(u: Unit) = empty0
      def failCase(context: Unit, error: E, stackTrace: StackTrace): Z =
        failCase(context, error, stackTrace, List.empty, Map.empty)
      def dieCase(context: Unit, t: Throwable, stackTrace: StackTrace): Z =
        dieCase(context, t, stackTrace, List.empty, Map.empty)
      def interruptCase(context: Unit, fiberId: FiberId, stackTrace: StackTrace): Z =
        interruptCase(context, fiberId, stackTrace, List.empty, Map.empty)
      def bothCase(u: Unit, a: Z, b: Z)            = bothCase0(a, b)
      def thenCase(u: Unit, a: Z, b: Z)            = thenCase0(a, b)
      def stacklessCase(u: Unit, a: Z, b: Boolean) = stacklessCase0(a, b)
      override def failCase(u: Unit, e: E, trace: StackTrace, spans: List[LogSpan], annotations: Map[String, String]) =
        failCase0(e, trace, spans, annotations)
      override def dieCase(
        u: Unit,
        t: Throwable,
        trace: StackTrace,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ) =
        dieCase0(t, trace, spans, annotations)
      override def interruptCase(
        u: Unit,
        fiberId: FiberId,
        trace: StackTrace,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ) =
        interruptCase0(fiberId, trace, spans, annotations)
    })

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

  /**
   * Returns the id of the fiber in the first `Interrupt` of this cause, or
   * `None` when the cause has no interruption.
   */
  final def interruptOption: Option[FiberId] =
    find { case Interrupt(fiberId, _) => fiberId }

  /**
   * Returns `true` when this cause contains at least one `Die` (a defect).
   */
  final def isDie: Boolean =
    dieOption.isDefined

  /**
   * Determines if the `Cause` is empty.
   */
  final def isEmpty: Boolean = {
    @tailrec
    def loop(cause: Cause[E], stack: List[Cause[E]]): Boolean =
      cause match {
        case _: Fail[?]          => false
        case _: Die              => false
        case _: Interrupt        => false
        case Then(left, right)   => loop(left, right :: stack)
        case Both(left, right)   => loop(left, right :: stack)
        case Stackless(cause, _) => loop(cause, stack)
        case _ =>
          stack match {
            case hd :: tl => loop(hd, tl)
            case Nil      => true
          }
      }
    (self eq Empty) || loop(self, Nil)
  }

  /**
   * Returns `true` when this cause contains at least one `Fail` (a typed
   * failure).
   */
  final def isFailure: Boolean =
    failureOption.isDefined

  /**
   * Determines if the `Cause` contains an interruption.
   */
  final def isInterrupted: Boolean =
    find { case Interrupt(_, _) => () }.isDefined

  /**
   * Determines if the `Cause` contains only interruptions and not any `Die` or
   * `Fail` causes. It also returns `true` for `Cause.empty`, which contains
   * neither.
   *
   * {{{
   * val interruption: Cause[Nothing] = Cause.interrupt(FiberId.None)
   *
   * val onlyInterrupted: Boolean = (interruption && interruption).isInterruptedOnly
   * // true
   * val mixed: Boolean = (interruption ++ Cause.fail("timeout")).isInterruptedOnly
   * // false
   * val empty: Boolean = Cause.empty.isInterruptedOnly
   * // true
   * }}}
   */
  final def isInterruptedOnly: Boolean = foldContext(())(Folder.IsInterruptedOnly)

  /**
   * Determines if the `Cause` is traced.
   */
  final def isTraced: Boolean =
    find {
      case Die(_, trace) if !trace.isEmpty       => ()
      case Fail(_, trace) if !trace.isEmpty      => ()
      case Interrupt(_, trace) if !trace.isEmpty => ()
    }.isDefined

  /**
   * Removes the `Fail` and `Interrupt` nodes from this cause and returns `Some`
   * with the `Die` nodes that remain, or `None` when the cause has no `Die`.
   */
  final def keepDefects: Option[Cause[Nothing]] =
    foldLog[Option[Cause[Nothing]]](
      None,
      (_, _, _, _) => None,
      (t, trace, spans, annotations) => Some(Die(t, trace, spans, annotations)),
      (_, _, _, _) => None
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
   * contains a linear sequence of failures. A `Then` node with an `Empty` side
   * linearizes to the empty set.
   */
  def linearize[E1 >: E]: Set[Cause[E1]] =
    foldLog[Set[Cause[E1]]](
      Set.empty,
      (e, trace, spans, annotations) => Set(Fail(e, trace, spans, annotations)),
      (t, trace, spans, annotations) => Set(Die(t, trace, spans, annotations)),
      (fiberId, trace, spans, annotations) => Set(Interrupt(fiberId, trace, spans, annotations))
    )(
      (left, right) => left.flatMap(l => right.map(r => l ++ r)),
      (left, right) => left | right,
      (cause, stackless) => cause.map(Stackless(_, stackless))
    )

  /**
   * Transforms the error type of this cause with the specified function.
   */
  def map[E1](f: E => E1): Cause[E1] =
    flatMap(e => Fail(f(e), StackTrace.none))

  protected def mapAll(
    ft: StackTrace => StackTrace,
    fs: List[LogSpan] => List[LogSpan],
    fa: Map[String, String] => Map[String, String]
  ): Cause[E] =
    foldLog[Cause[E]](
      Empty,
      (e, trace, spans, annotations) => Fail(e, ft(trace), fs(spans), fa(annotations)),
      (t, trace, spans, annotations) => Die(t, ft(trace), fs(spans), fa(annotations)),
      (fiberId, trace, spans, annotations) => Interrupt(fiberId, ft(trace), fs(spans), fa(annotations))
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Transforms the annotations in this cause with the specified function.
   */
  final def mapAnnotations(f: Map[String, String] => Map[String, String]): Cause[E] =
    foldLog[Cause[E]](
      Empty,
      (e, trace, spans, annotations) => Fail(e, trace, spans, f(annotations)),
      (t, trace, spans, annotations) => Die(t, trace, spans, f(annotations)),
      (fiberId, trace, spans, annotations) => Interrupt(fiberId, trace, spans, f(annotations))
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Transforms the spans in this cause with the specified function.
   */
  final def mapSpans(f: List[LogSpan] => List[LogSpan]): Cause[E] =
    foldLog[Cause[E]](
      Empty,
      (e, trace, spans, annotations) => Fail(e, trace, f(spans), annotations),
      (t, trace, spans, annotations) => Die(t, trace, f(spans), annotations),
      (fiberId, trace, spans, annotations) => Interrupt(fiberId, trace, f(spans), annotations)
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Transforms the traces in this cause with the specified function.
   */
  final def mapTrace(f: StackTrace => StackTrace): Cause[E] =
    foldLog[Cause[E]](
      Empty,
      (e, trace, spans, annotations) => Fail(e, f(trace), spans, annotations),
      (t, trace, spans, annotations) => Die(t, f(trace), spans, annotations),
      (fiberId, trace, spans, annotations) => Interrupt(fiberId, f(trace), spans, annotations)
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Returns `true` when this cause contains at least one `Fail`, `Die` or
   * `Interrupt`; the opposite of `isEmpty`.
   */
  final def nonEmpty: Boolean = !isEmpty

  /**
   * Returns a `String` with the cause pretty-printed.
   *
   * The output looks like a Java stack trace. For each parallel branch of the
   * cause (see `linearize`), the first leaf prints as a `className: message`
   * line followed by its `at` trace lines. Each later entry of the branch
   * prints as a `Suppressed:` entry, indented one tab more than the one before;
   * the later entries are the leaves that came after it in sequence and the
   * `getCause` chain of each `Throwable`. Branches that start with a `Die` come
   * first, then those that start with a `Fail`, then those that start with an
   * `Interrupt`. A typed failure that is not a `Throwable` prints the class
   * name and `toString` of its error value, a `Throwable` prints its class name
   * and `getMessage`, and an interruption prints as a
   * `java.lang.InterruptedException`. `Cause.empty` gives an empty string, and
   * so does a `Then` node with an `Empty` side (which `stripFailures` can
   * leave), because `linearize` drops it.
   *
   * {{{
   * val cause: Cause[String] = Cause.fail("disk full") ++ Cause.fail("rollback failed")
   *
   * val text: String = cause.prettyPrint
   * // java.lang.String: disk full
   * //   Suppressed: java.lang.String: rollback failed
   * }}}
   */
  final def prettyPrint: String = {
    val builder = new StringBuilder
    prettyPrintWith(builder.append(_).append('\n'))(Unsafe.unsafe)
    builder.result()
  }

  /** Pretty-prints this cause with the provided `append` function. */
  private[zio] final def prettyPrintWith(append: String => Unit)(implicit unsafe: Unsafe): Unit = {
    import Cause.Unified

    var size = 0
    def appendLine(line: String): Unit =
      if (size <= 1024) {
        append(line)
        size += 1
      }

    def appendCause(cause: Cause[E]): Unit =
      cause.unified.zipWithIndex.foreach {
        case (unified, 0) =>
          appendUnified(0, "", unified)
        case (unified, n) =>
          appendUnified(n, "Suppressed: ", unified)
      }

    def appendUnified(indent: Int, prefix: String, unified: Unified): Unit = {
      val baseIndent  = "\t" * indent
      val traceIndent = baseIndent + "\t"

      appendLine(s"$baseIndent$prefix${unified.className}: ${unified.message}")
      unified.trace.foreach(trace => appendLine(s"${traceIndent}at $trace"))
    }

    val (die, fail, interrupt) =
      self.linearize.foldLeft((Set.empty[Cause[E]], Set.empty[Cause[E]], Set.empty[Cause[E]])) {
        case ((die, fail, interrupt), cause) =>
          cause.find {
            case Die(_, _)       => (die + cause, fail, interrupt)
            case Fail(_, _)      => (die, fail + cause, interrupt)
            case Interrupt(_, _) => (die, fail, interrupt + cause)
          }.getOrElse((die, fail, interrupt))
      }

    die.foreach(appendCause)
    fail.foreach(appendCause)
    interrupt.foreach(appendCause)
  }

  /**
   * Returns the number of `Fail`, `Die` and `Interrupt` leaves in this cause.
   * `Empty` leaves count as zero.
   */
  def size: Int = self.foldContext(())(Cause.Folder.Size)

  /**
   * Adds the specified spans.
   */
  def spanned(spans: List[LogSpan]): Cause[E] =
    if (spans.isEmpty) self else mapSpans(_ ::: spans)

  /**
   * Grabs a complete, linearized list of log spans for the cause. Note: This
   * linearization may be misleading in the presence of parallel errors.
   */
  def spans: List[LogSpan] =
    self
      .foldLeft(List.empty[LogSpan]) {
        case (z, die @ Die(_, _))             => die.spans.reverse_:::(z)
        case (z, fail @ Fail(_, _))           => fail.spans.reverse_:::(z)
        case (z, interrupt @ Interrupt(_, _)) => interrupt.spans.reverse_:::(z)
        case (z, _)                           => z
      }
      .reverse

  /**
   * Squashes a `Cause` down to a single `Throwable`. It returns the first typed
   * failure when the cause has one. Otherwise, when the cause contains an
   * interruption, it returns a new `InterruptedException` whose message is
   * `Interrupted by fibers: ` followed by the interrupting fiber ids (`#1,
   * #2`). Otherwise it returns the first defect, and for a cause with no leaves
   * it returns an `InterruptedException` with no message. An interruption is
   * chosen over a defect when the cause has both.
   *
   * {{{
   * val crash: Cause[Nothing]          = Cause.die(new IllegalStateException("crash"))
   * val cause: Cause[RuntimeException] = Cause.fail(new RuntimeException("timeout")) && crash
   *
   * val failureFirst: String = cause.squash.getMessage
   * // timeout
   * val defectOnly: String = crash.squash.getMessage
   * // crash
   * }}}
   */
  final def squash(implicit ev: E IsSubtypeOfError Throwable): Throwable =
    squashWith(ev)

  /**
   * Squashes a `Cause` down to a single `Throwable` like `squash`, converting a
   * typed failure to a `Throwable` with `f`.
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
   * Squashes a `Cause` down to a single `Throwable`, chosen as `squash` chooses
   * it. In addition, appends a new element to the suppressed exceptions of the
   * `Throwable`, with this `Cause` "pretty printed" (in stackless mode) as the
   * message. When the chosen `Throwable` is one the cause holds, that same
   * object is changed, and each call adds one more suppressed exception to it.
   */
  final def squashTrace(implicit ev: E IsSubtypeOfError Throwable): Throwable =
    squashTraceWith(ev)

  /**
   * Squashes a `Cause` down to a single `Throwable` like `squashTrace`,
   * converting a typed failure to a `Throwable` with `f`. In addition, appends
   * a new element to the suppressed exceptions of the `Throwable`, with this
   * `Cause` "pretty printed" (in stackless mode) as the message.
   */
  final def squashTraceWith(f: E => Throwable): Throwable =
    attachTrace(squashWith(f))

  /**
   * Discards all typed failures kept on this `Cause`.
   */
  final def stripFailures: Cause[Nothing] =
    foldLog[Cause[Nothing]](
      Empty,
      (e, trace, spans, annotations) => Empty,
      (t, trace, spans, annotations) => Die(t, trace, spans, annotations),
      (fiberId, trace, spans, annotations) => Interrupt(fiberId, trace, spans, annotations)
    )(
      (left, right) => Then(left, right),
      (left, right) => Both(left, right),
      (cause, stackless) => Stackless(cause, stackless)
    )

  /**
   * Remove all `Die` causes that the specified partial function is defined at,
   * returning `Some` with the remaining causes or `None` if there are no
   * remaining causes. An `Empty` leaf counts as remaining, so
   * `Cause.empty.stripSomeDefects(pf)` returns `Some(Cause.empty)`; `None`
   * comes back when each leaf of this cause is a removed `Die`.
   */
  final def stripSomeDefects(pf: PartialFunction[Throwable, Any]): Option[Cause[E]] =
    foldLog[Option[Cause[E]]](
      Some(Empty),
      (e, trace, spans, annotations) => Some(Fail(e, trace, spans, annotations)),
      (t, trace, spans, annotations) => if (pf.isDefinedAt(t)) None else Some(Die(t, trace, spans, annotations)),
      (fiberId, trace, spans, annotations) => Some(Interrupt(fiberId, trace, spans, annotations))
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
    if (trace.isEmpty) self else mapTrace(_ ++ trace)

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
            else Chunk.fromArray(throwable.getStackTrace.takeWhile(_.getClassName != "zio.internal.FiberRuntime"))

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
          case value =>
            val className = if (value == null) "" else value.getClass.getName
            val message   = if (value == null) "null" else value.toString
            List(Unified(fail.trace.fiberId, className, message, fail.trace.toJava))
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

    if (self eq Empty) Nil
    else loop(self :: Nil, FiberId.None, false, Nil).reverse
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

/**
 * Constructors for [[Cause]] values, its cases (`Empty`, `Fail`, `Die`,
 * `Interrupt`, `Stackless`, `Then`, `Both`) and the `Folder` used by
 * `Cause#foldContext`.
 */
object Cause extends Serializable {

  /** A `Fail` cause whose error is `()`. */
  val unit: Cause[Unit] = fail(())

  /** The cause with no failures, defects or interruptions (`Empty`). */
  val empty: Cause[Nothing] = Empty

  /**
   * Creates a `Die` cause for the defect `defect`, with the stack trace `trace`
   * (none by default).
   */
  def die(defect: Throwable, trace: StackTrace = StackTrace.none): Cause[Nothing] = Die(defect, trace)

  /**
   * Creates a `Fail` cause for the typed error `error`, with the stack trace
   * `trace` (none by default).
   */
  def fail[E](error: E, trace: StackTrace = StackTrace.none): Cause[E] = Fail(error, trace)

  /**
   * Creates an `Interrupt` cause recording an interruption by the fiber
   * `fiberId`, with the stack trace `trace` (none by default).
   */
  def interrupt(fiberId: FiberId, trace: StackTrace = StackTrace.none): Cause[Nothing] = Interrupt(fiberId, trace)

  /**
   * Wraps `cause` in `Stackless(cause, false)`, so that `prettyPrint` shows the
   * JVM stack frames of the `Throwable`s in it.
   */
  def stack[E](cause: Cause[E]): Cause[E] = Stackless(cause, false)

  /**
   * Wraps `cause` in `Stackless(cause, true)`, so that `prettyPrint` leaves out
   * the JVM stack frames of the `Throwable`s in it and shows only their ZIO
   * traces.
   */
  def stackless[E](cause: Cause[E]): Cause[E] = Stackless(cause, true)

  private[zio] val none: Cause[Option[Nothing]] = fail(None)

  /**
   * A fold over the nodes of a [[Cause]], with a context value of type
   * `Context` passed to each method. `Cause#foldContext` calls one of the leaf
   * methods (`empty`, `failCase`, `dieCase`, `interruptCase`) for each leaf and
   * then combines the results with `thenCase`, `bothCase` and `stacklessCase`
   * from the leaves up.
   */
  trait Folder[-Context, -E, Z] {

    /** Returns the result for an `Empty` leaf. */
    def empty(context: Context): Z

    /** Returns the result for a `Fail` leaf with the error `error`. */
    def failCase(context: Context, error: E, stackTrace: StackTrace): Z

    /** Returns the result for a `Die` leaf with the defect `t`. */
    def dieCase(context: Context, t: Throwable, stackTrace: StackTrace): Z

    /**
     * Returns the result for an `Interrupt` leaf, where `fiberId` is the
     * interrupting fiber.
     */
    def interruptCase(context: Context, fiberId: FiberId, stackTrace: StackTrace): Z

    /** Combines the results for the two sides of a `Both` node. */
    def bothCase(context: Context, left: Z, right: Z): Z

    /** Combines the results for the two sides of a `Then` node. */
    def thenCase(context: Context, left: Z, right: Z): Z

    /**
     * Returns the result for a `Stackless` node from the result for the cause
     * it wraps and its `stackless` flag.
     */
    def stacklessCase(context: Context, value: Z, stackless: Boolean): Z

    /**
     * Returns the result for a `Fail` leaf with its log spans and annotations.
     * By default it ignores them and calls the three-argument `failCase`.
     */
    def failCase(
      context: Context,
      error: E,
      stackTrace: StackTrace,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Z =
      failCase(context, error, stackTrace)

    /**
     * Returns the result for a `Die` leaf with its log spans and annotations.
     * By default it ignores them and calls the three-argument `dieCase`.
     */
    def dieCase(
      context: Context,
      t: Throwable,
      stackTrace: StackTrace,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Z =
      dieCase(context, t, stackTrace)

    /**
     * Returns the result for an `Interrupt` leaf with its log spans and
     * annotations. By default it ignores them and calls the three-argument
     * `interruptCase`.
     */
    def interruptCase(
      context: Context,
      fiberId: FiberId,
      stackTrace: StackTrace,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Z =
      interruptCase(context, fiberId, stackTrace)
  }

  /**
   * The folders that `Cause#size`, `Cause#isInterruptedOnly` and `Cause#filter`
   * use.
   */
  object Folder {

    /**
     * Counts the `Fail`, `Die` and `Interrupt` leaves of a cause; `Cause#size`
     * uses it.
     */
    case object Size extends Folder[Any, Any, Int] {

      /** Counts an `Empty` leaf as 0. */
      def empty(context: Any): Int = 0

      /** Counts a `Fail` leaf as 1. */
      def failCase(context: Any, error: Any, stackTrace: StackTrace): Int = 1

      /** Counts a `Die` leaf as 1. */
      def dieCase(context: Any, t: Throwable, stackTrace: StackTrace): Int = 1

      /** Counts an `Interrupt` leaf as 1. */
      def interruptCase(context: Any, fiberId: FiberId, stackTrace: StackTrace): Int = 1

      /** Adds the counts of the two sides of a `Both` node. */
      def bothCase(context: Any, left: Int, right: Int): Int = left + right

      /** Adds the counts of the two sides of a `Then` node. */
      def thenCase(context: Any, left: Int, right: Int): Int = left + right

      /** Returns the count of the cause that a `Stackless` node wraps. */
      def stacklessCase(context: Any, value: Int, stackless: Boolean): Int = value
    }

    /**
     * Returns `true` when a cause has no `Fail` and no `Die` leaves;
     * `Cause#isInterruptedOnly` uses it.
     */
    case object IsInterruptedOnly extends Folder[Any, Any, Boolean] {

      /** Returns `true` for an `Empty` leaf. */
      def empty(context: Any): Boolean = true

      /** Returns `false` for a `Fail` leaf. */
      def failCase(context: Any, error: Any, stackTrace: StackTrace): Boolean = false

      /** Returns `false` for a `Die` leaf. */
      def dieCase(context: Any, t: Throwable, stackTrace: StackTrace): Boolean = false

      /** Returns `true` for an `Interrupt` leaf. */
      def interruptCase(context: Any, fiberId: FiberId, stackTrace: StackTrace): Boolean = true

      /** Returns `true` when both sides of a `Both` node returned `true`. */
      def bothCase(context: Any, left: Boolean, right: Boolean): Boolean = left && right

      /** Returns `true` when both sides of a `Then` node returned `true`. */
      def thenCase(context: Any, left: Boolean, right: Boolean): Boolean = left && right

      /** Returns the result for the cause that a `Stackless` node wraps. */
      def stacklessCase(context: Any, value: Boolean, stackless: Boolean): Boolean = value
    }

    /**
     * Rebuilds a cause keeping the nodes that satisfy `p`; `Cause#filter` uses
     * it. A node for which `p` returns `false` becomes `Cause.empty`.
     */
    final case class Filter[E](p: Cause[E] => Boolean) extends Folder[Any, E, Cause[E]] {

      /** Returns `Cause.empty`. */
      def empty(context: Any): Cause[E] = Cause.empty

      /**
       * Returns the `Fail` leaf when `p` holds for it, otherwise `Cause.empty`.
       */
      def failCase(context: Any, error: E, stackTrace: StackTrace): Cause[E] = {
        val c = Cause.Fail(error, stackTrace)
        if (p(c))
          c
        else
          Cause.empty
      }

      /**
       * Returns the `Die` leaf when `p` holds for it, otherwise `Cause.empty`.
       */
      def dieCase(context: Any, t: Throwable, stackTrace: StackTrace): Cause[E] = {
        val c = Cause.Die(t, stackTrace)
        if (p(c))
          c
        else
          Cause.empty
      }

      /**
       * Returns the `Interrupt` leaf when `p` holds for it, otherwise
       * `Cause.empty`.
       */
      def interruptCase(context: Any, fiberId: FiberId, stackTrace: StackTrace): Cause[E] = {
        val c = Cause.Interrupt(fiberId, stackTrace)
        if (p(c))
          c
        else
          Cause.empty
      }

      /**
       * Returns the other side when one side is `Cause.empty`; otherwise
       * returns `Both(left, right)` when `p` holds for it, or `Cause.empty`.
       */
      def bothCase(context: Any, left: Cause[E], right: Cause[E]): Cause[E] =
        if (left eq Cause.Empty)
          right
        else if (right eq Cause.empty)
          left
        else {
          val both = Both(left, right)
          if (p(both))
            both
          else
            Cause.Empty
        }

      /**
       * Returns the other side when one side is `Cause.empty`; otherwise
       * returns `Then(left, right)` when `p` holds for it, or `Cause.empty`.
       */
      def thenCase(context: Any, left: Cause[E], right: Cause[E]): Cause[E] =
        if (left eq Cause.Empty)
          right
        else if (right eq Cause.empty)
          left
        else {
          val then_ = Then(left, right)
          if (p(then_))
            then_
          else
            Cause.Empty
        }

      /**
       * Returns `value` when it equals `Cause.empty`; otherwise returns
       * `Stackless(value, stackless)` when `p` holds for it, or `Cause.empty`.
       */
      def stacklessCase(context: Any, value: Cause[E], stackless: Boolean): Cause[E] =
        value match {
          case Cause.Empty => value
          case _ =>
            val stackless2 = Stackless(value, stackless)
            if (p(stackless2))
              stackless2
            else
              Cause.Empty
        }
    }
  }

  /**
   * One failure, defect or interruption of a cause in the shape of a Java
   * exception: the fiber id from its trace, a class name, a message and the
   * stack trace elements. `Cause#unified` produces it.
   */
  final case class Unified(fiberId: FiberId, className: String, message: String, trace: Chunk[StackTraceElement]) {

    /**
     * Returns a `Throwable` whose `getMessage` is `message` and whose
     * `getStackTrace` is `trace`. Its class is not `className`, and it has no
     * cause.
     */
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
    cause.foldLog[Option[Cause[E]]](
      Some(Empty),
      (failureOption, trace, spans, annotations) => failureOption.map(Fail(_, trace, spans, annotations)),
      (t, trace, spans, annotations) => Some(Die(t, trace, spans, annotations)),
      (fiberId, trace, spans, annotations) => Some(Interrupt(fiberId, trace, spans, annotations))
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

  /**
   * A Cause that contains one or more sub-causes
   */
  private[Cause] sealed trait CompositeCause[+E] { self: Cause[E] =>

    /**
     * Stack-safe toString for Cause
     */
    final private def causeToString: String = {
      // result modifier function (Function0[Unit]) or cause (Cause[E]) to visit
      val visitStack = new mutable.Stack[Any]()
      // calculated string results
      val results = new mutable.Stack[String]()

      def twoArgStr(name: String) = {
        val right = results.pop()
        val left  = results.pop()
        results.push(s"$name($left,$right)")
      }

      @tailrec
      def visitRecursive(): String = {
        def visitCause(current: Cause[E]) =
          current match {
            case Both(left, right) =>
              visitStack.push(() => twoArgStr("Both"), right, left)
            case Then(left, right) =>
              visitStack.push(() => twoArgStr("Then"), right, left)
            case Stackless(cause, stackless) =>
              visitStack.push(() => results.push(s"Stackless(${results.pop()},$stackless)"), cause)
            case nonCompositeCause =>
              results.push(nonCompositeCause.toString)
          }

        if (visitStack.isEmpty) {
          results.pop()
        } else {
          visitStack.pop() match {
            case fn: Function0[Unit] =>
              fn()
            case cause: Cause[E] =>
              visitCause(cause)
          }
          visitRecursive()
        }
      }

      visitStack.push(self)
      visitRecursive()
    }

    final override def toString = causeToString
  }

  /**
   * The cause with no failures, defects or interruptions. It is the identity of
   * `++` and `&&`.
   */
  case object Empty extends Cause[Nothing] { self =>
    override def map[E1](f: Nothing => E1): Cause[E1] = self
    override protected def mapAll(
      ft: StackTrace => StackTrace,
      fs: List[LogSpan] => List[LogSpan],
      fa: Map[String, String] => Map[String, String]
    ): Cause[Nothing] = self
  }

  /**
   * A typed failure: the effect failed with the error `value` of its error
   * type, at the stack trace `trace`.
   */
  sealed case class Fail[+E](value: E, override val trace: StackTrace) extends Cause[E] { self =>
    override def annotations: Map[String, String] = Map.empty
    override def spans: List[LogSpan]             = List.empty

    final override def map[E1](f: E => E1): Cause[E1] =
      Fail(f(value), trace, spans, annotations)

    final override protected def mapAll(
      ft: StackTrace => StackTrace,
      fs: List[LogSpan] => List[LogSpan],
      fa: Map[String, String] => Map[String, String]
    ): Cause[E] = Fail(value, ft(trace), fs(spans), fa(annotations))
  }

  /** Constructors for [[Cause.Fail]]. */
  object Fail {

    /** Creates a `Fail` cause that also carries log spans and annotations. */
    def apply[E](value0: E, trace0: StackTrace, spans0: List[LogSpan], annotations0: Map[String, String]): Fail[E] =
      new Fail(value0, trace0) {
        override val annotations: Map[String, String] = annotations0
        override val spans: List[LogSpan]             = spans0
      }
  }

  /**
   * A defect: the effect died with the `Throwable` `value`, which its error
   * type does not describe, at the stack trace `trace`.
   */
  sealed case class Die(value: Throwable, override val trace: StackTrace) extends Cause[Nothing] { self =>
    override def annotations: Map[String, String] = Map.empty
    override def spans: List[LogSpan]             = List.empty

    final override def map[E1](f: Nothing => E1): Cause[E1] = self

    final override protected def mapAll(
      ft: StackTrace => StackTrace,
      fs: List[LogSpan] => List[LogSpan],
      fa: Map[String, String] => Map[String, String]
    ): Cause[Nothing] = Die(value, ft(trace), fs(spans), fa(annotations))
  }

  /** Constructors for [[Cause.Die]]. */
  object Die extends AbstractFunction2[Throwable, StackTrace, Die] {

    /** Creates a `Die` cause that also carries log spans and annotations. */
    def apply(value0: Throwable, trace0: StackTrace, spans0: List[LogSpan], annotations0: Map[String, String]): Die =
      new Die(value0, trace0) {
        override val annotations: Map[String, String] = annotations0
        override val spans: List[LogSpan]             = spans0
      }
  }

  /**
   * An interruption: the fiber `fiberId` interrupted the effect, at the stack
   * trace `trace`.
   */
  sealed case class Interrupt(fiberId: FiberId, override val trace: StackTrace) extends Cause[Nothing] { self =>
    override def annotations: Map[String, String] = Map.empty
    override def spans: List[LogSpan]             = List.empty

    final override def map[E1](f: Nothing => E1): Cause[E1] = self

    final override protected def mapAll(
      ft: StackTrace => StackTrace,
      fs: List[LogSpan] => List[LogSpan],
      fa: Map[String, String] => Map[String, String]
    ): Cause[Nothing] = Interrupt(fiberId, ft(trace), fs(spans), fa(annotations))
  }

  /** Constructors for [[Cause.Interrupt]]. */
  object Interrupt extends AbstractFunction2[FiberId, StackTrace, Interrupt] {

    /**
     * Creates an `Interrupt` cause that also carries log spans and annotations.
     */
    def apply(
      fiberId0: FiberId,
      trace0: StackTrace,
      spans0: List[LogSpan],
      annotations0: Map[String, String]
    ): Interrupt =
      new Interrupt(fiberId0, trace0) {
        override val annotations: Map[String, String] = annotations0
        override val spans: List[LogSpan]             = spans0
      }
  }

  /**
   * Wraps `cause` with a flag for printing: when `stackless` is `true`,
   * `prettyPrint` and `unified` leave out the JVM stack frames of the
   * `Throwable`s in `cause`; when it is `false`, they include them. `unified`
   * keeps the flag for each cause it visits after this node (depth-first, left
   * to right) until it meets another `Stackless`, so in `Then(Stackless(a,
   * true), b)` and `Both(Stackless(a, true), b)` the frames of `b` are left out
   * too. `prettyPrint` handles each parallel branch on its own, so there the
   * flag reaches only the causes that follow in sequence. Equality ignores this
   * wrapper. `Cause.stackless` and `Cause.stack` create it.
   */
  final case class Stackless[+E](cause: Cause[E], stackless: Boolean) extends Cause[E] with CompositeCause[E]

  /**
   * Two causes that happened in sequence: `left` first, then `right`, as when a
   * finalizer fails after the effect it guards. `++` creates it.
   */
  final case class Then[+E](left: Cause[E], right: Cause[E]) extends Cause[E] with CompositeCause[E]

  /**
   * Two causes that happened in parallel, as when both sides of a `zipPar`
   * fail. `&&` creates it.
   */
  final case class Both[+E](left: Cause[E], right: Cause[E]) extends Cause[E] with CompositeCause[E]

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

    (left eq right) || loop(List(left), List(right))
  }

  /**
   * Flattens a cause to a sequence of sets, where each set holds the failures
   * that happened in parallel (typed errors as `Right`, defects as `Left` and
   * interrupting fiber ids) and the sets follow each other in sequence.
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

    if (c eq Empty) Nil
    else loop(List(c), List.empty)
  }

  /**
   * Takes one step in evaluating a cause, returning the set of failures that
   * happen in parallel first (in the encoding of `flatten`) and a list of
   * causes that fail sequentially after those failures.
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
        if (stack.isEmpty) (parallel + Right(e), sequential)
        else loop(stack.head, stack.tail, parallel + Right(e), sequential)
      case Die(t, _) =>
        if (stack.isEmpty) (parallel + Left(t), sequential)
        else loop(stack.head, stack.tail, parallel + Left(t), sequential)
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

    if (c eq Empty) (Set.empty, Nil)
    else loop(c, List.empty, Set.empty, List.empty)
  }

  private case class FiberTrace(trace: String) extends Throwable(null, null, true, false) {
    override final def getMessage: String = trace
  }
}
