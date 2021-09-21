package zio.test.internal

import zio.test._
import zio.test.diff.Diff
import zio.{Cause, Exit, Fiber}

import scala.reflect.ClassTag
import scala.util.Try

object SmartAssertions {
  import zio.test.{ErrorMessage => M}

  def runSmartAssertion[A]: Arrow[SmartAssertion[A], A] =
    Arrow.make[SmartAssertion[A], A] { smartAssertion =>
      smartAssertion.result() match {
        case Left(error)  => Trace.fail(error)
        case Right(value) => Trace.succeed(value).withMessage(smartAssertion.error)
      }
    }

  def isSome[A]: Arrow[Option[A], A] =
    Arrow
      .make[Option[A], A] {
        case Some(value) => Trace.succeed(value)
        case None        => Trace.fail("Option was None")
      }

  def asRight[A]: Arrow[Either[_, A], A] =
    Arrow
      .make[Either[_, A], A] {
        case Right(value) => Trace.succeed(value)
        case Left(_)      => Trace.fail("Either was Left")
      }

  def asLeft[A]: Arrow[Either[A, _], A] =
    Arrow
      .make[Either[A, _], A] {
        case Left(value) => Trace.succeed(value)
        case Right(_)    => Trace.fail("Either was Right")
      }

  def isEmptyIterable[A]: Arrow[Iterable[A], Boolean] =
    Arrow
      .make[Iterable[A], Boolean] { as =>
        Trace.boolean(as.isEmpty) {
          className(as) + M.was + "empty" + M.text(s"(size ${as.size})")
        }
      }

  def isNonEmptyIterable[A]: Arrow[Iterable[A], Boolean] =
    Arrow
      .make[Iterable[A], Boolean] { as =>
        Trace.boolean(as.nonEmpty) {
          className(as) + M.choice("was not", "was") + "empty"
        }
      }

  def isEmptyOption[A]: Arrow[Option[A], Boolean] =
    Arrow
      .make[Option[A], Boolean] { option =>
        Trace.boolean(option.isEmpty) {
          className(option) + M.was + "empty"
        }
      }

  def isDefinedOption[A]: Arrow[Option[A], Boolean] =
    Arrow
      .make[Option[A], Boolean] { option =>
        Trace.boolean(option.isDefined) {
          className(option) + M.was + "defined"
        }
      }

  def forallIterable[A](predicate: Arrow[A, Boolean]): Arrow[Iterable[A], Boolean] =
    Arrow
      .make[Iterable[A], Boolean] { seq =>
        val results = seq.map(a => Arrow.run(predicate, Right(a)))

        val failures = results.filter(_.isFailure)
        val elements = if (failures.size == 1) "element" else "elements"

        Trace.Node(
          Result.succeed(failures.isEmpty),
          M.value(failures.size) + M.choice(s"$elements failed the predicate", s"$elements failed the predicate"),
          children = if (failures.isEmpty) None else Some(failures.reduce(_ && _))
        )
      }

  def existsIterable[A](predicate: Arrow[A, Boolean]): Arrow[Iterable[A], Boolean] =
    Arrow
      .make[Iterable[A], Boolean] { seq =>
        val results = seq.map(a => Arrow.run(predicate, Right(a)))

        val successes = results.filter(_.isSuccess)
        val elements  = if (successes.size == 1) "element" else "elements"

        Trace.Node(
          Result.succeed(successes.nonEmpty),
          M.value(successes.size) + M
            .choice(s"$elements satisfied the predicate", s"$elements satisfied the predicate"),
          children = if (successes.isEmpty) None else Some(successes.reduce(_ && _))
        )
      }

  def containsSeq[A](value: A): Arrow[Seq[A], Boolean] =
    Arrow
      .make[Seq[A], Boolean] { seq =>
        Trace.boolean(seq.contains(value)) {
          className(seq) + M.did + "contain" + M.pretty(value)
        }
      }

  def containsOption[A](value: A): Arrow[Option[A], Boolean] =
    Arrow
      .make[Option[A], Boolean] { option =>
        Trace.boolean(option.contains(value)) {
          className(option) + M.did + "contain" + M.pretty(value)
        }
      }

  def containsString(value: String): Arrow[String, Boolean] =
    Arrow
      .make[String, Boolean] { str =>
        Trace.boolean(str.contains(value)) {
          M.pretty(str) + M.did + "contain" + M.pretty(value)
        }
      }

  def hasAt[A](index: Int): Arrow[Seq[A], A] =
    Arrow
      .make[Seq[A], A] { as =>
        Try(as(index)).toOption match {
          case Some(value) => Trace.succeed(value)
          case None =>
            Trace.fail(
              M.text("Invalid index") + M.value(index) + "for" + className(as) + "of size" + M.value(as.length)
            )
        }
      }

  def hasKey[K, V](key: K): Arrow[Map[K, V], V] =
    Arrow
      .make[Map[K, V], V] { mapKV =>
        Try(mapKV(key)).toOption match {
          case Some(value) => Trace.succeed(value)
          case None =>
            Trace.fail(
              M.text("Missing key") + M.pretty(key)
            )
        }
      }

  def head[A]: Arrow[Iterable[A], A] =
    Arrow
      .make[Iterable[A], A] { as =>
        as.headOption match {
          case Some(value) => Trace.succeed(value)
          case None =>
            Trace.fail(className(as) + "was empty")
        }
      }

