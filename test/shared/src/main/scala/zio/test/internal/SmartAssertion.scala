package zio.test.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.Assertion.Render
import zio.{Cause, Exit, ZIO, ZTraceElement}
import zio.test.{ErrorMessage => M, _}

import scala.reflect.ClassTag
import scala.util.Try

final case class SmartAssertion[-A](arrow: TestArrow[A, Boolean]) {
  def &&[A1 <: A](that: SmartAssertion[A1]): SmartAssertion[A1] =
    SmartAssertion(arrow && that.arrow)

  def ||[A1 <: A](that: SmartAssertion[A1]): SmartAssertion[A1] =
    SmartAssertion(arrow || that.arrow)

  def unary_! : SmartAssertion[A] =
    SmartAssertion(!arrow)

  def negate: SmartAssertion[A] =
    SmartAssertion(!arrow)

  def test(value: A): Boolean =
    TestArrow.run(arrow, Right(value)).isSuccess
}

object SmartAssertion {
  lazy val Assertion = SmartAssertion
  type Assertion[-A] = SmartAssertion[A]

  def smartAssert[A](value: => A)(assertion: SmartAssertion[A]): Assert = {
    lazy val value0 = value
    Assert(TestArrow.succeed(value0).withCode("input") >>> assertion.arrow)
  }

  def smartAssertM[R, E, A](
    value: => ZIO[R, E, A]
  )(assertion: SmartAssertion[A])(implicit trace: ZTraceElement): ZIO[R, E, Assert] = {
    lazy val value0 = value
    value0.map(value => Assert(TestArrow.succeed(value).withCode("input") >>> assertion.arrow))
  }

  // ASSERTIONS

  /**
   * Makes a new assertion that always succeeds.
   */
  val anything: Assertion[Any] =
    SmartAssertion[Any] {
      SmartAssertions.anything.withCode("anything")
    }

  def hasAt[A](pos: Int)(assertion: SmartAssertion[A]): SmartAssertion[Seq[A]] =
    SmartAssertion[Seq[A]](
      SmartAssertions.hasAt(pos).withCode(s"hasAt($pos)") >>>
        assertion.arrow
    )

//  def equalTo[A](expected: A): SmartAssertion[A] =
  def equalTo[A, B](expected: A)(implicit eql: Eql[A, B]): Assertion[B] =
    SmartAssertion[B](
      TestArrow
        .make[B, Boolean] { actual =>
          Trace.boolean(expected == actual) {
            M.pretty(expected) + M.equals + M.pretty(actual)
          }
        }
        .withCode("equalTo")
    )

