package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.language.implicitConversions
import zio.{Chunk, ChunkBuilder, Trace, ZIO, nonFatal}
import zio.internal.stacktracer.SourceLocation
import zio.test.Assertion.Arguments

import scala.annotation.tailrec
import scala.util.control.TailCalls

/**
 * The result of an assertion: what `assertTrue`, `assert` and `Assertion#run`
 * return, and what a test returns. Combine results with `&&`, `||`, `!`,
 * `implies` and `iff`.
 *
 * A `TestResult` can also be used where a `ZIO` is expected, through
 * `TestResult.liftTestResultToZIO`: a failing result then stops the effect, and
 * the test reports it as an assertion failure.
 *
 * {{{
 * import zio.ZIO
 *
 * test("results combine") {
 *   for {
 *     n <- ZIO.succeed(4)
 *     _ <- assertTrue(n > 0) // the test stops here when this fails
 *   } yield assertTrue(n % 2 == 0) && assertTrue(n < 10)
 * }
 * }}}
 *
 * @param arrow
 *   the [[TestArrow]] that computes the result
 */
case class TestResult(arrow: TestArrow[Any, Boolean]) { self =>

  /**
   * The trace of the assertion, computed the first time it is read.
   */
  lazy val result: TestTrace[Boolean] = TestArrow.run(arrow, Right(()))

  /**
   * The part of `result` that explains the failure, or `None` when the result
   * is a success.
   */
  lazy val failures: Option[TestTrace[Boolean]] = TestTrace.prune(result, false)

  /**
   * Returns `true` when the assertion failed.
   */
  def isFailure: Boolean = failures.isDefined

  /**
   * Returns `true` when the assertion succeeded.
   */
  def isSuccess: Boolean = failures.isEmpty

  /**
   * Combines this result with `that` by logical and: the combined result
   * succeeds when both succeed.
   */
  def &&(that: TestResult): TestResult = TestResult(arrow && that.arrow)

  /**
   * Combines this result with `that` by logical or: the combined result
   * succeeds when at least one succeeds. When either side is an `assertTrue`
   * whose expression threw an exception, the combined result fails, even if the
   * other side succeeds. A thrown exception inside `&&` that is then negated is
   * dropped when the other side of that `&&` fails, so `!(a && b) || c` can
   * succeed even though `a` threw.
   */
  def ||(that: TestResult): TestResult = TestResult(arrow || that.arrow)

  /**
   * Negates this result by logical not: the negated result succeeds when this
   * one fails. Negating an `assertTrue` whose expression threw an exception
   * still gives a failure. A thrown exception inside `&&` that is then negated
   * is dropped together with its other side when that side fails, so `!(a &&
   * b)` succeeds when `b` fails even though `a` threw.
   */
  def unary_! : TestResult = TestResult(!arrow)

  /**
   * Logical implication: the combined result succeeds when this result fails or
   * `that` succeeds, the same as `!this || that`. When either side is an
   * `assertTrue` whose expression threw an exception, the result fails. An
   * exception inside a negated `&&` on the right-hand side follows the rules of
   * `!`, so `a ==> !(b && c)` can succeed even though `b` threw.
   */
  def implies(that: TestResult): TestResult = !self || that

  /**
   * Logical implication; the same as `implies`.
   */
  def ==>(that: TestResult): TestResult = self.implies(that)

  /**
   * Logical equivalence: the combined result succeeds when both results succeed
   * or both fail. When either side is an `assertTrue` whose expression threw an
   * exception, the result fails.
   */
  def iff(that: TestResult): TestResult =
    (self ==> that) && (that ==> self)

  /**
   * Logical equivalence; the same as `iff`.
   */
  def <==>(that: TestResult): TestResult =
    self.iff(that)

  /**
   * Labels this result with `message`; the same as `label`.
   */
  def ??(message: String): TestResult = self.label(message)

  /**
   * Returns this result with `message` as its label. The label is part of the
   * failure report; it does not change whether the result succeeds.
   */
  def label(message: String): TestResult = TestResult(arrow.label(message))

  /**
   * Returns this result with `details` about the generated input that made it
   * fail. `check` and its variants call it on the result of each sample they
   * test.
   */
  def setGenFailureDetails(details: GenFailureDetails): TestResult =
    TestResult(arrow.setGenFailureDetails(details))
}

