/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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
   * Returns a cause that fails for this cause and the specified cause, in parallel.
   */
  final def &&[E1 >: E](that: Cause[E1]): Cause[E1] = Both(self, that)

  /**
   * Returns a cause that fails for this cause and the specified cause, in sequence.
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
      .foldLeft(List.empty[Throwable]) { case (z, Die(v)) =>
        v :: z
      }
      .reverse

  @deprecated("use isDie", "2.0.0")
  final def died: Boolean =
    isDie

  /**
   * Returns the `Throwable` associated with the first `Die` in this `Cause` if
   * one exists.
   */
  final def dieOption: Option[Throwable] =
    find { case Die(t) => t }

  @deprecated("use isFailure", "2.0.0")
  final def failed: Boolean =
    isFailure

  /**
   * Returns the `E` associated with the first `Fail` in this `Cause` if one
   * exists.
   */
  def failureOption: Option[E] =
    find { case Fail(e) => e }

  /**
   * Returns the `E` associated with the first `Fail` in this `Cause` if one
   * exists, along with its (optional) trace.
   */
  def failureTraceOption: Option[(E, Option[ZTrace])] =
    find {
      case Traced(Fail(e), trace) => (e, Some(trace))
      case Fail(e)                => (e, None)
    }

  /**
   * Retrieve the first checked error on the `Left` if available,
   * if there are no checked errors return the rest of the `Cause`
   * that is known to contain only `Die` or `Interrupt` causes.
   */
  final def failureOrCause: Either[E, Cause[Nothing]] = failureOption match {
    case Some(error) => Left(error)
    case None        => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
  }

  /**
   * Retrieve the first checked error and its trace on the `Left` if available,
   * if there are no checked errors return the rest of the `Cause`
   * that is known to contain only `Die` or `Interrupt` causes.
   */
  final def failureTraceOrCause: Either[(E, Option[ZTrace]), Cause[Nothing]] = failureTraceOption match {
    case Some(errorAndTrace) => Left(errorAndTrace)
    case None                => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
  }

  /**
   * Produces a list of all recoverable errors `E` in the `Cause`.
   */
  final def failures: List[E] =
    self
      .foldLeft(List.empty[E]) { case (z, Fail(v)) =>
        v :: z
      }
      .reverse

  final def flatMap[E1](f: E => Cause[E1]): Cause[E1] = self match {
    case Empty                => Empty
    case Fail(value)          => f(value)
    case c @ Die(_)           => c
    case Interrupt(id)        => Interrupt(id)
    case Then(left, right)    => Then(left.flatMap(f), right.flatMap(f))
    case Both(left, right)    => Both(left.flatMap(f), right.flatMap(f))
    case Traced(cause, trace) => Traced(cause.flatMap(f), trace)
    case Meta(cause, data)    => Meta(cause.flatMap(f), data)
  }

  final def flatten[E1](implicit ev: E <:< Cause[E1]): Cause[E1] =
    self flatMap (e => e)

  /**
   * Determines if the `Cause` contains an interruption.
   */
  @deprecated("use isInterrupted", "2.0.0")
  final def interrupted: Boolean =
    isInterrupted

  /**
   * Determines if the `Cause` contains only interruptions and not any `Die` or
   * `Fail` causes.
   */
  @deprecated("use isInterruptedOnly", "2.0.0")
  final def interruptedOnly: Boolean =
    isInterruptedOnly

  /**
   * Returns a set of interruptors, fibers that interrupted the fiber described
   * by this `Cause`.
   */
  final def interruptors: Set[FiberId] =
    foldLeft[Set[FiberId]](Set()) { case (acc, Interrupt(fiberId)) =>
      acc + fiberId
    }

  final def isDie: Boolean =
    dieOption.isDefined

  /**
   * Determines if the `Cause` is empty.
   */
  final def isEmpty: Boolean =
    (self eq Empty) || foldLeft(true) {
      case (acc, _: Empty.type) => acc
      case (_, Die(_))          => false
      case (_, Fail(_))         => false
      case (_, Interrupt(_))    => false
    }

  final def isFailure: Boolean =
    failureOption.isDefined

  /**
   * Determines if the `Cause` contains an interruption.
   */
  final def isInterrupted: Boolean =
    find { case Interrupt(_) => () }.isDefined

  /**
   * Determines if the `Cause` contains only interruptions and not any `Die` or
   * `Fail` causes.
   */
  final def isInterruptedOnly: Boolean =
    find {
      case Die(_)  => false
      case Fail(_) => false
    }.getOrElse(true)

  final def fold[Z](
    empty: => Z,
    failCase: E => Z,
    dieCase: Throwable => Z,
    interruptCase: FiberId => Z
  )(thenCase: (Z, Z) => Z, bothCase: (Z, Z) => Z, tracedCase: (Z, ZTrace) => Z): Z =
    self match {
      case Empty => empty
      case Fail(value) =>
        failCase(value)
      case Die(value) =>
        dieCase(value)
      case Interrupt(fiberId) =>
        interruptCase(fiberId)
      case Then(left, right) =>
        thenCase(
          left.fold(empty, failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase),
          right.fold(empty, failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase)
        )
      case Both(left, right) =>
        bothCase(
          left.fold(empty, failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase),
          right.fold(empty, failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase)
        )
      case Traced(cause, trace) =>
        tracedCase(
          cause.fold(empty, failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase),
          trace
        )
      case Meta(cause, _) =>
        cause.fold(empty, failCase, dieCase, interruptCase)(thenCase, bothCase, tracedCase)
    }

  /**
   * Remove all `Fail` and `Interrupt` nodes from this `Cause`,
   * return only `Die` cause/finalizer defects.
   */
  final def keepDefects: Option[Cause[Nothing]] =
    self match {
      case Empty        => None
      case Interrupt(_) => None
      case Fail(_)      => None
      case d @ Die(_)   => Some(d)

      case Both(l, r) =>
        (l.keepDefects, r.keepDefects) match {
          case (Some(l), Some(r)) => Some(Both(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }

      case Then(l, r) =>
        (l.keepDefects, r.keepDefects) match {
          case (Some(l), Some(r)) => Some(Then(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }

      case Traced(c, trace) => c.keepDefects.map(Traced(_, trace))
      case Meta(c, data)    => c.keepDefects.map(Meta(_, data))
    }

  def linearize[E1 >: E]: Set[Cause[E1]] = {
    def loop[E](cause: Cause[E]): Set[Cause[E]] =
      cause match {
        case Empty => Set()

        case Both(left, right) => loop(left) ++ loop(right)

        case Meta(cause, data) => loop(cause).map(Meta(_, data))

        case Traced(cause, trace) => loop(cause).map(Traced(_, trace))

        case Then(left, right) =>
          for {
            l <- loop(left)
            r <- loop(right)
          } yield l ++ r

        case cause @ Fail(_)      => Set(cause)
        case cause @ Die(_)       => Set(cause)
        case cause @ Interrupt(_) => Set(cause)
      }

    loop(self)
  }

  final def map[E1](f: E => E1): Cause[E1] =
    flatMap(e => Fail(f(e)))

  /**
   * Returns a `Cause` that has been stripped of all tracing information.
   */
  final def untraced: Cause[E] =
    self match {
      case Traced(cause, _)  => cause.untraced
      case Meta(cause, data) => Meta(cause.untraced, data)

      case Empty            => Empty
      case c @ Fail(_)      => c
      case c @ Die(_)       => c
      case c @ Interrupt(_) => c

      case Then(left, right) => Then(left.untraced, right.untraced)
      case Both(left, right) => Both(left.untraced, right.untraced)
    }

  /**
   * Returns a `String` with the cause pretty-printed.
   */
  final def prettyPrint: String = {
    final case class Unified(fiberId: FiberId, className: String, message: String, trace: Chunk[StackTraceElement])

    def unify(cause: Cause[E]): List[Unified] = {
      def loop(
        causes: List[Cause[E]],
        fiberId: FiberId,
        accum: Chunk[StackTraceElement],
        stackless: Boolean,
        result: List[Unified]
      ): List[Unified] = {
        def unifyFail(error: E): Unified =
          Unified(fiberId, error.getClass.getName(), error.toString(), accum)

        def unifyDie(error: Throwable): Unified = {
          val extra =
            if (stackless) Chunk.empty else Chunk.fromArray(error.getStackTrace())

          Unified(fiberId, error.getClass.getName(), error.getMessage(), extra ++ accum)
        }

        def unifyInterrupt(fiberId: FiberId): Unified = {
          val message = s"Interrupted by thread \"${fiberId}\""

          Unified(fiberId, classOf[InterruptedException].getName(), message, accum)
        }

        causes match {
          case Nil => result

          case Empty :: more             => loop(more, fiberId, accum, stackless, result)
          case Both(left, right) :: more => loop(left :: right :: more, fiberId, accum, stackless, result)
          case Meta(cause, data) :: more => loop(cause :: more, fiberId, accum, data.stackless, result)
          case Traced(cause, trace) :: more =>
            loop(cause :: more, trace.fiberId, accum ++ trace.toJava, stackless, result)
          case Then(left, right) :: more => loop(left :: right :: more, fiberId, accum, stackless, result)
          case (cause @ Fail(_)) :: more => loop(more, fiberId, accum, stackless, unifyFail(cause.value) :: result)
          case (cause @ Die(_)) :: more  => loop(more, fiberId, accum, stackless, unifyDie(cause.value) :: result)
          case (cause @ Interrupt(_)) :: more =>
            loop(more, fiberId, accum, stackless, unifyInterrupt(cause.fiberId) :: result)
        }
      }

      loop(cause :: Nil, FiberId.None, Chunk.empty, false, Nil).reverse
    }

    def renderCause(cause: Cause[E]): String = {
      def renderUnified(indent: Int, prefix: String, unified: Unified) = {
        val baseIndent  = "\t" * indent
        val traceIndent = baseIndent + "\t"

        s"${baseIndent}${prefix}${unified.className}: ${unified.message}\n" +
          unified.trace.map(trace => s"${traceIndent}at ${trace}").mkString("\n")
      }

      unify(cause).zipWithIndex.map {
        case (unified, 0) =>
          renderUnified(0, s"Exception in thread \"zio-fiber-${unified.fiberId.seqNumber}\" ", unified)
        case (unified, n) => renderUnified(n, s"Suppressed: ", unified)
      }.mkString("\n")
    }

    self.linearize.map(renderCause).mkString("\n")
  }

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the
   * "most important" `Throwable`.
   */
  final def squash(implicit ev: E IsSubtypeOfError Throwable): Throwable =
    squashWith(ev)

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the
   * "most important" `Throwable`.
   */
  final def squashWith(f: E => Throwable): Throwable =
    failureOption.map(f) orElse
      (if (isInterrupted)
         Some(
           new InterruptedException(
             "Interrupted by fibers: " + interruptors.map(_.seqNumber.toString()).map("#" + _).mkString(", ")
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
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the
   * "most important" `Throwable`.
   * In addition, appends a new element the to `Throwable`s "caused by" chain,
   * with this `Cause` "pretty printed" (in stackless mode) as the message.
   */
  final def squashTraceWith(f: E => Throwable): Throwable =
    attachTrace(squashWith(f))

  /**
   * Discards all typed failures kept on this `Cause`.
   */
  final def stripFailures: Cause[Nothing] =
    self match {
      case Empty            => Empty
      case c @ Interrupt(_) => c
      case Fail(_)          => Empty
      case c @ Die(_)       => c

      case Both(l, r) => l.stripFailures && r.stripFailures
      case Then(l, r) => l.stripFailures ++ r.stripFailures

      case Traced(c, trace) => Traced(c.stripFailures, trace)
      case Meta(c, data)    => Meta(c.stripFailures, data)
    }

  /**
   * Remove all `Die` causes that the specified partial function is defined at,
   * returning `Some` with the remaining causes or `None` if there are no
   * remaining causes.
   */
  final def stripSomeDefects(pf: PartialFunction[Throwable, Any]): Option[Cause[E]] =
    self match {
      case Empty              => None
      case Interrupt(fiberId) => Some(Interrupt(fiberId))
      case Fail(e)            => Some(Fail(e))
      case Die(t)             => if (pf.isDefinedAt(t)) None else Some(Die(t))
      case Both(l, r) =>
        (l.stripSomeDefects(pf), r.stripSomeDefects(pf)) match {
          case (Some(l), Some(r)) => Some(Both(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }
      case Then(l, r) =>
        (l.stripSomeDefects(pf), r.stripSomeDefects(pf)) match {
          case (Some(l), Some(r)) => Some(Then(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }
      case Traced(c, trace) => c.stripSomeDefects(pf).map(Traced(_, trace))
      case Meta(c, data)    => c.stripSomeDefects(pf).map(Meta(_, data))
    }

  /**
   * Grabs a list of execution traces from the cause.
   */
  final def traces: List[ZTrace] =
    self
      .foldLeft(List.empty[ZTrace]) { case (z, Traced(_, trace)) =>
        trace :: z
      }
      .reverse

  final def find[Z](f: PartialFunction[Cause[E], Z]): Option[Z] = {
    @tailrec
    def loop(cause: Cause[E], stack: List[Cause[E]]): Option[Z] =
      f.lift(cause) match {
        case Some(z) => Some(z)
        case None =>
          cause match {
            case Then(left, right) => loop(left, right :: stack)
            case Both(left, right) => loop(left, right :: stack)
            case Traced(cause, _)  => loop(cause, stack)
            case Meta(cause, _)    => loop(cause, stack)
            case _ =>
              stack match {
                case hd :: tl => loop(hd, tl)
                case Nil      => None
              }
          }
      }
    loop(self, Nil)
  }

  final def foldLeft[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z = {
    @tailrec
    def loop(z: Z, cause: Cause[E], stack: List[Cause[E]]): Z =
      (f.applyOrElse[(Z, Cause[E]), Z](z -> cause, _ => z), cause) match {
        case (z, Then(left, right)) => loop(z, left, right :: stack)
        case (z, Both(left, right)) => loop(z, left, right :: stack)
        case (z, Traced(cause, _))  => loop(z, cause, stack)
        case (z, Meta(cause, _))    => loop(z, cause, stack)
        case (z, _) =>
          stack match {
            case hd :: tl => loop(z, hd, tl)
            case Nil      => z
          }
      }
    loop(z, self, Nil)
  }

  private def attachTrace(e: Throwable): Throwable = {
    val trace = Cause.FiberTrace(Cause.stackless(this).prettyPrint)
    e.addSuppressed(trace)
    e
  }
}

object Cause extends Serializable {
  val empty: Cause[Nothing]                               = Empty
  def die(defect: Throwable): Cause[Nothing]              = Die(defect)
  def fail[E](error: E): Cause[E]                         = Fail(error)
  def interrupt(fiberId: FiberId): Cause[Nothing]         = Interrupt(fiberId)
  def stack[E](cause: Cause[E]): Cause[E]                 = Meta(cause, Data(false))
  def stackless[E](cause: Cause[E]): Cause[E]             = Meta(cause, Data(true))
  def traced[E](cause: Cause[E], trace: ZTrace): Cause[E] = Traced(cause, trace)

  /**
   * Converts the specified `Cause[Option[E]]` to an `Option[Cause[E]]` by
   * recursively stripping out any failures with the error `None`.
   */
  def flipCauseOption[E](c: Cause[Option[E]]): Option[Cause[E]] =
    c match {
      case Empty                => Some(Empty)
      case Traced(cause, trace) => flipCauseOption(cause).map(Traced(_, trace))
      case Meta(cause, data)    => flipCauseOption(cause).map(Meta(_, data))
      case Interrupt(id)        => Some(Interrupt(id))
      case d @ Die(_)           => Some(d)
      case Fail(Some(e))        => Some(Fail(e))
      case Fail(None)           => None
      case Then(left, right) =>
        (flipCauseOption(left), flipCauseOption(right)) match {
          case (Some(cl), Some(cr)) => Some(Then(cl, cr))
          case (None, Some(cr))     => Some(cr)
          case (Some(cl), None)     => Some(cl)
          case (None, None)         => None
        }

      case Both(left, right) =>
        (flipCauseOption(left), flipCauseOption(right)) match {
          case (Some(cl), Some(cr)) => Some(Both(cl, cr))
          case (None, Some(cr))     => Some(cr)
          case (Some(cl), None)     => Some(cl)
          case (None, None)         => None
        }
    }

  /**
   * Converts the specified `Cause[Either[E, A]]` to an `Either[Cause[E], A]` by
   * recursively stripping out any failures with the error `None`.
   */
  def flipCauseEither[E, A](c: Cause[Either[E, A]]): Either[Cause[E], A] =
    c match {
      case Empty                => Left(Empty)
      case Traced(cause, trace) => flipCauseEither(cause).left.map(Traced(_, trace))
      case Meta(cause, data)    => flipCauseEither(cause).left.map(Meta(_, data))
      case Interrupt(id)        => Left(Interrupt(id))
      case d @ Die(_)           => Left(d)
      case Fail(Left(e))        => Left(Fail(e))
      case Fail(Right(a))       => Right(a)
      case Then(left, right) =>
        (flipCauseEither(left), flipCauseEither(right)) match {
          case (Left(cl), Left(cr)) => Left(Then(cl, cr))
          case (Right(a), _)        => Right(a)
          case (_, Right(a))        => Right(a)
        }

      case Both(left, right) =>
        (flipCauseEither(left), flipCauseEither(right)) match {
          case (Left(cl), Left(cr)) => Left(Both(cl, cr))
          case (Right(a), _)        => Right(a)
          case (_, Right(a))        => Right(a)
        }
    }

  case object Empty extends Cause[Nothing] {
    override def equals(that: Any): Boolean = that match {
      case _: Empty.type     => true
      case Then(left, right) => this == left && this == right
      case Both(left, right) => this == left && this == right
      case traced: Traced[_] => this == traced.cause
      case meta: Meta[_]     => this == meta.cause
      case _                 => false
    }
  }

  final case class Fail[+E](value: E) extends Cause[E] {
    override def equals(that: Any): Boolean = that match {
      case fail: Fail[_]     => value == fail.value
      case c @ Then(_, _)    => sym(empty)(this, c)
      case c @ Both(_, _)    => sym(empty)(this, c)
      case traced: Traced[_] => this == traced.cause
      case meta: Meta[_]     => this == meta.cause
      case _                 => false
    }
  }

  final case class Die(value: Throwable) extends Cause[Nothing] {
    override def equals(that: Any): Boolean = that match {
      case die: Die          => value == die.value
      case c @ Then(_, _)    => sym(empty)(this, c)
      case c @ Both(_, _)    => sym(empty)(this, c)
      case traced: Traced[_] => this == traced.cause
      case meta: Meta[_]     => this == meta.cause
      case _                 => false
    }
  }

  final case class Interrupt(fiberId: FiberId) extends Cause[Nothing] {
    override def equals(that: Any): Boolean =
      (this eq that.asInstanceOf[AnyRef]) || (that match {
        case interrupt: Interrupt => fiberId == interrupt.fiberId
        case c @ Then(_, _)       => sym(empty)(this, c)
        case c @ Both(_, _)       => sym(empty)(this, c)
        case traced: Traced[_]    => this == traced.cause
        case meta: Meta[_]        => this == meta.cause
        case _                    => false
      })
  }

  // Traced is excluded completely from equals & hashCode
  final case class Traced[+E](cause: Cause[E], trace: ZTrace) extends Cause[E] {
    override def hashCode: Int = cause.hashCode()
    override def equals(obj: Any): Boolean = obj match {
      case traced: Traced[_] => cause == traced.cause
      case meta: Meta[_]     => cause == meta.cause
      case _                 => cause == obj
    }
  }

  // Meta is excluded completely from equals & hashCode
  final case class Meta[+E](cause: Cause[E], data: Data) extends Cause[E] {
    override def hashCode: Int = cause.hashCode
    override def equals(obj: Any): Boolean = obj match {
      case traced: Traced[_] => cause == traced.cause
      case meta: Meta[_]     => cause == meta.cause
      case _                 => cause == obj
    }
  }

  final case class Then[+E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
    override def equals(that: Any): Boolean = that match {
      case traced: Traced[_] => self.equals(traced.cause)
      case meta: Meta[_]     => self.equals(meta.cause)
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
      case traced: Traced[_] => self.equals(traced.cause)
      case meta: Meta[_]     => self.equals(meta.cause)
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

  final case class Data(stackless: Boolean) // TODO: Rename to Stackless and make it unary operator

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
   * Flattens a cause to a sequence of sets of causes, where each set
   * represents causes that fail in parallel and sequential sets represent
   * causes that fail after each other.
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
          case Traced(c, _) => loop(Then(c, right), stack, parallel, sequential)
          case Meta(c, _)   => loop(Then(c, right), stack, parallel, sequential)
          case o            => loop(o, stack, parallel, right :: sequential)
        }
      case Both(left, right) => loop(left, right :: stack, parallel, sequential)
      case Traced(cause, _)  => loop(cause, stack, parallel, sequential)
      case Meta(cause, _)    => loop(cause, stack, parallel, sequential)
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