  /**
   * Makes a new assertion that requires a given numeric value to match a value
   * with some tolerance.
   */
  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): SmartAssertion[A] =
    SmartAssertion[A](
      SmartAssertions.approximatelyEquals(reference, tolerance).withCode(s"approximatelyEquals($reference, $tolerance)")
    )

  /**
   * Makes a new assertion that requires an Iterable contain the specified
   * element. See [[SmartAssertion.exists]] if you want to require an Iterable
   * to contain an element satisfying an assertion.
   */
  def contains[A](element: A): SmartAssertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      SmartAssertions.containsIterable(element).withCode(s"contains")
    )

  /**
   * Makes a new assertion that requires a `Cause` contain the specified cause.
   */
  def containsCause[E](cause: Cause[E]): SmartAssertion[Cause[E]] =
    SmartAssertion[Cause[E]] {
      SmartAssertions.containsCause(cause).withCode("containsCause")
    }

  /**
   * Makes a new assertion that requires a substring to be present.
   */
  def containsString(element: String): Assertion[String] =
    SmartAssertion[String](
      SmartAssertions.containsString(element).withCode(s"containsString(${PrettyPrint(element)})")
    )

  /**
   * Makes a new assertion that requires an exit value to die.
   */
  def dies(assertion: Assertion[Throwable]): Assertion[Exit[Any, Any]] =
    SmartAssertion[Exit[Any, Any]](
      SmartAssertions.asExitDie.withCode("dies") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an exit value to die with an instance
   * of given type (or its subtype).
   */
  def diesWithA[E: ClassTag]: Assertion[Exit[E, Any]] =
    dies(isSubtype[E](anything))

  /**
   * Makes a new assertion that requires a given string to end with the
   * specified suffix.
   */
  def endsWith[A](suffix: Seq[A]): Assertion[Seq[A]] =
    SmartAssertion[Seq[A]](
      SmartAssertions.endsWithSeq(suffix).withCode(s"endsWith(${PrettyPrint(suffix)})")
    )

  /**
   * Makes a new assertion that requires a given string to end with the
   * specified suffix.
   */
  def endsWithString(suffix: String): Assertion[String] =
    SmartAssertion[String](
      SmartAssertions.endsWithString(suffix).withCode(s"endsWithString($suffix)")
    )

  /**
   * Makes a new assertion that requires a given string to equal another
   * ignoring case.
   */
  def equalsIgnoreCase(other: String): Assertion[String] =
    SmartAssertion[String](
      TestArrow
        .make[String, Boolean] { string =>
          Trace.boolean(
            string.equalsIgnoreCase(other)
          ) {
            M.pretty(string) + M.equals + M.pretty(other) + "(ignoring case)"
          }
        }
        .withCode(s"equalsIgnoreCase(${PrettyPrint(other)}")
    )

  /**
   * Makes a new assertion that focuses in on a field in a case class.
   *
   * {{{
   * hasField("age", _.age, within(0, 10))
   * }}}
   */
  def hasField[A, B](name: String, proj: A => B, assertion: SmartAssertion[B]): SmartAssertion[A] =
    SmartAssertion {
      SmartAssertions.hasField(name, proj).withCode(s"hasField($name)") >>> assertion.arrow
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    SmartAssertion[Either[A, Any]](
      SmartAssertions.asLeft[A].withCode("isLeft") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    SmartAssertion[Either[Any, A]](
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
    SmartAssertion[Option[A]](
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

    SmartAssertion[Iterable[A]](
      TestArrow.make[Iterable[A], Boolean] { iterable =>
        Trace.boolean(
          loop(iterable.iterator)
        ) {
          M.pretty(iterable) + M.was + "sorted"
        }
      }
    )
  }

  /**
   * Makes a new assertion that requires an Iterable is sorted in reverse order.
   */
  def isSortedReverse[A](implicit ord: Ordering[A]): Assertion[Iterable[A]] =
    isSorted(ord.reverse)

  /**
   * Makes a new assertion that requires a value have the specified type.
   *
   * Example:
   * {{{
   *   assert(Duration.fromNanos(1))(isSubtype[Duration.Finite](Assertion.anything))
   * }}}
   */
  def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    SmartAssertion[Any](
      SmartAssertions
        .as[Any, A]
        .withCode(s"isSubtype(${PrettyPrint(Render.className(C))})") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a value be true.
   */
  def isTrue: SmartAssertion[Boolean] =
    SmartAssertion {
      TestArrow
        .make[Boolean, Boolean] { boolean =>
          Trace.boolean(boolean)(M.value(boolean) + M.was + M.value(true))
        }
        .withCode("isTrue")
    }

  /**
   * Makes a new assertion that requires a value be false.
   */
  def isFalse: SmartAssertion[Boolean] =
    SmartAssertion {
      TestArrow
        .make[Boolean, Boolean] { boolean =>
          Trace.boolean(!boolean)(M.value(boolean) + M.was + M.value(false))
        }
        .withCode("isFalse")
    }

  /**
   * Makes a new assertion that requires a Failure value satisfying the
   * specified assertion.
   */
  def isFailure(assertion: Assertion[Throwable]): Assertion[Try[Any]] =
    SmartAssertion[Try[Any]](
      TestArrow
        .make[Try[Any], Throwable] { tryValue =>
          Trace.option(tryValue.failed.toOption) {
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
    SmartAssertion[Try[A]](
      TestArrow
        .make[Try[A], A] { tryValue =>
          Trace.option(tryValue.toOption) {
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
    SmartAssertion {
      TestArrow
        .make[A, Boolean] { value =>
          Trace.boolean(values.exists(_ == value)) {
            M.value(value) + M.was + M.value(values)
          }
        }
        .withCode("isOneOf")
    }

  /**
   * Makes a new assertion that requires an exception to have a certain message.
   */
  def hasMessage(message: Assertion[String]): Assertion[Throwable] =
    SmartAssertion[Throwable](
      TestArrow
        .make[Throwable, String] { throwable =>
          Option(throwable.getMessage) match {
            case Some(value) => Trace.succeed(value)
            case None        => Trace.fail(s"${throwable.getClass.getName} had no message")
          }
        }
        .withCode("hasMessage") >>> message.arrow
    )

  /**
   * Makes a new assertion that requires an exception to have certain suppressed
   * exceptions.
   */
  def hasSuppressed(cause: Assertion[Iterable[Throwable]]): Assertion[Throwable] =
    SmartAssertion[Throwable](
      TestArrow
        .fromFunction[Throwable, Iterable[Throwable]]((_: Throwable).getSuppressed)
        .withCode("hasSuppressed") >>> cause.arrow
    )

  /**
   * Makes a new assertion that requires an exception to have a certain cause.
   */
  def hasThrowableCause(cause: Assertion[Throwable]): Assertion[Throwable] =
    SmartAssertion[Throwable](
      TestArrow
        .make[Throwable, Throwable] { throwable =>
          Option(throwable.getCause) match {
            case Some(value) => Trace.succeed(value)
            case None        => Trace.fail(s"${throwable.getClass.getName} had no cause")
          }

        }
        .withCode("hasThrowableCause") >>> cause.arrow
    )

  /**
   * Makes a new assertion that requires a None value.
   */
  val isNone: Assertion[Option[Any]] =
    SmartAssertion {
      SmartAssertions.isEmptyOption
        .withCode("isNone")
    }

  /**
   * Makes a new assertion that requires an Iterable to be non empty.
   */
  val isNonEmpty: Assertion[Iterable[Any]] =
    SmartAssertion {
      SmartAssertions.isNonEmptyIterable
        .withCode("isNonEmpty")
    }

  /**
   * Makes a new assertion that requires the value be unit.
   */
  val isUnit: Assertion[Unit] =
    SmartAssertion[Unit](
      TestArrow
        .make[Unit, Boolean] { value =>
          Trace.boolean(value == ())(M.value(value) + M.was + M.value("unit"))
        }
        .withCode("isUnit")
    )

  /**
   * Makes a new assertion that requires the value be less than the specified
   * reference value.
   */
  def isLessThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    SmartAssertion[A](
      SmartAssertions.lessThan(reference).withCode(s"isLessThan($reference)")
    )

  /**
   * Makes a new assertion that requires the value be less than or equal to the
   * specified reference value.
   */
  def isLessThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    SmartAssertion[A](
      SmartAssertions.lessThanOrEqualTo(reference).withCode(s"isLessThanEqualTo($reference)")
    )

  /**
   * Makes a new assertion that requires the value be greater than the specified
   * reference value.
   */
  def isGreaterThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    SmartAssertion[A](
      SmartAssertions.greaterThan(reference).withCode(s"isGreaterThan($reference)")
    )

  /**
   * Makes a new assertion that requires the value be greater than or equal to
   * the specified reference value.
   */
  def isGreaterThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    SmartAssertion[A](
      SmartAssertions.greaterThanOrEqualTo(reference).withCode(s"isGreaterThanEqualTo($reference)")
    )

  /**
   * Makes a new assertion that requires a numeric value is negative.
   */
  def isNegative[A](implicit num: Numeric[A]): Assertion[A] =
    SmartAssertion[A](
      TestArrow
        .make[A, Boolean] { value =>
          Trace.boolean(num.lt(value, num.zero))(M.pretty(value) + M.was + M.value("negative"))
        }
        .withCode("isNegative")
    )

  /**
   * Makes a new assertion that requires a numeric value is positive.
   */
  def isPositive[A](implicit num: Numeric[A]): Assertion[A] =
    SmartAssertion[A](
      TestArrow
        .make[A, Boolean] { value =>
          Trace.boolean(num.gt(value, num.zero))(M.pretty(value) + M.was + "positive")
        }
        .withCode("isPositive")
    )

  /**
   * Makes a new assertion that requires an Iterable contain an element
   * satisfying the given assertion. See [[Assertion.contains]] if you only need
   * an Iterable to contain a given element.
   */
  def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      SmartAssertions.existsIterable(assertion.arrow).withCode("exists")
    )

  /**
   * Makes a new assertion that requires a value to fall within a specified min
   * and max (inclusive).
   */
  def isWithin[A](min: A, max: A)(implicit ord: Ordering[A]): Assertion[A] =
    SmartAssertion[A](
      TestArrow
        .make[A, Boolean] { value =>
          Trace.boolean(ord.gteq(value, min) && ord.lteq(value, max))(
            M.pretty(value) + M.was + "within" + M.pretty(min) + "and" + M.pretty(max)
          )
        }
        .withCode("isWithin")
    )

  /**
   * Makes a new assertion that requires an exit value to fail.
   */
  def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    SmartAssertion[Exit[E, Any]](
      SmartAssertions.asExitFailure[E].withCode("fails") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the expression to fail with an instance
   * of given type (or its subtype).
   */
  def failsWithA[E: ClassTag]: Assertion[Exit[E, Any]] =
    fails(isSubtype[E](anything))

  /**
   * Makes a new assertion that requires an Iterable contain only elements
   * satisfying the given assertion.
   */
  def forall[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      SmartAssertions.forallIterable(assertion.arrow).withCode("forall")
    )

  /**
   * Makes a new assertion that requires an Iterable to have the same distinct
   * elements as the other Iterable, though not necessarily in the same order.
   */
  def hasSameElementsDistinct[A](other: Iterable[A]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      TestArrow
        .make[Iterable[A], Boolean] { value =>
          Trace.boolean(value.toSet == other.toSet)(
            M.pretty(value) + M.had + "the same distinct elements as" + M.pretty(other)
          )
        }
        .withCode(s"hasSameElementsDistinct")
    )

  /**
   * Makes a new assertion that requires the size of an Iterable be satisfied by
   * the specified assertion.
   */
  def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      TestArrow.fromFunction[Iterable[A], Int](_.size).withCode("hasSize") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the size of a string be satisfied by
   * the specified assertion.
   */
  def hasSizeString(assertion: Assertion[Int]): Assertion[String] =
    SmartAssertion[String](
      TestArrow.fromFunction[String, Int](_.length).withCode("hasSizeString") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the intersection of two Iterables
   * satisfy the given assertion.
   */
  def hasIntersection[A](other: Iterable[A])(assertion: Assertion[Iterable[A]]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      TestArrow
        .fromFunction[Iterable[A], Iterable[A]](value => value.toSeq.intersect(other.toSeq))
        .withCode("hasIntersection") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable contain at least one of the
   * specified elements.
   */
  def hasAtLeastOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isGreaterThanEqualTo(1)))

  /**
   * Makes a new assertion that requires an Iterable contain at most one of the
   * specified elements.
   */
  def hasAtMostOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isLessThanEqualTo(1)))

  /**
   * Makes a new assertion that requires an Iterable to contain the first
   * element satisfying the given assertion.
   */
  def hasFirst[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      SmartAssertions.head.withCode("hasFirst") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable to contain the last element
   * satisfying the given assertion.
   */
  def hasLast[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      SmartAssertions.last.withCode("hasLast") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable to be empty.
   */
  val isEmpty: Assertion[Iterable[Any]] =
    SmartAssertion[Iterable[Any]](
      SmartAssertions.isEmptyIterable.withCode("isEmpty")
    )

  /**
   * Makes a new assertion that requires a given string to be empty.
   */
  val isEmptyString: Assertion[String] =
    SmartAssertion[String](
      SmartAssertions.isEmptyString.withCode("isEmptyString")
    )

  /**
   * Makes a new assertion that requires a Map to have the specified key with
   * value satisfying the specified assertion.
   */
  def hasKey[K, V](key: K, assertion: Assertion[V]): Assertion[Map[K, V]] =
    SmartAssertion[Map[K, V]](
      SmartAssertions.hasKey[K, V](key).withCode("hasKey") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires a Map to have the specified key.
   */
  def hasKey[K, V](key: K): Assertion[Map[K, V]] =
    hasKey(key, anything)

  /**
   * Makes a new assertion that requires a Map have keys satisfying the
   * specified assertion.
   */
  def hasKeys[K, V](assertion: Assertion[Iterable[K]]): Assertion[Map[K, V]] =
    SmartAssertion[Map[K, V]](
      TestArrow.make[Map[K, V], Iterable[K]](map => Trace.succeed(map.keys)).withCode("hasKeys") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires an Iterable contain none of the
   * specified elements.
   */
  def hasNoneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(isEmpty)

  /**
   * Makes a new assertion that requires an Iterable contain exactly one of the
   * specified elements.
   */
  def hasOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(equalTo(1)))

  /**
   * Makes a new assertion that requires an Iterable to have the same elements
   * as the specified Iterable, though not necessarily in the same order.
   */
  def hasSameElements[A](other: Iterable[A]): Assertion[Iterable[A]] =
    SmartAssertion[Iterable[A]](
      TestArrow
        .make[Iterable[A], Boolean] { actual =>
          val actualSeq = actual.toSeq
          val otherSeq  = other.toSeq

          val result = actualSeq.diff(otherSeq).isEmpty && otherSeq.diff(actualSeq).isEmpty
          Trace.boolean(result) {
            M.pretty(actualSeq) + M.had + "the same elements as " + M.pretty(otherSeq)
          }

        }
        .withCode("hasSameElements")
    )

  /**
   * Makes a new assertion that requires the specified Iterable to be a subset
   * of the other Iterable.
   */
  def hasSubset[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSameElements(other))

  /**
   * Makes a new assertion that requires a Map have values satisfying the
   * specified assertion.
   */
  def hasValues[K, V](assertion: Assertion[Iterable[V]]): Assertion[Map[K, V]] =
    SmartAssertion[Map[K, V]](
      TestArrow.fromFunction((_: Map[K, V]).values).withCode("hasValues") >>> assertion.arrow
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
    SmartAssertion[Sum](
      TestArrow
        .make[Sum, Proj] { sum =>
          Trace.option(term(sum)) {
            M.pretty(sum) + M.was + "a case of " + termName
          }
        }
        .withCode("isCase") >>> assertion.arrow
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

    SmartAssertion[Iterable[Any]](
      TestArrow
        .make[Iterable[Any], Boolean] { as =>
          Trace.boolean(loop(as.iterator, Set.empty)) {
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
    SmartAssertion[String](
      SmartAssertions.isNonEmptyString
        .withCode("isNonEmptyString")
    )

  /**
   * Makes a new assertion that requires a null value.
   */
  val isNull: Assertion[Any] =
    SmartAssertion[Any](
      TestArrow
        .make[Any, Boolean] { x =>
          Trace.boolean(x == null) {
            M.pretty(x) + M.was + "null"
          }
        }
        .withCode("isNull")
    )

  /**
   * Makes a new assertion that requires a numeric value is zero.
   */
  def isZero[A](implicit num: Numeric[A]): Assertion[A] =
    SmartAssertion[A](
      TestArrow
        .make[A, Boolean] { x =>
          Trace.boolean(num.zero == x) {
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
    SmartAssertion[String](
      TestArrow
        .make[String, Boolean] { s =>
          Trace.boolean(s.matches(regex)) {
            M.pretty(s) + M.did + "match" + M.pretty(regex)
          }
        }
        .withCode("matchesRegex")
    )

  /**
   * Makes a new assertion that requires a numeric value is non negative.
   */
  def nonNegative[A](implicit num: Numeric[A]): Assertion[A] =
    isGreaterThanEqualTo(num.zero)

  /**
   * Makes a new assertion that requires a numeric value is non positive.
   */
  def nonPositive[A](implicit num: Numeric[A]): Assertion[A] =
    isLessThanEqualTo(num.zero)

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  def not[A](assertion: Assertion[A]): Assertion[A] =
    assertion.negate

  /**
   * Makes a new assertion that always fails.
   */
  val nothing: Assertion[Any] =
    SmartAssertion[Any](
      TestArrow
        .make[Any, Boolean] { _ =>
          Trace.boolean(false) {
            M.custom("failure")
          }
        }
        .withCode("nothing")
    )

  /**
   * Makes a new assertion that requires a given sequence to start with the
   * specified prefix.
   */
  def startsWith[A](prefix: Seq[A]): Assertion[Seq[A]] =
    SmartAssertion[Seq[A]](
      SmartAssertions
        .startsWithSeq(prefix)
        .withCode("startsWith")
    )

  /**
   * Makes a new assertion that requires a given string to start with a
   * specified prefix.
   */
  def startsWithString(prefix: String): Assertion[String] =
    SmartAssertion[String](
      SmartAssertions
        .startsWithString(prefix)
        .withCode("startsWithString")
    )

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  def succeeds[A](assertion: Assertion[A]): Assertion[Exit[Any, A]] =
    SmartAssertion {
      TestArrow.make { exit =>
        Trace.boolean(exit.isSuccess) {
          M.custom("succeeds")
        }
      }
    }

  /**
   * Makes a new assertion that requires the expression to throw.
   */
  def throws[A](assertion: Assertion[Throwable]): Assertion[A] =
    SmartAssertion[A](
      SmartAssertions.throws.withCode("throws") >>> assertion.arrow
    )

  /**
   * Makes a new assertion that requires the expression to throw an instance of
   * given type (or its subtype).
   */
  def throwsA[E: ClassTag]: Assertion[Any] =
    throws(isSubtype[E](anything))

}