/**
 * Constructors and conversions for [[TestResult]].
 */
object TestResult {

  /**
   * Returns a result that succeeds when all the given results succeed.
   */
  def allSuccesses(assert: TestResult, asserts: TestResult*): TestResult = asserts.foldLeft(assert)(_ && _)

  /**
   * Returns a result that succeeds when all the given results succeed. An empty
   * collection gives a success.
   */
  def allSuccesses(asserts: Iterable[TestResult])(implicit trace: Trace, sourceLocation: SourceLocation): TestResult =
    allSuccesses(assertCompletes, asserts.toSeq: _*)

  /**
   * Returns a result that succeeds when at least one of the given results
   * succeeds.
   */
  def anySuccesses(assert: TestResult, asserts: TestResult*): TestResult = asserts.foldLeft(assert)(_ || _)

  /**
   * Returns a result that succeeds when at least one of the given results
   * succeeds. An empty collection gives a failure.
   */
  def anySuccesses(asserts: Iterable[TestResult])(implicit trace: Trace, sourceLocation: SourceLocation): TestResult =
    anySuccesses(!assertCompletes, asserts.toSeq: _*)

  /**
   * Converts a result to an effect, so that a [[TestResult]] can be a step of a
   * `for` comprehension over `ZIO`. A success becomes an effect that succeeds
   * with the result. A failure becomes an effect that dies; the test reports it
   * as an assertion failure, and the steps after it do not run.
   */
  implicit def liftTestResultToZIO(result: TestResult)(implicit trace: Trace): ZIO[Any, Nothing, TestResult] =
    if (result.isSuccess)
      ZIO.succeed(result)
    else
      ZIO.die(Exit(result))

  private[zio] final case class Exit(result: TestResult) extends Throwable

  /**
   * Returns a result that succeeds when all of `asserts` succeed. It throws an
   * `UnsupportedOperationException` when `asserts` is empty; `allSuccesses`
   * does not.
   */
  @deprecated("Use allSuccesses", "2.0.16")
  def all(asserts: TestResult*): TestResult = asserts.reduce(_ && _)

  /**
   * Returns a result that succeeds when at least one of `asserts` succeeds. It
   * throws an `UnsupportedOperationException` when `asserts` is empty;
   * `anySuccesses` does not.
   */
  @deprecated("Use anySuccesses", "2.0.16")
  def any(asserts: TestResult*): TestResult = asserts.reduce(_ || _)
}

/**
 * A step of an assertion: a function from an `A`, or from an exception thrown
 * by an earlier step, to a [[TestTrace]] of `B`, with the source code it came
 * from. `assertTrue` compiles each expression into a chain of arrows joined
 * with `>>>`, and an [[Assertion]] wraps an arrow to `Boolean`. `TestArrow.run`
 * runs one.
 */
sealed trait TestArrow[-A, +B] { self =>

  /**
   * Returns a description of this arrow built from the code recorded in it.
   * `Assertion#render` returns it.
   */
  def render: String = {
    val builder = ChunkBuilder.make[String]()

    @tailrec
    def loop(arrows: List[Either[String, TestArrow[_, _]]]): Unit =
      if (arrows.nonEmpty) {
        arrows.head match {
          case Right(TestArrow.And(left, right)) =>
            loop(Left("(") :: Right(left) :: Left(" && ") :: Right(right) :: Left(")") :: arrows.tail)
          case Right(TestArrow.Or(left, right)) =>
            loop(Left("(") :: Right(left) :: Left(" || ") :: Right(right) :: Left(")") :: arrows.tail)
          case Right(TestArrow.Not(arrow)) =>
            loop(Left("not") :: Left("(") :: Right(arrow) :: Left(")") :: arrows.tail)
          case Right(TestArrow.AndThen(left, right)) =>
            loop(Right(left) :: Left("(") :: Right(right) :: Left(")") :: arrows.tail)
          case Right(arrow: TestArrow.Meta[_, _]) =>
            val code = arrow.code.map { code =>
              if (arrow.codeArguments.nonEmpty) s"${code}(${arrow.codeArguments.mkString(", ")})" else code
            }
            builder += code.mkString
            loop(arrows.tail)
          case Right(arrow @ TestArrow.TestArrowF(_)) =>
            builder += arrow.toString
            loop(arrows.tail)
          case Right(arrow @ TestArrow.Suspend(_)) =>
            builder += arrow.toString
            loop(arrows.tail)
          case Left(str) =>
            builder += str
            loop(arrows.tail)
        }
      }

    loop(List(Right(self)))
    builder.result.mkString
  }

