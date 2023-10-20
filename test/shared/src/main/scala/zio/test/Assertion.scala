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

final case class Assertion[-A](arrow: TestArrow[A, Boolean]) { self =>

  def render: String = arrow.render

  def &&[A1 <: A](that: Assertion[A1]): Assertion[A1] =
    Assertion(arrow && that.arrow)

  def ||[A1 <: A](that: Assertion[A1]): Assertion[A1] =
    Assertion(arrow || that.arrow)

  def unary_! : Assertion[A] =
    Assertion(!arrow)

  def negate: Assertion[A] =
    Assertion(!arrow)

  def test(value: A)(implicit sourceLocation: SourceLocation): Boolean =
    TestArrow.run(arrow.withLocation, Right(value)).isSuccess

  // TODO: IMPLEMENT LABELING
  def label(message: String): Assertion[A] =
    Assertion(self.arrow.label(message))

  def ??(message: String): Assertion[A] =
    self.label(message)

  def run(value: => A)(implicit sourceLocation: SourceLocation): TestResult =
    Assertion.smartAssert(value)(self)

  private[test] def withCode(code: String, arguments: Arguments*): Assertion[A] =
    Assertion(arrow.withCode(code, arguments: _*))
}

object Assertion extends AssertionVariants {
  import Arguments._

  private[test] def smartAssert[A](
    expr: => A,
    codeString: Option[String] = None,
    assertionString: Option[String] = None
  )(
    assertion: Assertion[A]
  )(implicit sourceLocation: SourceLocation): TestResult = {
    lazy val value0 = expr
    val completeString =
      codeString.flatMap(code =>
        assertionString.map { assertion =>
          code.blue + " did not satisfy " + assertion.cyan
        }
      )
    TestResult(
      (TestArrow.succeed(value0).withCode(codeString.getOrElse("input")) >>> assertion.arrow).withLocation
        .withCompleteCode(completeString.getOrElse("<CODE>"))
    )
  }

  private[test] def smartAssertZIO[R, E, A](
    expr: => ZIO[R, E, A]
  )(assertion: Assertion[A])(implicit trace: Trace, sourceLocation: SourceLocation): ZIO[R, E, TestResult] = {
    lazy val value0 = expr
    value0.map(smartAssert(_)(assertion))
  }

  /**
   * Makes a new `Assertion` from a function.
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
   * Makes a new assertion that always succeeds.
   */
  val anything: Assertion[Any] =
    Assertion[Any] {
      SmartAssertions.anything.withCode("anything")
    }

  def hasAt[A](pos: Int)(assertion: Assertion[A]): Assertion[Seq[A]] =
    Assertion[Seq[A]](
      SmartAssertions.hasAt(pos).withCode("hasAt", valueArgument(pos)) >>>
        assertion.arrow
    )

