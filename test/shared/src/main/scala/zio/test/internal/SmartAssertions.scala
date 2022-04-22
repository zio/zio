package zio.test.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio._
import zio.test.diff.Diff

import scala.reflect.ClassTag
import scala.util.Try
import zio.test.{ErrorMessage => M, SmartAssertionOps => _, _}

object SmartAssertions {

  def anything: TestArrow[Any, Boolean] =
    TestArrow.make[Any, Boolean](_ => Trace.boolean(true)(M.was + "anything"))

  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): TestArrow[A, Boolean] =
    TestArrow.make[A, Boolean] { actual =>
      val referenceType = implicitly[Numeric[A]]
      val max           = referenceType.plus(reference, tolerance)
      val min           = referenceType.minus(reference, tolerance)

      val result = referenceType.gteq(actual, min) && referenceType.lteq(actual, max)
      Trace.boolean(result) {
        M.pretty(actual) + M.did + "approximately equal" + M.pretty(reference) + "with a tolerance of" +
          M.pretty(tolerance)
      }
    }

  def custom[A, B](customAssertion: CustomAssertion[A, B]): TestArrow[A, B] =
    TestArrow.make { a =>
      customAssertion.run(a) match {
        case Left(error)  => Trace.fail(error)
        case Right(value) => Trace.succeed(value)
      }
    }

  def isSome[A]: TestArrow[Option[A], A] =
    TestArrow
      .make[Option[A], A] {
        case Some(value) => Trace.succeed(value)
        case None        => Trace.fail("Option was None")
      }

  def asRight[A]: TestArrow[Either[_, A], A] =
    TestArrow
      .make[Either[_, A], A] {
        case Right(value) => Trace.succeed(value)
        case Left(_)      => Trace.fail("Either was Left")
      }

  def asLeft[A]: TestArrow[Either[A, _], A] =
    TestArrow
      .make[Either[A, _], A] {
        case Left(value) => Trace.succeed(value)
        case Right(_)    => Trace.fail("Either was Right")
      }

  def isEmptyIterable[A]: TestArrow[Iterable[A], Boolean] =
    TestArrow
      .make[Iterable[A], Boolean] { as =>
        Trace.boolean(as.isEmpty) {
          className(as) + M.was + "empty" + M.text(s"(size ${as.size})")
        }
      }

  def isEmptyString: TestArrow[String, Boolean] =
    TestArrow
      .make[String, Boolean] { string =>
        Trace.boolean(string.isEmpty) {
          M.pretty(string) + M.was + "empty" + M.text(s"(length ${string.length})")
        }
      }

  def isNonEmptyIterable: TestArrow[Iterable[Any], Boolean] =
    TestArrow
      .make[Iterable[Any], Boolean] { as =>
        Trace.boolean(as.nonEmpty) {
          className(as) + M.choice("was not", "was") + "empty"
        }
      }

  def isNonEmptyString: TestArrow[String, Boolean] =
    TestArrow
      .make[String, Boolean] { string =>
        Trace.boolean(string.nonEmpty) {
          M.pretty(string) + M.choice("was not", "was") + "empty"
        }
      }

  def isEmptyOption: TestArrow[Option[Any], Boolean] =
    TestArrow
      .make[Option[Any], Boolean] { option =>
        Trace.boolean(option.isEmpty) {
          className(option) + M.was + "empty"
        }
      }

  def isDefinedOption: TestArrow[Option[Any], Boolean] =
    TestArrow
      .make[Option[Any], Boolean] { option =>
        Trace.boolean(option.isDefined) {
          className(option) + M.was + "defined"
        }
      }

  def forallIterable[A](predicate: TestArrow[A, Boolean]): TestArrow[Iterable[A], Boolean] =
    TestArrow
      .make[Iterable[A], Boolean] { seq =>
        val results = seq.map(a => TestArrow.run(predicate, Right(a)))

        val failures = results.filter(_.isFailure)
        val elements = if (failures.size == 1) "element" else "elements"

        Trace.Node(
          Result.succeed(failures.isEmpty),
          M.pretty(failures.size) + M.choice(s"$elements failed the predicate", s"$elements failed the predicate"),
          children = if (failures.isEmpty) None else Some(failures.reduce(_ && _))
        )
      }

  def existsIterable[A](predicate: TestArrow[A, Boolean]): TestArrow[Iterable[A], Boolean] =
    TestArrow
      .make[Iterable[A], Boolean] { seq =>
        val results = seq.map(a => TestArrow.run(predicate, Right(a)))

        val successes = results.filter(_.isSuccess)
        val elements  = if (successes.size == 1) "element" else "elements"

        Trace.Node(
          Result.succeed(successes.nonEmpty),
          M.pretty(successes.size) + M
            .choice(s"$elements satisfied the predicate", s"$elements satisfied the predicate"),
          children = if (successes.isEmpty) None else Some(successes.reduce(_ && _))
        )
      }

  def containsSeq[A](value: A): TestArrow[Seq[A], Boolean] =
    TestArrow
      .make[Seq[A], Boolean] { seq =>
        Trace.boolean(seq.contains(value)) {
          className(seq) + M.did + "contain" + M.pretty(value)
        }
      }

  def containsIterable[A](value: A): TestArrow[Iterable[A], Boolean] =
    TestArrow
      .make[Iterable[A], Boolean] { seq =>
        Trace.boolean(seq.exists(_ == value)) {
          className(seq) + M.did + "contain" + M.pretty(value)
        }
      }

  def containsCause[E](expected: Cause[E]): TestArrow[Cause[E], Boolean] =
    TestArrow
      .make[Cause[E], Boolean] { cause =>
        Trace.boolean(cause.contains(expected)) {
          M.pretty(cause) + M.did + "contain" + M.pretty(expected)
        }
      }

  def containsOption[A](value: A): TestArrow[Option[A], Boolean] =
    TestArrow
      .make[Option[A], Boolean] { option =>
        Trace.boolean(option.contains(value)) {
          className(option) + M.did + "contain" + M.pretty(value)
        }
      }

  def containsString(value: String): TestArrow[String, Boolean] =
    TestArrow
      .make[String, Boolean] { str =>
        Trace.boolean(str.contains(value)) {
          M.pretty(str) + M.did + "contain" + M.pretty(value)
        }
      }

  def hasAt[A](index: Int): TestArrow[Seq[A], A] =
    TestArrow
      .make[Seq[A], A] { as =>
        Try(as(index)).toOption match {
          case Some(value) => Trace.succeed(value)
          case None =>
            Trace.fail(
              M.text("Invalid index") + M.value(index) + "for" + className(as) + "of size" + M.value(as.length)
            )
        }
      }

  def hasField[A, B](name: String, proj: A => B): TestArrow[A, B] =
    TestArrow
      .make[A, B] { a =>
        Trace.succeed(proj(a))
      }

  def hasKey[K, V](key: K): TestArrow[Map[K, V], V] =
    TestArrow
      .make[Map[K, V], V] { mapKV =>
        Try(mapKV(key)).toOption match {
          case Some(value) => Trace.succeed(value)
          case None =>
            Trace.fail(
              M.text("Missing key") + M.pretty(key)
            )
        }
      }

  def head[A]: TestArrow[Iterable[A], A] =
    TestArrow
      .make[Iterable[A], A] { as =>
        as.headOption match {
          case Some(value) => Trace.succeed(value)
          case None =>
            Trace.fail(className(as) + "was empty")
        }
      }

  def last[A]: TestArrow[Iterable[A], A] =
    TestArrow
      .make[Iterable[A], A] { as =>
        as.lastOption match {
          case Some(value) => Trace.succeed(value)
          case None        => Trace.fail(className(as) + "was empty")
        }
      }

  def isEven[A](implicit integral: Integral[A]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(integral.rem(a, integral.fromInt(2)) == integral.fromInt(0)) {
          M.pretty(a) + M.was + "even"
        }
      }

  def isOdd[A](implicit integral: Integral[A]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(integral.rem(a, integral.fromInt(2)) == integral.fromInt(1)) {
          M.pretty(a) + M.was + "odd"
        }
      }

  def greaterThan[A](that: A)(implicit ordering: Ordering[A]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(ordering.gt(a, that)) {
          M.pretty(a) + M.was + "greater than" + M.pretty(that)
        }
      }

  def greaterThanOrEqualTo[A](that: A)(implicit ordering: Ordering[A]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { a =>
        Trace.boolean(ordering.gteq(a, that)) {
          M.pretty(a) + M.was + s"greater than or equal to" + M.pretty(that)
        }
      }

  def lessThan[A](that: A)(implicit ordering: Ordering[A]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { a =>
        Trace.boolean(ordering.lt(a, that)) {
          M.pretty(a) + M.was + "less than" + M.pretty(that)
        }
      }

  def lessThanOrEqualTo[A](that: A)(implicit ordering: Ordering[A]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { a =>
        Trace.boolean(ordering.lteq(a, that)) {
          M.pretty(a) + M.was + "less than or equal to" + M.pretty(that)
        }
      }

  def equalTo[A](that: A)(implicit diff: OptionalImplicit[Diff[A]]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { a =>
        val result = (a, that) match {
          case (a: Array[_], that: Array[_]) => a.sameElements[Any](that)
          case _                             => a == that
        }

        Trace.boolean(result) {
          diff.value match {
            case Some(diff) if !diff.isLowPriority && !result =>
              M.custom(ConsoleUtils.underlined("Expected")) ++ M.pretty(that) ++
                M.custom(ConsoleUtils.underlined("Diff")) ++
                M.custom(ConsoleUtils.red(diff.diff(that, a).render))
            case _ =>
              M.pretty(a) + M.equals + M.pretty(that)
          }
        }
      }

  def asCauseDie[E]: TestArrow[Cause[E], Throwable] =
    TestArrow
      .make[Cause[E], Throwable] {
        case cause if cause.dieOption.isDefined =>
          Trace.succeed(cause.dieOption.get)
        case _ =>
          Trace.fail(M.value("Cause") + M.did + "contain a" + M.value("Die"))
      }

  def asCauseFailure[E]: TestArrow[Cause[E], E] =
    TestArrow
      .make[Cause[E], E] {
        case cause if cause.failureOption.isDefined =>
          Trace.succeed(cause.failureOption.get)
        case _ =>
          Trace.fail(M.value("Cause") + M.did + "contain a" + M.value("Fail"))
      }

  def asCauseInterrupted[E]: TestArrow[Cause[E], Boolean] =
    TestArrow
      .make[Cause[E], Boolean] {
        case cause if cause.isInterrupted =>
          Trace.succeed(true)
        case _ =>
          Trace.fail(M.value("Cause") + M.did + "contain a" + M.value("Interrupt"))
      }

  def asExitDie[E, A]: TestArrow[Exit[E, A], Throwable] =
    TestArrow
      .make[Exit[E, A], Throwable] {
        case Exit.Failure(cause) if cause.dieOption.isDefined =>
          Trace.succeed(cause.dieOption.get)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.was + "a" + M.value("Cause.Die"))
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit.Failure") + M.did + "contain a" + M.value("Cause.Die"))
      }

  def asExitCause[E]: TestArrow[Exit[E, Any], Cause[E]] =
    TestArrow
      .make[Exit[E, Any], Cause[E]] {
        case Exit.Failure(cause) =>
          Trace.succeed(cause)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.did + "contain a" + M.value("Cause"))
      }

  def asExitFailure[E]: TestArrow[Exit[E, Any], E] =
    TestArrow
      .make[Exit[E, Any], E] {
        case Exit.Failure(cause) if cause.failureOption.isDefined =>
          Trace.succeed(cause.failureOption.get)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.was + "a" + M.value("Failure"))
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit.Failure") + M.did + "contain a" + M.value("Cause.Fail"))
      }

  def asExitInterrupted[E, A]: TestArrow[Exit[E, A], Boolean] =
    TestArrow
      .make[Exit[E, A], Boolean] {
        case Exit.Failure(cause) if cause.isInterrupted =>
          Trace.succeed(true)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.was + "interrupted")
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit.Failure") + M.did + "contain a" + M.value("Cause.Interrupt"))
      }

  def asExitSuccess[E, A]: TestArrow[Exit[E, A], A] =
    TestArrow
      .make[Exit[E, A], A] {
        case Exit.Success(value) =>
          Trace.succeed(value)
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit") + M.was + "a" + M.value("Success"))
      }

  val throws: TestArrow[Any, Throwable] =
    TestArrow.makeEither(
      Trace.succeed,
      _ => Trace.fail("Expected failure")
    )

  def as[A, B](implicit CB: ClassTag[B]): TestArrow[A, B] =
    TestArrow
      .make[A, B] { a =>
        CB.unapply(a) match {
          case Some(value) => Trace.succeed(value)
          case None        => Trace.fail(M.value(a.getClass.getSimpleName) + "is not an instance of" + M.value(className(CB)))
        }
      }

  def is[A, B](implicit CB: ClassTag[B]): TestArrow[A, Boolean] =
    TestArrow
      .make[A, Boolean] { a =>
        Trace.boolean(CB.unapply(a).isDefined) {
          M.value(a.getClass.getSimpleName) + M.was + "an instance of" + M.value(className(CB))
        }
      }

  def startsWithSeq[A](prefix: Seq[A]): TestArrow[Seq[A], Boolean] =
    TestArrow
      .make[Seq[A], Boolean] { seq =>
        Trace.boolean(seq.startsWith(prefix)) {
          M.pretty(seq) + M.did + "start with" + M.pretty(prefix)
        }
      }

  def startsWithString(prefix: String): TestArrow[String, Boolean] =
    TestArrow
      .make[String, Boolean] { string =>
        Trace.boolean(string.startsWith(prefix)) {
          M.value(string) + M.did + "start with" + M.value(prefix)
        }
      }

  def endsWithSeq[A](postfix: Seq[A]): TestArrow[Seq[A], Boolean] =
    TestArrow
      .make[Seq[A], Boolean] { seq =>
        Trace.boolean(seq.endsWith(postfix)) {
          M.value(seq) + M.did + "end with" + M.value(seq)
        }
      }

  def endsWithString(postfix: String): TestArrow[String, Boolean] =
    TestArrow
      .make[String, Boolean] { string =>
        Trace.boolean(string.endsWith(postfix)) {
          M.value(string) + M.did + "end with" + M.value(postfix)
        }
      }

  private def className[A](C: ClassTag[A]): String =
    try {
      C.runtimeClass.getSimpleName
    } catch {
      // See https://github.com/scala/bug/issues/2034.
      case t: InternalError if t.getMessage == "Malformed class name" =>
        C.runtimeClass.getName
    }

  private def className[A](a: Iterable[A]) =
    M.value(a.toString.takeWhile(_ != '('))

  private def className[A](a: Option[A]) =
    M.value(a.toString.takeWhile(_ != '('))
}
