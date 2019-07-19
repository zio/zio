/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

sealed trait Cause[+E] extends Product with Serializable { self =>
  import Cause._

  final def &&[E1 >: E](that: Cause[E1]): Cause[E1] = Both(self, that)

  final def ++[E1 >: E](that: Cause[E1]): Cause[E1] = Then(self, that)

  final def defects: List[Throwable] =
    self
      .fold(List.empty[Throwable]) {
        case (z, Die(v)) => v :: z
      }
      .reverse

  final def died: Boolean =
    self match {
      case Die(_)            => true
      case Then(left, right) => left.died || right.died
      case Both(left, right) => left.died || right.died
      case Traced(cause, _)  => cause.died
      case _                 => false
    }

  final def failed: Boolean =
    self match {
      case Fail(_)           => true
      case Then(left, right) => left.failed || right.failed
      case Both(left, right) => left.failed || right.failed
      case Traced(cause, _)  => cause.failed
      case _                 => false
    }

  /**
   * Retrieve the first checked error on the `Left` if available,
   * if there are no checked errors return the rest of the `Cause`
   * that is known to contain only `Die` or `Interrupt` causes.
   * */
  final def failureOrCause: Either[E, Cause[Nothing]] = self.failures.headOption match {
    case Some(error) => Left(error)
    case None        => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
  }

  final def failures[E1 >: E]: List[E1] =
    self
      .fold(List.empty[E1]) {
        case (z, Fail(v)) => v :: z
      }
      .reverse

