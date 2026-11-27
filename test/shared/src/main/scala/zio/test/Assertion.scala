package zio.test

import zio.internal.ansi.AnsiStringOps
import zio.internal.stacktracer.SourceLocation
import zio.test.Assertion.Arguments
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.internal.SmartAssertions
import zio.test.{ErrorMessage => M}
import zio.{Cause, Exit, Trace, ZIO}

import scala.reflect.ClassTag
import scala.util.Try

/**
 * An `Assertion[A]` is a check on a value of type `A` that reports why it
 * failed. The companion object holds the built-in assertions (`equalTo`,
 * `contains`, `isSome`, `fails`, ...). Combine assertions with `&&`, `||` and
 * `!`, check a value with `assert(value)(assertion)`, and check the result of
 * an effect with `assertZIO(effect)(assertion)`.
 *
 * `assertTrue` with ordinary Scala expressions is the shorter way to write most
 * checks; an `Assertion` is a value, so it can be named, reused and combined.
 *
 * {{{
 * import zio.test.Assertion._
 *
 * val inRange: Assertion[Int] = isGreaterThanEqualTo(0) && isLessThan(100)
 *
 * val result: TestResult = assert(42)(inRange)
 * // result.isSuccess == true
 * }}}
 *
 * @param arrow
 *   the [[TestArrow]] that runs the check
 *
 * @see
 *   [[https://zio.dev/reference/test/assertions/ Assertions]]
 */
final case class Assertion[-A](arrow: TestArrow[A, Boolean]) { self =>

  /**
   * Returns a description of this assertion, built from the names and arguments
   * of the assertions it combines.
   */
  def render: String = arrow.render

  /**
   * Combines this assertion with `that` by logical and: the result succeeds
   * when both succeed. Both assertions run on the value.
   */
  def &&[A1 <: A](that: Assertion[A1]): Assertion[A1] =
    Assertion(arrow && that.arrow)

  /**
   * Combines this assertion with `that` by logical or: the result succeeds when
   * at least one of them succeeds. Both assertions run on the value, and the
   * result fails when either of them throws an exception, even if the other one
   * succeeds.
   */
  def ||[A1 <: A](that: Assertion[A1]): Assertion[A1] =
    Assertion(arrow || that.arrow)

  /**
   * Negates this assertion by logical not: the result succeeds when this
   * assertion is not satisfied. An assertion that throws an exception still
   * fails after negation. The same as `negate`.
   */
  def unary_! : Assertion[A] =
    Assertion(!arrow)

  /**
   * Negates this assertion: the result succeeds when this assertion is not
   * satisfied. The same as `!`.
   */
  def negate: Assertion[A] =
    Assertion(!arrow)

  /**
   * Returns `true` when `value` satisfies this assertion.
   */
  def test(value: A)(implicit sourceLocation: SourceLocation): Boolean =
    TestArrow.run(arrow.withLocation, Right(value)).isSuccess

  // TODO: IMPLEMENT LABELING
  /**
   * Returns this assertion with `message` as its label. The label is part of
   * the failure report; it does not change whether the assertion succeeds.
   */
  def label(message: String): Assertion[A] =
    Assertion(self.arrow.label(message))

  /**
   * Labels this assertion with `message`. The same as `label`.
   */
  def ??(message: String): Assertion[A] =
    self.label(message)

  /**
   * Checks `value` against this assertion and returns the [[TestResult]].
   * `value` is evaluated inside the check, so an exception it throws becomes
   * part of the result (see `throws`) instead of escaping.
   */
  def run(value: => A)(implicit sourceLocation: SourceLocation): TestResult =
    Assertion.smartAssert(value)(self)

  private[test] def withCode(code: String, arguments: Arguments*): Assertion[A] =
    Assertion(arrow.withCode(code, arguments: _*))
}

/**
 * The built-in assertions. Import `zio.test.Assertion._` to use them without
 * the `Assertion.` prefix.
 *
 * @see
 *   [[https://zio.dev/reference/test/assertions/built-in-assertions/ Built-in Assertions]]
 */
object Assertion extends AssertionVariants {
  import Arguments._

  private[test] def smartAssert[A](
    expr: => A,
    codeString: Option[String] = None,
    assertionString: Option[String] = None
  )(
    assertion: Assertion[A]
  )(implicit sourceLocation: SourceLocation): TestResult = {
    val completeString =
      codeString.flatMap(code =>
        assertionString.map { assertion =>
          val code0      = code.blue
          val assertion0 = assertion.cyan
          new StringBuilder(17 + code0.length + assertion0.length)
            .append(code0)
            .append(" did not satisfy ")
            .append(assertion0)
            .result()
        }
      )
    TestResult(
      (TestArrow.succeed(expr).withCode(codeString.getOrElse("input")) >>> assertion.arrow).withLocation
        .withCompleteCode(completeString.getOrElse("<CODE>"))
    )
  }