  /**
   * Makes a new assertion that requires a given numeric value to match a value
   * with some tolerance.
   */
  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): Assertion[A] =
    Assertion[A](
      SmartAssertions
        .approximatelyEquals(reference, tolerance)
        .withCode("approximatelyEquals", valueArgument(reference), valueArgument(tolerance, name = Some("tolerance")))
    )

  /**
   * Makes a new assertion that requires an Iterable contain the specified
   * element. See [[Assertion.exists]] if you want to require an Iterable to
   * contain an element satisfying an assertion.
   */
  def contains[A](element: A): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      SmartAssertions.containsIterable(element).withCode("contains", valueArgument(element))
    )

  /**
   * Makes a new assertion that requires a `Cause` contain the specified cause.
   */
  def containsCause[E](cause: Cause[E]): Assertion[Cause[E]] =
    Assertion[Cause[E]] {
      SmartAssertions.containsCause(cause).withCode("containsCause", valueArgument(cause))
    }

  /**
   * Makes a new assertion that requires a substring to be present.
   */
  def containsString(element: String): Assertion[String] =
    Assertion[String](
      SmartAssertions.containsString(element).withCode("containsString", valueArgument(element))
    )

  /**
   * Makes a new assertion that requires an exit value to die.
   */
  def dies(assertion: Assertion[Throwable]): Assertion[Exit[Any, Any]] =
    Assertion[Exit[Any, Any]](
      SmartAssertions.asExitDie.withCode("dies") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an exit value to die with an instance
   * of given type (or its subtype).
   */
  def diesWithA[E: ClassTag]: Assertion[Exit[Any, Any]] =
    dies(isSubtype[E](anything)).withCode("diesWithA", typeArgument[E])

  /**
   * Makes a new assertion that requires a given string to end with the
   * specified suffix.
   */
  def endsWith[A](suffix: Seq[A]): Assertion[Seq[A]] =
    Assertion[Seq[A]](
      SmartAssertions.endsWithSeq(suffix).withCode("endsWith", valueArgument(suffix))
    )

  /**
   * Makes a new assertion that requires a given string to end with the
   * specified suffix.
   */
  def endsWithString(suffix: String): Assertion[String] =
    Assertion[String](
      SmartAssertions.endsWithString(suffix).withCode("endsWithString", valueArgument(suffix))
    )

  /**
   * Makes a new assertion that requires a given string to equal another
   * ignoring case.
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
   * Makes a new assertion that focuses in on a field in a case class.
   *
   * {{{
   * hasField("age", _.age, within(0, 10))
   * }}}
   */
  def hasField[A, B](name: String, proj: A => B, assertion: Assertion[B]): Assertion[A] =
    Assertion {
      SmartAssertions.hasField(name, proj).withCode("hasField", valueArgument(field(name))) >>> assertion.arrow
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion[Either[A, Any]](
      SmartAssertions.asLeft[A].withCode("isLeft") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion[Either[Any, A]](
      SmartAssertions.asRight[A].withCode("isRight") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Either is Right.
   */
  val isRight: Assertion[Either[Any, Any]] =
    isRight(anything)

  /**
   * Makes a new assertion that requires an Either is Left.
   */
  val isLeft: Assertion[Either[Any, Any]] =
    isLeft(anything)

  /**
   * Makes a new assertion that requires a Some value satisfying the specified
   * assertion.
   */
  def isSome[A](assertion: Assertion[A]): Assertion[Option[A]] =
    Assertion[Option[A]](
      SmartAssertions.isSome.withCode("isSome") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Option is Some.
   */
  val isSome: Assertion[Option[Any]] =
    isSome(anything)

  /**
   * Makes a new assertion that requires an Iterable is sorted.
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
   * Makes a new assertion that requires an Iterable is sorted in reverse order.
   */
  def isSortedReverse[A](implicit ord: Ordering[A]): Assertion[Iterable[A]] =
    isSorted(ord.reverse).withCode("isSortedReverse")

  /**
   * Makes a new assertion that requires a value have the specified type.
   *
   * Example:
   * {{{
   *   assert(Duration.fromNanos(1))(isSubtype[Duration.Finite](Assertion.anything))
   * }}}
   */
  def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion[Any](
      SmartAssertions.as[Any, A].withCode("isSubtype", typeArgument[A]) >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a value be true.
   */
  def isTrue: Assertion[Boolean] =
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
  def isFalse: Assertion[Boolean] =
    Assertion {
      TestArrow
        .make[Boolean, Boolean] { boolean =>
          TestTrace.boolean(!boolean)(M.value(boolean) + M.was + M.value(false))
        }
        .withCode("isFalse")
    }

  /**
   * Makes a new assertion that requires a Failure value satisfying the
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
   * Makes a new assertion that requires a Try value is Failure.
   */
  val isFailure: Assertion[Try[Any]] =
    isFailure(anything)

  /**
   * Makes a new assertion that requires a Success value satisfying the
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
   * Makes a new assertion that requires a Try value is Success.
   */
  val isSuccess: Assertion[Try[Any]] =
    isSuccess(anything)

  /**
   * Makes a new assertion that requires a value to be equal to one of the
   * specified values.
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
   * Makes a new assertion that requires an exception to have a certain message.
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
   * Makes a new assertion that requires an exception to have certain suppressed
   * exceptions.
   */
  def hasSuppressed(cause: Assertion[Iterable[Throwable]]): Assertion[Throwable] =
    Assertion[Throwable](
      TestArrow
        .fromFunction[Throwable, Iterable[Throwable]]((_: Throwable).getSuppressed)
        .withCode("hasSuppressed") >>> cause.arrow
    )

  /**
   * Makes a new assertion that requires an exception to have a certain cause.
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
   * Makes a new assertion that requires a None value.
   */
  val isNone: Assertion[Option[Any]] =
    Assertion {
      SmartAssertions.isEmptyOption
        .withCode("isNone")
    }

  /**
   * Makes a new assertion that requires an Iterable to be non empty.
   */
  val isNonEmpty: Assertion[Iterable[Any]] =
    Assertion {
      SmartAssertions.isNonEmptyIterable
        .withCode("isNonEmpty")
    }

  /**
   * Makes a new assertion that requires the value be unit.
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
   * Makes a new assertion that requires a numeric value is negative.
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
   * Makes a new assertion that requires a numeric value is positive.
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
   * Makes a new assertion that requires an Iterable contain an element
   * satisfying the given assertion. See [[Assertion.contains]] if you only need
   * an Iterable to contain a given element.
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
   * Makes a new assertion that requires an exit value to fail.
   */
  def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion[Exit[E, Any]](
      SmartAssertions.asExitFailure[E].withCode("fails") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the expression to fail with an instance
   * of given type (or its subtype).
   */
  def failsWithA[E: ClassTag]: Assertion[Exit[Any, Any]] =
    fails(isSubtype[E](anything)).withCode("failsWithA", typeArgument[E])

  /**
   * Makes a new assertion that requires an exit value to fail with a cause that
   * meets the specified assertion.
   */
  def failsCause[E](assertion: Assertion[Cause[E]]): Assertion[Exit[E, Any]] =
    Assertion[Exit[E, Any]](
      SmartAssertions.asExitCause[E].withCode("failsCause") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable contain only elements
   * satisfying the given assertion.
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
   * Makes a new assertion that requires the size of a string be satisfied by
   * the specified assertion.
   */
  def hasSizeString(assertion: Assertion[Int]): Assertion[String] =
    Assertion[String](
      TestArrow
        .fromFunction[String, Int](_.length)
        .withCode("hasSizeString") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the intersection of two Iterables
   * satisfy the given assertion.
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
   * Makes a new assertion that requires an Iterable contain at most one of the
   * specified elements.
   */
  def hasAtMostOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isLessThanEqualTo(1))).withCode("hasAtMostOneOf", valueArgument(other))

  /**
   * Makes a new assertion that requires an Iterable to contain the first
   * element satisfying the given assertion.
   */
  def hasFirst[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion[Iterable[A]](
      SmartAssertions.head.withCode("hasFirst") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable to contain the last element
   * satisfying the given assertion.
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
   * value satisfying the specified assertion.
   */
  def hasKey[K, V](key: K, assertion: Assertion[V]): Assertion[Map[K, V]] =
    Assertion[Map[K, V]](
      SmartAssertions
        .hasKey[K, V](key)
        .withCode("hasKey", valueArgument(key)) >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a Map to have the specified key.
   */
  def hasKey[K, V](key: K): Assertion[Map[K, V]] =
    hasKey(key, anything).withCode("hasKey", valueArgument(key))

  /**
   * Makes a new assertion that requires a Map have keys satisfying the
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
   * Makes a new assertion that requires an Iterable contain exactly one of the
   * specified elements.
   */
  def hasOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(equalTo(1))).withCode("hasOneOf", valueArgument(other))

  /**
   * Makes a new assertion that requires an Iterable to have the same elements
   * as the specified Iterable, though not necessarily in the same order.
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
   * Makes a new assertion that requires the specified Iterable to be a subset
   * of the other Iterable.
   */
  def hasSubset[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSameElements(other)).withCode("hasSubset", valueArgument(other))

  /**
   * Makes a new assertion that requires a Map have values satisfying the
   * specified assertion.
   */
  def hasValues[K, V](assertion: Assertion[Iterable[V]]): Assertion[Map[K, V]] =
    Assertion[Map[K, V]](
      TestArrow
        .fromFunction((_: Map[K, V]).values)
        .withCode("hasValues") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the sum type be a specified term.
   *
   * {{{
   * isCase("Some", Some.unapply, anything)
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
   * Makes a new assertion that requires an Iterable is distinct.
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
   * Makes a new assertion that requires a given string to match the specified
   * regular expression.
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
   * Makes a new assertion that requires a numeric value is non negative.
   */
  def nonNegative[A](implicit num: Numeric[A]): Assertion[A] =
    isGreaterThanEqualTo(num.zero).withCode("nonNegative")

  /**
   * Makes a new assertion that requires a numeric value is non positive.
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
   * Makes a new assertion that requires the expression to throw.
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
   * Makes a new assertion that requires an exit value to be interrupted.
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
   * Makes a new assertion that requires an exit value to be interrupted.
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
  object Arguments {

    /**
     * Construct a `TypeArgument` from a ClassTag.
     */
    def typeArgument[A](implicit C: ClassTag[A]): Arguments =
      TypeArgument(C)

    /**
     * Creates a string representation of a field accessor.
     */
    def field(name: String): String =
      "_." + name

    /*
     * Creates a string representation of a class name.
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
     * Construct a `AssertionArgument` from an `Assertion`.
     */
    def assertionArgument[A]: Assertion[A] => Arguments =
      Arguments.AssertionArgument(_)

    /**
     * Construct a `ValueArgument` from a value.
     */
    def valueArgument[A](a: A, name: Option[String] = None): Arguments =
      Arguments.ValueArgument(a, name)

    /**
     * Creates a string representation of an unapply method for a term.
     */
    def unapplyTerm(termName: String): String =
      termName + ".unapply"

    final case class AssertionArgument[A](assertion: Assertion[A])           extends Arguments
    final case class TypeArgument[A](classTag: ClassTag[A])                  extends Arguments
    final case class ValueArgument[A](value: A, name: Option[String] = None) extends Arguments
  }
}