  final def fold[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z =
    (f.lift(z -> self).getOrElse(z), self) match {
      case (z, Then(left, right)) => right.fold(left.fold(z)(f))(f)
      case (z, Both(left, right)) => right.fold(left.fold(z)(f))(f)
      case (z, Traced(cause, _))  => cause.fold(z)(f)

      case (z, _) => z
    }

  final def interrupted: Boolean =
    self match {
      case Interrupt         => true
      case Then(left, right) => left.interrupted || right.interrupted
      case Both(left, right) => left.interrupted || right.interrupted
      case Traced(cause, _)  => cause.interrupted
      case _                 => false
    }

  final def map[E1](f: E => E1): Cause[E1] = self match {
    case Fail(value) => Fail(f(value))
    case c @ Die(_)  => c
    case Interrupt   => Interrupt

    case Then(left, right)    => Then(left.map(f), right.map(f))
    case Both(left, right)    => Both(left.map(f), right.map(f))
    case Traced(cause, trace) => Traced(cause.map(f), trace)
  }

  final def prettyPrint: String = {
    sealed trait Segment
    sealed trait Step extends Segment

    final case class Sequential(all: List[Step])     extends Segment
    final case class Parallel(all: List[Sequential]) extends Step
    final case class Failure(lines: List[String])    extends Step

    def prefixBlock[A](values: List[String], p1: String, p2: String): List[String] =
      values match {
        case Nil => Nil
        case head :: tail =>
          (p1 + head) :: tail.map(p2 + _)
      }

    def parallelSegments(cause: Cause[Any]): List[Sequential] =
      cause match {
        case Cause.Both(left, right) => parallelSegments(left) ++ parallelSegments(right)
        case _                       => List(causeToSequential(cause))
      }

    def linearSegments(cause: Cause[Any]): List[Step] =
      cause match {
        case Cause.Then(first, second) => linearSegments(first) ++ linearSegments(second)
        case _                         => causeToSequential(cause).all
      }

    // Inline definition of `StringOps.lines` to avoid calling either of `.linesIterator` or `.lines`
    // since both are deprecated in either 2.11 or 2.13 respectively.
    def lines(str: String): List[String] = augmentString(str).linesWithSeparators.map(_.stripLineEnd).toList

    def renderThrowable(e: Throwable): List[String] = {
      import java.io.{ PrintWriter, StringWriter }

      val sw = new StringWriter()
      val pw = new PrintWriter(sw)

      e.printStackTrace(pw)
      lines(sw.toString)
    }

    def renderTrace(maybeTrace: Option[ZTrace]): List[String] =
      maybeTrace.fold("No ZIO Trace available." :: Nil) { trace =>
        "" :: lines(trace.prettyPrint)
      }

    def renderFail(error: List[String], maybeTrace: Option[ZTrace]): Sequential =
      Sequential(
        List(Failure("A checked error was not handled." :: error ++ renderTrace(maybeTrace)))
      )

    def renderFailThrowable(t: Throwable, maybeTrace: Option[ZTrace]): Sequential =
      renderFail(renderThrowable(t), maybeTrace)

    def renderDie(t: Throwable, maybeTrace: Option[ZTrace]): Sequential =
      Sequential(
        List(Failure("An unchecked error was produced." :: renderThrowable(t) ++ renderTrace(maybeTrace)))
      )

    def renderInterrupt(maybeTrace: Option[ZTrace]): Sequential =
      Sequential(
        List(Failure("An unchecked error was produced." :: renderTrace(maybeTrace)))
      )

    def causeToSequential(cause: Cause[Any]): Sequential =
      cause match {
        case Cause.Fail(t: Throwable) =>
          renderFailThrowable(t, None)
        case Cause.Fail(error) =>
          renderFail(lines(error.toString), None)
        case Cause.Die(t) =>
          renderDie(t, None)
        case Cause.Interrupt =>
          renderInterrupt(None)

        case t: Cause.Then[Any] => Sequential(linearSegments(t))
        case b: Cause.Both[Any] => Sequential(List(Parallel(parallelSegments(b))))
        case Traced(c, trace) =>
          c match {
            case Cause.Fail(t: Throwable) =>
              renderFailThrowable(t, Some(trace))
            case Cause.Fail(error) =>
              renderFail(lines(error.toString), Some(trace))
            case Cause.Die(t) =>
              renderDie(t, Some(trace))
            case Cause.Interrupt =>
              renderInterrupt(Some(trace))
            case _ =>
              Sequential(
                Failure("An error was rethrown with a new trace." :: renderTrace(Some(trace))) :: causeToSequential(c).all
              )
          }

      }

    def format(segment: Segment): List[String] =
      segment match {
        case Failure(lines) =>
          prefixBlock(lines, "─", " ")
        case Parallel(all) =>
          List(("══╦" * (all.size - 1)) + "══╗") ++
            all.foldRight[List[String]](Nil) {
              case (current, acc) =>
                prefixBlock(acc, "  ║", "  ║") ++
                  prefixBlock(format(current), "  ", "  ")
            }
        case Sequential(all) =>
          all.flatMap { segment =>
            List("║") ++
              prefixBlock(format(segment), "╠", "║")
          } ++ List("▼")
      }

    val sequence = causeToSequential(this)

    ("Fiber failed." :: {
      sequence match {
        // use simple report for single failures
        case Sequential(List(Failure(cause))) => cause

        case _ => format(sequence).updated(0, "╥")
      }
    }).mkString("\n")
  }

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the
   * "most important" `Throwable`.
   */
  final def squash(implicit ev: E <:< Throwable): Throwable =
    squashWith(ev)

  /**
   * Squashes a `Cause` down to a single `Throwable`, chosen to be the
   * "most important" `Throwable`.
   */
  final def squashWith(f: E => Throwable): Throwable =
    failures.headOption.map(f) orElse
      (if (interrupted) Some(new InterruptedException) else None) orElse
      defects.headOption getOrElse (new InterruptedException)

  /**
   * Remove all `Fail` and `Interrupt` nodes from this `Cause`,
   * return only `Die` cause/finalizer defects.
   */
  final def stripFailures: Option[Cause[Nothing]] =
    self match {
      case Interrupt => None
      case Fail(_)   => None

      case d @ Die(_) => Some(d)

      case Both(l, r) =>
        (l.stripFailures, r.stripFailures) match {
          case (Some(l), Some(r)) => Some(Both(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }

      case Then(l, r) =>
        (l.stripFailures, r.stripFailures) match {
          case (Some(l), Some(r)) => Some(Then(l, r))
          case (Some(l), None)    => Some(l)
          case (None, Some(r))    => Some(r)
          case (None, None)       => None
        }

      case Traced(c, trace) => c.stripFailures.map(Traced(_, trace))
    }

  final def succeeded: Boolean = !failed

  final def traces: List[ZTrace] =
    self
      .fold(List.empty[ZTrace]) {
        case (z, Traced(_, trace)) => trace :: z
      }
      .reverse

}

object Cause extends Serializable {

  final def die(defect: Throwable): Cause[Nothing]               = Die(defect)
  final def fail[E](error: E): Cause[E]                          = Fail(error)
  final val interrupt: Cause[Nothing]                            = Interrupt
  final def traced[E](cause: Cause[E], trace: ZTrace): Traced[E] = Traced(cause, trace)

  final case class Fail[E](value: E) extends Cause[E] {
    override final def equals(that: Any): Boolean = that match {
      case fail: Fail[_]     => value == fail.value
      case traced: Traced[_] => this == traced.cause
      case _                 => false
    }
  }

  final case class Die(value: Throwable) extends Cause[Nothing] {
    override final def equals(that: Any): Boolean = that match {
      case die: Die          => value == die.value
      case traced: Traced[_] => this == traced.cause
      case _                 => false
    }
  }

  case object Interrupt extends Cause[Nothing] {
    override final def equals(that: Any): Boolean =
      (this eq that.asInstanceOf[AnyRef]) || (that match {
        case traced: Traced[_] => this == traced.cause
        case _                 => false
      })
  }

  // Traced is excluded completely from equals & hashCode
  final case class Traced[E](cause: Cause[E], trace: ZTrace) extends Cause[E] {
    override final def hashCode: Int = cause.hashCode()
    override final def equals(obj: Any): Boolean = obj match {
      case traced: Traced[_] => cause == traced.cause
      case _                 => cause == obj
    }
  }

  final case class Then[E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
    override final def equals(that: Any): Boolean = that match {
      case traced: Traced[_] => that.equals(traced.cause)
      case other: Cause[_]   => eq(other) || sym(assoc)(other, self) || sym(dist)(self, other)
      case _                 => false
    }
    override final def hashCode: Int = flatten(self).hashCode

    private def eq(that: Cause[_]): Boolean = (self, that) match {
      case (tl: Then[_], tr: Then[_]) => tl.left == tr.left && tl.right == tr.right
      case _                          => false
    }

    private def assoc(l: Cause[_], r: Cause[_]): Boolean = (l, r) match {
      case (Then(Then(al, bl), cl), Then(ar, Then(br, cr))) => al == ar && bl == br && cl == cr
      case _                                                => false
    }

    private def dist(l: Cause[_], r: Cause[_]): Boolean = (l, r) match {
      case (Then(al, Both(bl, cl)), Both(Then(ar1, br), Then(ar2, cr)))
          if ar1 == ar2 && al == ar1 && bl == br && cl == cr =>
        true
      case (Then(Both(al, bl), cl), Both(Then(ar, cr1), Then(br, cr2)))
          if cr1 == cr2 && al == ar && bl == br && cl == cr1 =>
        true
      case _ => false
    }
  }

  final case class Both[E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
    override final def equals(that: Any): Boolean = that match {
      case traced: Traced[_] => that.equals(traced.cause)
      case other: Cause[_]   => eq(other) || sym(assoc)(self, other) || comm(other)
      case _                 => false
    }
    override final def hashCode: Int = flatten(self).hashCode

    private def eq(that: Cause[_]) = (self, that) match {
      case (bl: Both[_], br: Both[_]) => bl.left == br.left && bl.right == br.right
      case _                          => false
    }
    private def assoc(l: Cause[_], r: Cause[_]): Boolean = (l, r) match {
      case (Both(Both(al, bl), cl), Both(ar, Both(br, cr))) => al == ar && bl == br && cl == cr
      case _                                                => false
    }
    private def comm(that: Cause[_]): Boolean = (self, that) match {
      case (Both(al, bl), Both(ar, br)) => al == br && bl == ar
      case _                            => false
    }
  }

  private[Cause] def sym(f: (Cause[_], Cause[_]) => Boolean): (Cause[_], Cause[_]) => Boolean =
    (l, r) => f(l, r) || f(r, l)

  private[Cause] def flatten(c: Cause[_]): Set[Cause[_]] = c match {
    case Then(left, right) => flatten(left) ++ flatten(right)
    case Both(left, right) => flatten(left) ++ flatten(right)
    case Traced(cause, _)  => flatten(cause)
    case o                 => Set(o)
  }
}