  def isEven[A](implicit integral: Integral[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(integral.rem(a, integral.fromInt(2)) == integral.fromInt(0)) {
          M.pretty(a) + M.was + "even"
        }
      }

  def isOdd[A](implicit integral: Integral[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(integral.rem(a, integral.fromInt(2)) == integral.fromInt(1)) {
          M.pretty(a) + M.was + "odd"
        }
      }

  def greaterThan[A](that: A)(implicit ordering: Ordering[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(ordering.gt(a, that)) {
          M.pretty(a) + M.was + "greater than" + M.pretty(that)
        }
      }

  def greaterThanOrEqualTo[A](that: A)(implicit ordering: Ordering[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { a =>
        Trace.boolean(ordering.gteq(a, that)) {
          M.pretty(a) + M.was + s"greater than or equal to" + M.pretty(that)
        }
      }

  def lessThan[A](that: A)(implicit ordering: Ordering[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { a =>
        Trace.boolean(ordering.lt(a, that)) {
          M.pretty(a) + M.was + "less than" + M.pretty(that)
        }
      }

  def lessThanOrEqualTo[A](that: A)(implicit ordering: Ordering[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { a =>
        Trace.boolean(ordering.lteq(a, that)) {
          M.pretty(a) + M.was + "less than or equal to" + M.pretty(that)
        }
      }

  def notEqualTo[A](that: A)(implicit diff: OptionalImplicit[Diff[A]]): Arrow[A, Boolean] =
    !equalTo[A](that)

  def equalTo[A](that: A)(implicit diff: OptionalImplicit[Diff[A]]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { a =>
        val result = (a, that) match {
          case (a: Array[_], that: Array[_]) => a.sameElements[Any](that)
          case _                             => a == that
        }

        Trace.boolean(result) {
          diff.value match {
            case Some(diff) if !diff.isLowPriority && !result =>
              M.custom(ConsoleUtils.underlined("Expected")) + "\n" +/ M.pretty(that) + "\n" +/
                M.custom(ConsoleUtils.underlined("Diff")) + "\n" +/
                M.custom(ConsoleUtils.red(diff.diff(that, a).render))
            case _ =>
              M.pretty(a) + M.equals + M.pretty(that)
          }
        }
      }

  val throws: Arrow[Any, Throwable] =
    Arrow.makeEither(
      Trace.succeed,
      _ => Trace.fail("Expected failure")
    )

  def asCauseDie[E]: Arrow[Cause[E], Throwable] =
    Arrow
      .make[Cause[E], Throwable] {
        case cause if cause.dieOption.isDefined =>
          Trace.succeed(cause.dieOption.get)
        case _ =>
          Trace.fail(M.value("Cause") + M.did + "contain a" + M.value("Die"))
      }

  def asCauseFail[E]: Arrow[Cause[E], E] =
    Arrow
      .make[Cause[E], E] {
        case cause if cause.failureOption.isDefined =>
          Trace.succeed(cause.failureOption.get)
        case _ =>
          Trace.fail(M.value("Cause") + M.did + "contain a" + M.value("Fail"))
      }

  def asCauseInterrupt[E]: Arrow[Cause[E], Fiber.Id] =
    Arrow
      .make[Cause[E], Fiber.Id] {
        case cause if cause.interruptors.nonEmpty =>
          Trace.succeed(cause.interruptors.head)
        case _ =>
          Trace.fail(M.value("Cause") + M.did + "contain a" + M.value("Interrupt"))
      }

  def asExitDie[E, A]: Arrow[Exit[E, A], Throwable] =
    Arrow
      .make[Exit[E, A], Throwable] {
        case Exit.Failure(cause) if cause.dieOption.isDefined =>
          Trace.succeed(cause.dieOption.get)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.was + "a" + M.value("Cause.Die"))
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit.Failure") + M.did + "contain a" + M.value("Cause.Die"))
      }

  def asExitCause[E, A]: Arrow[Exit[E, A], Cause[E]] =
    Arrow
      .make[Exit[E, A], Cause[E]] {
        case Exit.Failure(cause) =>
          Trace.succeed(cause)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.did + "contain a" + M.value("Cause"))
      }

  def asExitFail[E, A]: Arrow[Exit[E, A], E] =
    Arrow
      .make[Exit[E, A], E] {
        case Exit.Failure(cause) if cause.failureOption.isDefined =>
          Trace.succeed(cause.failureOption.get)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.was + "a" + M.value("Failure"))
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit.Failure") + M.did + "contain a" + M.value("Cause.Fail"))
      }

  def asExitInterrupt[E, A]: Arrow[Exit[E, A], Fiber.Id] =
    Arrow
      .make[Exit[E, A], Fiber.Id] {
        case Exit.Failure(cause) if cause.interruptors.nonEmpty =>
          Trace.succeed(cause.interruptors.head)
        case Exit.Success(_) =>
          Trace.fail(M.value("Exit.Success") + M.was + "interrupted")
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit.Failure") + M.did + "contain a" + M.value("Cause.Interrupt"))
      }

  def asExitSuccess[E, A]: Arrow[Exit[E, A], A] =
    Arrow
      .make[Exit[E, A], A] {
        case Exit.Success(value) =>
          Trace.succeed(value)
        case Exit.Failure(_) =>
          Trace.fail(M.value("Exit") + M.was + "a" + M.value("Success"))
      }

  def as[A, B](implicit CB: ClassTag[B]): Arrow[A, B] =
    Arrow
      .make[A, B] { a =>
        CB.unapply(a) match {
          case Some(value) => Trace.succeed(value)
          case None        => Trace.fail(M.value(a.getClass.getSimpleName) + "is not an instance of" + M.value(className(CB)))
        }
      }

  def is[A, B](implicit CB: ClassTag[B]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { a =>
        Trace.boolean(CB.unapply(a).isDefined) {
          M.value(a.getClass.getSimpleName) + M.is + "an instance of" + M.value(className(CB))
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