  /**
   * Labels this arrow with `message`; the same as `label`.
   */
  def ??(message: String): TestArrow[A, B] = self.label(message)

  /**
   * Returns this arrow with `message` as its label, for the failure report.
   */
  def label(message: String): TestArrow[A, B] = self.meta(customLabel = Some(message))

  /**
   * Returns this arrow with `details` about the generated input that made it
   * fail.
   */
  def setGenFailureDetails(details: GenFailureDetails): TestArrow[A, B] =
    self.meta(genFailureDetails = Some(details))

  import TestArrow._

  /**
   * Returns this arrow with the given metadata. An argument left as `None`
   * keeps the value this arrow already has.
   */
  def meta(
    span: Option[Span] = None,
    parentSpan: Option[Span] = None,
    code: Option[String] = None,
    location: Option[String] = None,
    completeCode: Option[String] = None,
    customLabel: Option[String] = None,
    genFailureDetails: Option[GenFailureDetails] = None
  ): TestArrow[A, B] = self match {
    case self: Meta[A, B] =>
      new Meta(
        self.arrow,
        span.orElse(self.span),
        parentSpan.orElse(self.parentSpan),
        code.orElse(self.code),
        location.orElse(self.location),
        completeCode.orElse(self.completeCode),
        customLabel.orElse(self.customLabel),
        genFailureDetails.orElse(self.genFailureDetails)
      ) {
        override def codeArguments: Chunk[Arguments] = self.codeArguments
      }
    case _ =>
      Meta(
        arrow = self,
        span = span,
        parentSpan = parentSpan,
        code = code,
        location = location,
        completeCode = completeCode,
        customLabel = customLabel,
        genFailureDetails = genFailureDetails
      )
  }

  /**
   * Returns this arrow with the start and end offsets of its code within the
   * source of the whole assertion.
   */
  def span(span: (Int, Int)): TestArrow[A, B] =
    meta(span = Some(Span(span._1, span._2)))

  /**
   * Returns this arrow with `code` as its source code.
   */
  def withCode(code: String): TestArrow[A, B] =
    meta(code = Some(code))

  /**
   * Returns this arrow with `code` as its source code and `arguments` as the
   * arguments recorded with it, replacing arguments set before.
   */
  def withCode(code: String, arguments: Arguments*): TestArrow[A, B] = self match {
    case self: Meta[A, B] =>
      new Meta(
        self.arrow,
        self.span,
        self.parentSpan,
        Some(code),
        self.location,
        self.completeCode,
        self.customLabel,
        self.genFailureDetails
      ) {
        override def codeArguments: Chunk[Arguments] = Chunk.fromIterable(arguments)
      }
    case _ =>
      new Meta(
        arrow = self,
        span = None,
        parentSpan = None,
        code = Some(code),
        location = None,
        completeCode = None,
        customLabel = None,
        genFailureDetails = None
      ) {
        override def codeArguments: Chunk[Arguments] = Chunk.fromIterable(arguments)
      }
  }

  /**
   * Returns this arrow with the source code of the whole assertion it belongs
   * to.
   */
  def withCompleteCode(completeCode: String): TestArrow[A, B] =
    meta(completeCode = Some(completeCode))