  private[test] def smartAssertZIO[R, E, A](assertion: Assertion[A])(
    effect: ZIO[R, E, A]
  )(implicit trace: Trace, sourceLocation: SourceLocation): ZIO[R, E, TestResult] =
    effect.map(smartAssert(_)(assertion))

  @deprecated("use smartAssertZIO variant without the by-name parameter", "2.1.23")
  private[Assertion] def smartAssertZIO[R, E, A](
    expr: => ZIO[R, E, A]
  )(assertion: Assertion[A])(implicit trace: Trace, sourceLocation: SourceLocation): ZIO[R, E, TestResult] =
    smartAssertZIO(assertion)(expr)

  /**
   * Makes a new assertion named `name` from a predicate: it succeeds when `run`
   * returns `true` for the value.
   *
   * {{{
   * val isEven: Assertion[Int] = Assertion.assertion[Int]("isEven")(_ % 2 == 0)
   *
   * val result: TestResult = assert(4)(isEven)
   * // result.isSuccess == true
   * }}}
   */
  def assertion[A](name: String)(run: (=> A) => Boolean): Assertion[A] =
    Assertion(
      TestArrow
        .make[A, Boolean] { a =>
          val result = run(a)
          TestTrace.boolean(result) {
            M.text("Custom Assertion") + M.value(name) + M.choice("succeeded", "failed")
          }
        }
        .withCode(name)
    )

  /**
   * Makes a new assertion named `name` that extracts a value with `get` and
   * checks it with `assertion`. It fails when `get` returns `None`.
   */
  def assertionRec[A, B](name: String)(assertion: Assertion[B])(get: A => Option[B]): Assertion[A] =
    Assertion(
      TestArrow
        .make[A, B] { a =>
          get(a).fold[TestTrace[B]](
            TestTrace.fail(M.text("Custom Assertion") + M.value(name) + M.choice("succeeded", "failed"))
          ) { b =>
            TestTrace.succeed(b)
          }
        }
        .withCode(name) >>> assertion.arrow
    )

  // TODO: Extend Syntax
  // def assertion[A](expr: A => Boolean): Assertion[A]
  // val hasLengthGreaterThan10 = assertion[String](_.length > 10)
  // assertTrue(hasLengthGreaterThan10("hello"))

  // # ASSERTIONS

  /**
   * Makes a new assertion that succeeds for any value, `null` included.
   */
  val anything: Assertion[Any] =
    Assertion[Any] {
      SmartAssertions.anything.withCode("anything")
    }

  /**
   * Makes a new assertion that requires a sequence to have an element at index
   * `pos` that satisfies `assertion`. It fails when `pos` is negative or not
   * less than the size of the sequence.
   */
  def hasAt[A](pos: Int)(assertion: Assertion[A]): Assertion[Seq[A]] =
    Assertion[Seq[A]](
      SmartAssertions.hasAt(pos).withCode("hasAt", valueArgument(pos)) >>>
        assertion.arrow
    )