  /**
   * Returns this arrow with the file and line of `sourceLocation`.
   */
  def withLocation(implicit sourceLocation: SourceLocation): TestArrow[A, B] =
    meta(location = Some(s"${sourceLocation.path}:${sourceLocation.line}"))

  /**
   * Returns this arrow with the start and end offsets of the enclosing
   * expression within the source of the whole assertion.
   */
  def withParentSpan(span: (Int, Int)): TestArrow[A, B] =
    meta(parentSpan = Some(Span(span._1, span._2)))

  /**
   * Composes this arrow with `that`, which runs on the output of this arrow.
   * When this arrow fails, `that` does not run; when this arrow throws an
   * exception, `that` receives the exception instead of a value.
   */
  def >>>[C](that: TestArrow[B, C]): TestArrow[A, C] =
    AndThen[A, B, C](self, that)

  /**
   * Combines two Boolean arrows by logical and. Both arrows run on the input.
   */
  def &&[A1 <: A](that: TestArrow[A1, Boolean])(implicit ev: B <:< Boolean): TestArrow[A1, Boolean] =
    And(self.asInstanceOf[TestArrow[A1, Boolean]], that)

  /**
   * Combines two Boolean arrows by logical or. Both arrows run on the input.
   */
  def ||[A1 <: A](that: TestArrow[A1, Boolean])(implicit ev: B <:< Boolean): TestArrow[A1, Boolean] =
    Or(self.asInstanceOf[TestArrow[A1, Boolean]], that)

  /**
   * Negates a Boolean arrow by logical not.
   */
  def unary_![A1 <: A](implicit ev: B <:< Boolean): TestArrow[A1, Boolean] =
    Not(self.asInstanceOf[TestArrow[A1, Boolean]])
}

/**
 * Constructors of [[TestArrow]] values, and `run` to run one.
 */
object TestArrow {

  /**
   * An arrow that ignores its input and returns `value`. `value` is evaluated
   * each time the arrow runs; when it throws an exception, the arrow dies with
   * it.
   */
  def succeed[A](value: => A): TestArrow[Any, A] = TestArrowF(_ => TestTrace.succeed(value))

  /**
   * An arrow that applies `f` to its input. When `f` throws an exception, the
   * arrow dies with it.
   */
  def fromFunction[A, B](f: A => B): TestArrow[A, B] = make(f andThen TestTrace.succeed)

  /**
   * An arrow that builds the arrow to run from its input. When the input is an
   * exception from an earlier step, the arrow dies with it.
   */
  def suspend[A, B](f: A => TestArrow[Any, B]): TestArrow[A, B] = TestArrow.Suspend(f)

  /**
   * An arrow that returns the trace that `f` computes from its input. When the
   * input is an exception from an earlier step, the arrow dies with it.
   */
  def make[A, B](f: A => TestTrace[B]): TestArrow[A, B] =
    makeEither(e => TestTrace.die(e).annotate(TestTrace.Annotation.Rethrow), f)

  /**
   * An arrow that handles both kinds of input: a value with `onSucceed`, and an
   * exception from an earlier step with `onFail`.
   */
  def makeEither[A, B](onFail: Throwable => TestTrace[B], onSucceed: A => TestTrace[B]): TestArrow[A, B] =
    TestArrowF {
      case Left(error)  => onFail(error)
      case Right(value) => onSucceed(value)
    }

  private val onError: PartialFunction[Throwable, TailCalls.TailRec[TestTrace[Nothing]]] = {
    case ex if nonFatal(ex) =>
      ex.setStackTrace(ex.getStackTrace.filterNot { (ste: StackTraceElement) =>
        ste.getClassName.startsWith("zio.test.TestArrow")
      })
      TailCalls.done(TestTrace.die(ex))
  }

  private def runLoop[A, B](arrow: TestArrow[A, B], in: Either[Throwable, A]): TailCalls.TailRec[TestTrace[B]] =
    TailCalls.tailcall(arrow match {
      case TestArrowF(f) =>
        try TailCalls.done(f(in))
        catch onError

      case AndThen(f, g) =>
        runLoop(f, in).flatMap { t1 =>
          t1.result match {
            case _: Result.Fail.type   => TailCalls.done(t1.asInstanceOf[TestTrace[B]])
            case Result.Die(err)       => runLoop(g, Left(err)).map(t1 >>> _)
            case Result.Succeed(value) => runLoop(g, Right(value)).map(t1 >>> _)
          }
        }

      case And(lhs, rhs) =>
        for {
          l <- runLoop(lhs, in)
          r <- runLoop(rhs, in)
        } yield l && r

      case Or(lhs, rhs) =>
        for {
          l <- runLoop(lhs, in)
          r <- runLoop(rhs, in)
        } yield l || r

      case Not(arrow) =>
        runLoop(arrow, in).map(!_)

      case Suspend(f) =>
        in match {
          case Left(exception) =>
            TailCalls.done(TestTrace.die(exception))
          case Right(value) =>
            try runLoop(f(value), in)
            catch onError
        }

      case Meta(arrow, span, parentSpan, code, location, completeCode, customLabel, genFailureDetails) =>
        runLoop(arrow, in).map {
          _.withSpan(span)
            .withCode(code)
            .withParentSpan(parentSpan)
            .withLocation(location)
            .withCompleteCode(completeCode)
            .withCustomLabel(customLabel)
            .withGenFailureDetails(genFailureDetails)
        }
    })

  /**
   * Runs `arrow` on `in`, a value or an exception from an earlier step, and
   * returns the trace. A non-fatal exception thrown while running becomes a
   * trace that died with it.
   */
  def run[A, B](arrow: TestArrow[A, B], in: Either[Throwable, A]): TestTrace[B] =
    (try runLoop(arrow, in)
    catch onError).result

  /**
   * The start (inclusive) and end (exclusive) offsets of a piece of code within
   * the source of an assertion.
   */
  case class Span(start: Int, end: Int) {

    /**
     * Returns the part of `str` from `start` to `end`, after limiting both to
     * the bounds of `str`. It returns an empty string when `start` is not less
     * than `end`.
     */
    def substring(str: String): String = {
      val safeStart = math.max(0, math.min(start, str.length))
      val safeEnd   = math.max(safeStart, math.min(end, str.length))
      str.substring(safeStart, safeEnd)
    }
  }

  /**
   * An arrow with metadata about the code it came from: spans, code, location,
   * label and generator failure details.
   */
  sealed case class Meta[-A, +B](
    arrow: TestArrow[A, B],
    span: Option[Span],
    parentSpan: Option[Span],
    code: Option[String],
    location: Option[String],
    completeCode: Option[String],
    customLabel: Option[String],
    genFailureDetails: Option[GenFailureDetails]
  ) extends TestArrow[A, B] {

    /**
     * The arguments recorded with the code; empty unless set by `withCode`.
     */
    def codeArguments: Chunk[Arguments] = Chunk.empty
  }

  /**
   * An arrow defined by a function from a value, or an exception from an
   * earlier step, to a trace.
   */
  case class TestArrowF[-A, +B](f: Either[Throwable, A] => TestTrace[B]) extends TestArrow[A, B]

  /**
   * Two arrows composed with `>>>`.
   */
  case class AndThen[A, B, C](f: TestArrow[A, B], g: TestArrow[B, C]) extends TestArrow[A, C]

  /**
   * Two Boolean arrows combined with `&&`.
   */
  case class And[A](left: TestArrow[A, Boolean], right: TestArrow[A, Boolean]) extends TestArrow[A, Boolean]

  /**
   * Two Boolean arrows combined with `||`.
   */
  case class Or[A](left: TestArrow[A, Boolean], right: TestArrow[A, Boolean]) extends TestArrow[A, Boolean]

  /**
   * A Boolean arrow negated with `!`.
   */
  case class Not[A](arrow: TestArrow[A, Boolean]) extends TestArrow[A, Boolean]

  /**
   * An arrow that builds the arrow to run from its input; see `suspend`.
   */
  case class Suspend[A, B](f: A => TestArrow[Any, B]) extends TestArrow[A, B]
}