  /**
   * Makes a new assertion that requires a numeric value to be within
   * `tolerance` of `reference`, both bounds included: the value must be at
   * least `reference - tolerance` and at most `reference + tolerance`.
   *
   * {{{
   * val result: TestResult = assert(0.1 + 0.2)(Assertion.approximatelyEquals(0.3, 0.0001))
   * // result.isSuccess == true
   * }}}
   */
  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): Assertion[A] =
    Assertion[A](
      SmartAssertions
        .approximatelyEquals(reference, tolerance)
        .withCode("approximatelyEquals", valueArgument(reference), valueArgument(tolerance, name = Some("tolerance")))
    )

  /**
   * Makes a new assertion that requires an Iterable to contain the specified
   * element, compared with `==`. See [[Assertion.exists]] to require an element
   * that satisfies an assertion.
   */
  def contains[A](element: A): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      SmartAssertions.containsIterable(element).withCode("contains", valueArgument(element))
    )

  /**
   * Makes a new assertion that requires a `Cause` to contain the specified
   * cause.
   */
  def containsCause[E](cause: Cause[E]): Assertion[Cause[E]] =
    Assertion[Cause[E]] {
      SmartAssertions.containsCause(cause).withCode("containsCause", valueArgument(cause))
    }

  /**
   * Makes a new assertion that requires a string to contain `element` as a
   * substring.
   */
  def containsString(element: String): Assertion[String] =
    Assertion[String](
      SmartAssertions.containsString(element).withCode("containsString", valueArgument(element))
    )

  /**
   * Makes a new assertion that requires an `Exit` to be a failure whose cause
   * contains a defect that satisfies `assertion`. When the cause holds several
   * defects, the first one is checked; a cause that also holds a typed failure
   * still satisfies it.
   */
  def dies(assertion: Assertion[Throwable]): Assertion[Exit[Any, Any]] =
    Assertion[Exit[Any, Any]](
      SmartAssertions.asExitDie.withCode("dies") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an `Exit` to be a failure whose cause
   * contains a defect of type `E` (or a subtype). The typed error of the `Exit`
   * does not matter.
   */
  def diesWithA[E: ClassTag]: Assertion[Exit[Any, Any]] =
    dies(isSubtype[E](anything)).withCode("diesWithA", typeArgument[E])

  /**
   * Makes a new assertion that requires a sequence to end with the specified
   * suffix.
   */
  def endsWith[A](suffix: Seq[A]): Assertion[Seq[A]] =
    Assertion[Seq[A]](
      SmartAssertions.endsWithSeq(suffix).withCode("endsWith", valueArgument(suffix))
    )

  /**
   * Makes a new assertion that requires a string to end with the specified
   * suffix.
   */
  def endsWithString(suffix: String): Assertion[String] =
    Assertion[String](
      SmartAssertions.endsWithString(suffix).withCode("endsWithString", valueArgument(suffix))
    )

  /**
   * Makes a new assertion that requires a string to equal `other`, ignoring
   * case.
   */
  def equalsIgnoreCase(other: String): Assertion[String] =
    Assertion[String](
      TestArrow
        .make[String, Boolean] { string =>
          TestTrace.boolean(
            string.equalsIgnoreCase(other)
          ) {
            M.pretty(string) + M.equals + M.pretty(other) + "(ignoring case)"
          }
        }
        .withCode("equalsIgnoreCase", valueArgument(other))
    )

  /**
   * Makes a new assertion that extracts a field with `proj` and checks it with
   * `assertion`. `name` is the field name shown in the failure report.
   *
   * {{{
   * import zio.test.Assertion._
   *
   * final case class User(name: String, age: Int)
   *
   * val adult: Assertion[User] = hasField("age", (_: User).age, isGreaterThanEqualTo(18))
   *
   * val result: TestResult = assert(User("Ann", 26))(adult)
   * // result.isSuccess == true
   * }}}
   */
  def hasField[A, B](name: String, proj: A => B, assertion: Assertion[B]): Assertion[A] =
    Assertion {
      SmartAssertions.hasField(name, proj).withCode("hasField", valueArgument(field(name))) >>> assertion.arrow
    }

  /**
   * Makes a new assertion that requires a `Left` value satisfying the specified
   * assertion.
   */
  def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion[Either[A, Any]](
      SmartAssertions.asLeft[A].withCode("isLeft") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a `Right` value satisfying the
   * specified assertion.
   */
  def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion[Either[Any, A]](
      SmartAssertions.asRight[A].withCode("isRight") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an `Either` to be a `Right`.
   */
  val isRight: Assertion[Either[Any, Any]] =
    isRight(anything)

  /**
   * Makes a new assertion that requires an `Either` to be a `Left`.
   */
  val isLeft: Assertion[Either[Any, Any]] =
    isLeft(anything)

  /**
   * Makes a new assertion that requires a `Some` value satisfying the specified
   * assertion.
   */
  def isSome[A](assertion: Assertion[A]): Assertion[Option[A]] =
    Assertion[Option[A]](
      SmartAssertions.isSome.withCode("isSome") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an `Option` to be a `Some`.
   */
  val isSome: Assertion[Option[Any]] =
    isSome(anything)

  /**
   * Makes a new assertion that requires an Iterable to be sorted in ascending
   * order of `ord`; equal neighbours are allowed. An empty Iterable is sorted.
   */
  def isSorted[A](implicit ord: Ordering[A]): Assertion[Iterable[A]] = {
    @scala.annotation.tailrec
    def loop(iterator: Iterator[A]): Boolean = iterator.hasNext match {
      case false => true
      case true =>
        val x = iterator.next()
        if (iterator.hasNext) {
          val y = iterator.next()
          if (ord.lteq(x, y)) loop(Iterator(y) ++ iterator) else false
        } else {
          true
        }
    }

    Assertion[Iterable[A]](
      TestArrow
        .make[Iterable[A], Boolean] { iterable =>
          TestTrace.boolean(
            loop(iterable.iterator)
          ) {
            M.pretty(iterable) + M.was + "sorted"
          }
        }
        .withCode("isSorted")
    )
  }

  /**
   * Makes a new assertion that requires an Iterable to be sorted in descending
   * order of `ord`; equal neighbours are allowed.
   */
  def isSortedReverse[A](implicit ord: Ordering[A]): Assertion[Iterable[A]] =
    isSorted(ord.reverse).withCode("isSortedReverse")

  /**
   * Makes a new assertion that requires a value to be an instance of `A` and
   * checks it, as an `A`, with `assertion`. The check uses the runtime class
   * from the `ClassTag`, so the type arguments of `A` are not checked.
   *
   * {{{
   * import zio.test.Assertion._
   *
   * sealed trait Shape
   * final case class Circle(radius: Double) extends Shape
   * final case class Square(side: Double)   extends Shape
   *
   * val shape: Shape = Circle(1.5)
   *
   * val result: TestResult =
   *   assert(shape)(isSubtype[Circle](hasField("radius", (_: Circle).radius, equalTo(1.5))))
   * // result.isSuccess == true
   * }}}
   */
  def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion[Any](
      SmartAssertions.as[Any, A].withCode("isSubtype", typeArgument[A]) >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a value be true.
   */
  val isTrue: Assertion[Boolean] =
    Assertion {
      TestArrow
        .make[Boolean, Boolean] { boolean =>
          TestTrace.boolean(boolean)(M.value(boolean) + M.was + M.value(true))
        }
        .withCode("isTrue")
    }

  /**
   * Makes a new assertion that requires a value be false.
   */
  val isFalse: Assertion[Boolean] =
    Assertion {
      TestArrow
        .make[Boolean, Boolean] { boolean =>
          TestTrace.boolean(!boolean)(M.value(boolean) + M.was + M.value(false))
        }
        .withCode("isFalse")
    }

  /**
   * Makes a new assertion that requires a `Failure` value satisfying the
   * specified assertion.
   */
  def isFailure(assertion: Assertion[Throwable]): Assertion[Try[Any]] =
    Assertion[Try[Any]](
      TestArrow
        .make[Try[Any], Throwable] { tryValue =>
          TestTrace.option(tryValue.failed.toOption) {
            M.pretty(tryValue) + M.was + "a Failure"
          }
        }
        .withCode("isFailure") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a `Try` value to be a `Failure`.
   */
  val isFailure: Assertion[Try[Any]] =
    isFailure(anything)

  /**
   * Makes a new assertion that requires a `Success` value satisfying the
   * specified assertion.
   */
  def isSuccess[A](assertion: Assertion[A]): Assertion[Try[A]] =
    Assertion[Try[A]](
      TestArrow
        .make[Try[A], A] { tryValue =>
          TestTrace.option(tryValue.toOption) {
            M.pretty(tryValue) + M.was + "a Success"
          }
        }
        .withCode("isSuccess") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a `Try` value to be a `Success`.
   */
  val isSuccess: Assertion[Try[Any]] =
    isSuccess(anything)

  /**
   * Makes a new assertion that requires a value to be equal (with `==`) to one
   * of the specified values.
   */
  def isOneOf[A](values: Iterable[A]): Assertion[A] =
    Assertion {
      TestArrow
        .make[A, Boolean] { value =>
          TestTrace.boolean(values.exists(_ == value)) {
            M.value(value) + M.was + M.value(values)
          }
        }
        .withCode("isOneOf", valueArgument(values))
    }

  /**
   * Makes a new assertion that requires an exception's message to satisfy
   * `message`. It fails when the message is `null`.
   */
  def hasMessage(message: Assertion[String]): Assertion[Throwable] =
    Assertion[Throwable](
      TestArrow
        .make[Throwable, String] { throwable =>
          Option(throwable.getMessage) match {
            case Some(value) => TestTrace.succeed(value)
            case None        => TestTrace.fail(s"${throwable.getClass.getName} had no message")
          }
        }
        .withCode("hasMessage") >>> message.arrow
    )

  /**
   * Makes a new assertion that requires the suppressed exceptions of an
   * exception to satisfy `cause`.
   */
  def hasSuppressed(cause: Assertion[Iterable[Throwable]]): Assertion[Throwable] =
    Assertion[Throwable](
      TestArrow
        .fromFunction[Throwable, Iterable[Throwable]]((_: Throwable).getSuppressed)
        .withCode("hasSuppressed") >>> cause.arrow
    )

  /**
   * Makes a new assertion that requires an exception to have a cause that
   * satisfies `cause`. It fails when the cause is `null`.
   */
  def hasThrowableCause(cause: Assertion[Throwable]): Assertion[Throwable] =
    Assertion[Throwable](
      TestArrow
        .make[Throwable, Throwable] { throwable =>
          Option(throwable.getCause) match {
            case Some(value) => TestTrace.succeed(value)
            case None        => TestTrace.fail(s"${throwable.getClass.getName} had no cause")
          }

        }
        .withCode("hasThrowableCause") >>> cause.arrow
    )

  /**
   * Makes a new assertion that requires a `None` value.
   */
  val isNone: Assertion[Option[Any]] =
    Assertion {
      SmartAssertions.isEmptyOption
        .withCode("isNone")
    }

  /**
   * Makes a new assertion that requires an Iterable to be non-empty.
   */
  val isNonEmpty: Assertion[Iterable[Any]] =
    Assertion {
      SmartAssertions.isNonEmptyIterable
        .withCode("isNonEmpty")
    }

  /**
   * Makes a new assertion that requires the value be unit. The type
   * `Assertion[Unit]` does the check, so it succeeds for any value it accepts.
   */
  val isUnit: Assertion[Unit] =
    Assertion[Unit](
      TestArrow
        .make[Unit, Boolean] { value =>
          TestTrace.boolean(true)(M.value(value) + M.was + M.value("unit"))
        }
        .withCode("isUnit")
    )

  /**
   * Makes a new assertion that requires the value be less than the specified
   * reference value.
   */
  def isLessThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion[A](
      SmartAssertions.lessThan(reference).withCode("isLessThan", valueArgument(reference))
    )

  /**
   * Makes a new assertion that requires the value be less than or equal to the
   * specified reference value.
   */
  def isLessThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion[A](
      SmartAssertions.lessThanOrEqualTo(reference).withCode("isLessThanEqualTo", valueArgument(reference))
    )

  /**
   * Makes a new assertion that requires the value be greater than the specified
   * reference value.
   */
  def isGreaterThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion[A](
      SmartAssertions.greaterThan(reference).withCode("isGreaterThan", valueArgument(reference))
    )

  /**
   * Makes a new assertion that requires the value be greater than or equal to
   * the specified reference value.
   */
  def isGreaterThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion[A](
      SmartAssertions.greaterThanOrEqualTo(reference).withCode("isGreaterThanEqualTo", valueArgument(reference))
    )

  /**
   * Makes a new assertion that requires a numeric value to be less than zero.
   */
  def isNegative[A](implicit num: Numeric[A]): Assertion[A] =
    Assertion[A](
      TestArrow
        .make[A, Boolean] { value =>
          TestTrace.boolean(num.lt(value, num.zero))(M.pretty(value) + M.was + M.value("negative"))
        }
        .withCode("isNegative")
    )

  /**
   * Makes a new assertion that requires a numeric value to be greater than
   * zero.
   */
  def isPositive[A](implicit num: Numeric[A]): Assertion[A] =
    Assertion[A](
      TestArrow
        .make[A, Boolean] { value =>
          TestTrace.boolean(num.gt(value, num.zero))(M.pretty(value) + M.was + "positive")
        }
        .withCode("isPositive")
    )

  /**
   * Makes a new assertion that requires an Iterable to contain an element
   * satisfying the given assertion; an empty Iterable fails. See
   * [[Assertion.contains]] if you only need an Iterable to contain a given
   * element.
   */
  def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      SmartAssertions.existsIterable(assertion.arrow).withCode("exists", assertionArgument(assertion))
    )

  /**
   * Makes a new assertion that requires a value to fall within a specified min
   * and max (inclusive).
   */
  def isWithin[A](min: A, max: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion[A](
      TestArrow
        .make[A, Boolean] { value =>
          TestTrace.boolean(ord.gteq(value, min) && ord.lteq(value, max))(
            M.pretty(value) + M.was + "within" + M.pretty(min) + "and" + M.pretty(max)
          )
        }
        .withCode("isWithin", valueArgument(min, name = Some("min")), valueArgument(max, name = Some("max")))
    )

  /**
   * Makes a new assertion that requires an `Exit` to be a failure whose cause
   * contains a typed error that satisfies `assertion`. When the cause holds
   * several typed errors, the first one is checked; a cause that also holds a
   * defect still satisfies it.
   *
   * {{{
   * import zio._
   * import zio.test.Assertion._
   *
   * val effect: IO[String, Int] = ZIO.fail("not found")
   *
   * val result: UIO[TestResult] = assertZIO(effect.exit)(fails(equalTo("not found")))
   * }}}
   */
  def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion[Exit[E, Any]](
      SmartAssertions.asExitFailure[E].withCode("fails") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an `Exit` to be a failure whose cause
   * contains a typed error of type `E` (or a subtype).
   */
  def failsWithA[E: ClassTag]: Assertion[Exit[Any, Any]] =
    fails(isSubtype[E](anything)).withCode("failsWithA", typeArgument[E])

  /**
   * Makes a new assertion that requires an `Exit` to be a failure whose cause
   * satisfies `assertion`. Any failure qualifies: typed errors, defects and
   * interruptions.
   */
  def failsCause[E](assertion: Assertion[Cause[E]]): Assertion[Exit[E, Any]] =
    Assertion[Exit[E, Any]](
      SmartAssertions.asExitCause[E].withCode("failsCause") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires every element of an Iterable to satisfy
   * the given assertion. An empty Iterable succeeds.
   */
  def forall[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      SmartAssertions.forallIterable(assertion.arrow).withCode("forall", assertionArgument(assertion))
    )

  /**
   * Makes a new assertion that requires an Iterable to have the same distinct
   * elements as the other Iterable, though not necessarily in the same order.
   */
  def hasSameElementsDistinct[A](other: Iterable[A]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      TestArrow
        .make[Iterable[A], Boolean] { value =>
          TestTrace.boolean(value.toSet == other.toSet)(
            M.pretty(value) + M.had + "the same distinct elements as" + M.pretty(other)
          )
        }
        .withCode("hasSameElementsDistinct", valueArgument(other))
    )

  /**
   * Makes a new assertion that requires the size of an Iterable be satisfied by
   * the specified assertion.
   */
  def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      TestArrow
        .fromFunction[Iterable[A], Int](_.size)
        .withCode("hasSize") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the length of a string be satisfied by
   * the specified assertion.
   */
  def hasSizeString(assertion: Assertion[Int]): Assertion[String] =
    Assertion[String](
      TestArrow
        .fromFunction[String, Int](_.length)
        .withCode("hasSizeString") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the intersection of an Iterable with
   * `other` to satisfy the given assertion. The intersection keeps duplicates,
   * as `Seq#intersect` does.
   */
  def hasIntersection[A](other: Iterable[A])(assertion: Assertion[Iterable[A]]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      TestArrow
        .fromFunction[Iterable[A], Iterable[A]](value => value.toSeq.intersect(other.toSeq))
        .withCode("hasIntersection", valueArgument(other)) >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable contain at least one of the
   * specified elements.
   */
  def hasAtLeastOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isGreaterThanEqualTo(1))).withCode("hasAtLeastOneOf", valueArgument(other))

  /**
   * Makes a new assertion that requires the intersection of an Iterable with
   * `other`, duplicates included, to have at most one element.
   */
  def hasAtMostOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isLessThanEqualTo(1))).withCode("hasAtMostOneOf", valueArgument(other))

  /**
   * Makes a new assertion that requires the first element of an Iterable to
   * satisfy the given assertion. An empty Iterable fails.
   */
  def hasFirst[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      SmartAssertions.head.withCode("hasFirst") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the last element of an Iterable to
   * satisfy the given assertion. An empty Iterable fails.
   */
  def hasLast[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      SmartAssertions.last.withCode("hasLast") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable to be empty.
   */
  val isEmpty: Assertion[Iterable[Any]] =
    Assertion[Iterable[Any]](
      SmartAssertions.isEmptyIterable.withCode("isEmpty")
    )

  /**
   * Makes a new assertion that requires a given string to be empty.
   */
  val isEmptyString: Assertion[String] =
    Assertion[String](
      SmartAssertions.isEmptyString.withCode("isEmptyString")
    )

  /**
   * Makes a new assertion that requires a Map to have the specified key with
   * value satisfying the specified assertion. The value is looked up with
   * `Map#apply`, so a map with a default value (`withDefaultValue`) satisfies
   * it for a missing key too.
   */
  def hasKey[K, V](key: K, assertion: Assertion[V]): Assertion[Map[K, V]] =
    Assertion[Map[K, V]](
      SmartAssertions
        .hasKey[K, V](key)
        .withCode("hasKey", valueArgument(key)) >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a Map to have the specified key. The
   * key is looked up with `Map#apply`, so a map with a default value
   * (`withDefaultValue`) satisfies it for a missing key too.
   */
  def hasKey[K, V](key: K): Assertion[Map[K, V]] =
    hasKey(key, anything).withCode("hasKey", valueArgument(key))

  /**
   * Makes a new assertion that requires the keys of a Map to satisfy the
   * specified assertion.
   */
  def hasKeys[K, V](assertion: Assertion[Iterable[K]]): Assertion[Map[K, V]] =
    Assertion[Map[K, V]](
      TestArrow.make[Map[K, V], Iterable[K]](map => TestTrace.succeed(map.keys)).withCode("hasKeys") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable contain none of the
   * specified elements.
   */
  def hasNoneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(isEmpty).withCode("hasNoneOf", valueArgument(other))

  /**
   * Makes a new assertion that requires the intersection of an Iterable with
   * `other`, duplicates included, to have exactly one element.
   */
  def hasOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(equalTo(1))).withCode("hasOneOf", valueArgument(other))

  /**
   * Makes a new assertion that requires an Iterable to have the same elements
   * as the specified Iterable, though not necessarily in the same order.
   * Duplicates count: `Seq(1, 1, 2)` and `Seq(1, 2, 2)` differ.
   */
  def hasSameElements[A](other: Iterable[A]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      TestArrow
        .make[Iterable[A], Boolean] { actual =>
          val actualSeq = actual.toSeq
          val otherSeq  = other.toSeq

          val result = actualSeq.diff(otherSeq).isEmpty && otherSeq.diff(actualSeq).isEmpty
          TestTrace.boolean(result) {
            M.pretty(actualSeq) + M.had + "the same elements as " + M.pretty(otherSeq)
          }

        }
        .withCode("hasSameElements", valueArgument(other))
    )

  /**
   * Makes a new assertion that requires an Iterable to contain every element of
   * `other`, so that `other` is a subset of it. Duplicates count: `Seq(1)` does
   * not satisfy `hasSubset(Seq(1, 1))`.
   */
  def hasSubset[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSameElements(other)).withCode("hasSubset", valueArgument(other))

  /**
   * Makes a new assertion that requires the values of a Map to satisfy the
   * specified assertion.
   */
  def hasValues[K, V](assertion: Assertion[Iterable[V]]): Assertion[Map[K, V]] =
    Assertion[Map[K, V]](
      TestArrow
        .fromFunction((_: Map[K, V]).values)
        .withCode("hasValues") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a value to be the case named
   * `termName`: `term` extracts the case's content, or returns `None` for other
   * cases, and `assertion` checks the content.
   *
   * {{{
   * import zio.test.Assertion._
   *
   * sealed trait Payment
   * final case class Card(last4: String) extends Payment
   * case object Cash                     extends Payment
   *
   * val card: Assertion[Payment] =
   *   isCase[Payment, String]("Card", { case Card(last4) => Some(last4); case _ => None }, equalTo("4242"))
   *
   * val result: TestResult = assert(Card("4242"): Payment)(card)
   * // result.isSuccess == true
   * }}}
   */
  def isCase[Sum, Proj](
    termName: String,
    term: Sum => Option[Proj],
    assertion: Assertion[Proj]
  ): Assertion[Sum] =
    Assertion[Sum](
      TestArrow
        .make[Sum, Proj] { sum =>
          TestTrace.option(term(sum)) {
            M.pretty(sum) + M.was + "a case of " + termName
          }
        }
        .withCode("isCase", valueArgument(termName), valueArgument(unapplyTerm(termName))) >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable to have no duplicate
   * elements.
   */
  val isDistinct: Assertion[Iterable[Any]] = {
    @scala.annotation.tailrec
    def loop(iterator: Iterator[Any], seen: Set[Any]): Boolean = iterator.hasNext match {
      case false => true
      case true =>
        val x = iterator.next()
        if (seen.contains(x)) false else loop(iterator, seen + x)
    }

    Assertion[Iterable[Any]](
      TestArrow
        .make[Iterable[Any], Boolean] { as =>
          TestTrace.boolean(loop(as.iterator, Set.empty)) {
            M.pretty(as) + M.was + "distinct"
          }
        }
        .withCode("isDistinct")
    )
  }

  /**
   * Makes a new assertion that requires a given string to be non empty.
   */
  val isNonEmptyString: Assertion[String] =
    Assertion[String](
      SmartAssertions.isNonEmptyString
        .withCode("isNonEmptyString")
    )

  /**
   * Makes a new assertion that requires a null value.
   */
  val isNull: Assertion[Any] =
    Assertion[Any](
      TestArrow
        .make[Any, Boolean] { x =>
          TestTrace.boolean(x == null) {
            M.pretty(x) + M.was + "null"
          }
        }
        .withCode("isNull")
    )

  /**
   * Makes a new assertion that requires a numeric value is zero.
   */
  def isZero[A](implicit num: Numeric[A]): Assertion[A] =
    Assertion[A](
      TestArrow
        .make[A, Boolean] { x =>
          TestTrace.boolean(num.zero == x) {
            M.pretty(x) + M.was + "zero"
          }
        }
        .withCode("isZero")
    )

  /**
   * Makes a new assertion that requires the whole string to match the specified
   * regular expression, as `String#matches` does: `"abc"` does not match `"b"`.
   */
  def matchesRegex(regex: String): Assertion[String] =
    Assertion[String](
      TestArrow
        .make[String, Boolean] { s =>
          TestTrace.boolean(s.matches(regex)) {
            M.pretty(s) + M.did + "match" + M.pretty(regex)
          }
        }
        .withCode("matchesRegex", valueArgument(regex))
    )

  /**
   * Makes a new assertion that requires a numeric value to be zero or greater.
   */
  def nonNegative[A](implicit num: Numeric[A]): Assertion[A] =
    isGreaterThanEqualTo(num.zero).withCode("nonNegative")

  /**
   * Makes a new assertion that requires a numeric value to be zero or less.
   */
  def nonPositive[A](implicit num: Numeric[A]): Assertion[A] =
    isLessThanEqualTo(num.zero).withCode("nonPositive")

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  def not[A](assertion: Assertion[A]): Assertion[A] =
    assertion.negate

  /**
   * Makes a new assertion that always fails.
   */
  val nothing: Assertion[Any] =
    Assertion[Any](
      TestArrow
        .make[Any, Boolean] { input =>
          TestTrace.succeed(false)
        }
        .withCode("nothing")
    )

  /**
   * Makes a new assertion that requires a given sequence to start with the
   * specified prefix.
   */
  def startsWith[A](prefix: Seq[A]): Assertion[Seq[A]] =
    Assertion[Seq[A]](
      SmartAssertions
        .startsWithSeq(prefix)
        .withCode("startsWith", valueArgument(prefix))
    )

  /**
   * Makes a new assertion that requires a given string to start with a
   * specified prefix.
   */
  def startsWithString(prefix: String): Assertion[String] =
    Assertion[String](
      SmartAssertions.startsWithString(prefix).withCode("startsWithString", valueArgument(prefix))
    )

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  def succeeds[A](assertion: Assertion[A]): Assertion[Exit[Any, A]] =
    Assertion {
      SmartAssertions.asExitSuccess[Any, A].withCode("succeeds") >>> assertion.arrow
    }

  /**
   * Makes a new assertion that requires the expression to throw an exception
   * that satisfies `assertion`. Use it with `assert`, which evaluates the
   * expression inside the check.
   *
   * {{{
   * import zio.test.Assertion._
   *
   * val result: TestResult =
   *   assert(Integer.parseInt("forty-two"))(throws(isSubtype[NumberFormatException](anything)))
   * // result.isSuccess == true
   * }}}
   */
  def throws[A](assertion: Assertion[Throwable]): Assertion[A] =
    Assertion[A](
      SmartAssertions.throws.withCode("throws") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the expression to throw an instance of
   * given type (or its subtype).
   */
  def throwsA[E: ClassTag]: Assertion[Any] =
    throws(isSubtype[E](anything))

  /**
   * Makes a new assertion that requires an `Exit` to be a failure whose cause
   * contains an interruption. A cause that also holds a typed error or a defect
   * satisfies it.
   */
  def isInterrupted: Assertion[Exit[Any, Any]] =
    Assertion[Exit[Any, Any]](
      TestArrow
        .make[Exit[Any, Any], Boolean] {
          case Exit.Failure(cause) =>
            TestTrace.boolean(cause.isInterrupted) {
              M.value("Exit") + M.was + "interrupted"
            }
          case _ =>
            TestTrace.fail {
              M.value("Exit") + M.was + "interrupted"
            }
        }
        .withCode("isInterrupted")
    )

  /**
   * Makes a new assertion that requires an `Exit` to be a failure whose cause
   * is a single interruption and nothing else.
   */
  def isJustInterrupted: Assertion[Exit[Any, Any]] =
    Assertion[Exit[Any, Any]](
      TestArrow
        .make[Exit[Any, Any], Boolean] {
          case Exit.Failure(Cause.Interrupt(_, _)) =>
            TestTrace.succeed(true)
          case _ =>
            TestTrace.fail {
              M.value("Exit") + M.was + "just interrupted"
            }
        }
        .withCode("isJustInterrupted")
    )

  /**
   * An argument of a built-in assertion (a value, a type or a nested
   * assertion), kept so that `render` can show how the assertion was built.
   */
  sealed trait Arguments { self =>
    override final def toString: String = self match {
      case AssertionArgument(assertion) =>
        assertion.render
      case TypeArgument(classTag) =>
        className(classTag)
      case ValueArgument(value, name) =>
        name.map(n => s"$n=$value").getOrElse(s"$value")
    }
  }
  /**
   * Constructors and cases of `Arguments`.
   */
  object Arguments {

    /**
     * Constructs a `TypeArgument` from a ClassTag.
     */
    def typeArgument[A](implicit C: ClassTag[A]): Arguments =
      TypeArgument(C)

    /**
     * Creates a string representation of a field accessor.
     */
    def field(name: String): String =
      "_." + name

    /**
     * Returns the simple name of the runtime class of `C`, or its full name
     * when the simple name is not available.
     */
    def className[A](C: ClassTag[A]): String =
      try {
        C.runtimeClass.getSimpleName
      } catch {
        // See https://github.com/scala/bug/issues/2034.
        case t: InternalError if t.getMessage == "Malformed class name" =>
          C.runtimeClass.getName
      }

    /**
     * Constructs an `AssertionArgument` from an `Assertion`.
     */
    def assertionArgument[A]: Assertion[A] => Arguments =
      Arguments.AssertionArgument(_)

    /**
     * Constructs a `ValueArgument` from a value and an optional parameter name.
     */
    def valueArgument[A](a: A, name: Option[String] = None): Arguments =
      Arguments.ValueArgument(a, name)

    /**
     * Creates a string representation of an unapply method for a term.
     */
    def unapplyTerm(termName: String): String =
      termName + ".unapply"

    /**
     * An argument that is itself an assertion.
     */
    final case class AssertionArgument[A](assertion: Assertion[A])           extends Arguments
    /**
     * An argument that is a type, given by its `ClassTag`.
     */
    final case class TypeArgument[A](classTag: ClassTag[A])                  extends Arguments
    /**
     * An argument that is a value, with an optional parameter name.
     */
    final case class ValueArgument[A](value: A, name: Option[String] = None) extends Arguments
  }
}
