package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import scala.language.implicitConversions
import zio.Trace
import zio.UIO
import zio.ZIO
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.Tracer.instance
import scala.util.control.NonFatal

case class TestResult(arrow: TestArrow[Any, Boolean]) { self =>

  lazy val result: TestTrace[Boolean] = TestArrow.run(arrow, Right(()))

  lazy val failures: Option[TestTrace[Boolean]] = TestTrace.prune(result, false)

  def isFailure: Boolean = failures.isDefined

  def isSuccess: Boolean = failures.isEmpty

  def &&(that: TestResult): TestResult = TestResult(arrow && that.arrow)

  def ||(that: TestResult): TestResult = TestResult(arrow || that.arrow)

  def unary_! : TestResult = TestResult(!arrow)

  def implies(that: TestResult): TestResult = !self || that

  def ==>(that: TestResult): TestResult = self.implies(that)

  def iff(that: TestResult): TestResult =
    (self ==> that) && (that ==> self)

  def <==>(that: TestResult): TestResult =
    self.iff(that)

  def ??(message: String): TestResult = self.label(message)

  def label(message: String): TestResult = TestResult(arrow.label(message))

  def setGenFailureDetails(details: GenFailureDetails): TestResult =
    TestResult(arrow.setGenFailureDetails(details))
}

object TestResult {
  def all(asserts: TestResult*): TestResult = asserts.reduce(_ && _)

  def any(asserts: TestResult*): TestResult = asserts.reduce(_ || _)

}

sealed trait TestArrow[-A, +B] { self =>
  def ??(message: String): TestArrow[A, B] = self.label(message)

  def label(message: String): TestArrow[A, B] = self.meta(customLabel = Some(message))

  def setGenFailureDetails(details: GenFailureDetails): TestArrow[A, B] =
    self.meta(genFailureDetails = Some(details))

  import TestArrow._

  def meta(
    span: Option[Span] = None,
    parentSpan: Option[Span] = None,
    code: Option[String] = None,
    location: Option[String] = None,
    completeCode: Option[String] = None,
    customLabel: Option[String] = None,
    genFailureDetails: Option[GenFailureDetails] = None
  ): TestArrow[A, B] = self match {
    case meta: Meta[A, B] =>
      meta.copy(
        span = meta.span.orElse(span),
        parentSpan = meta.parentSpan.orElse(parentSpan),
        code = code.orElse(meta.code),
        location = meta.location.orElse(location),
        completeCode = meta.completeCode.orElse(completeCode),
        customLabel = meta.customLabel.orElse(customLabel),
        genFailureDetails = meta.genFailureDetails.orElse(genFailureDetails)
      )
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

  def span(span: (Int, Int)): TestArrow[A, B] =
    meta(span = Some(Span(span._1, span._2)))

  def withCode(code: String): TestArrow[A, B] =
    meta(code = Some(code))

  def withCompleteCode(completeCode: String): TestArrow[A, B] =
    meta(completeCode = Some(completeCode))

  def withLocation(implicit trace: Trace): TestArrow[A, B] =
    trace match {
      case Trace(_, file, line) =>
        meta(location = Some(s"$file:$line"))
      case _ => self
    }

  def withParentSpan(span: (Int, Int)): TestArrow[A, B] =
    meta(parentSpan = Some(Span(span._1, span._2)))

  def >>>[C](that: TestArrow[B, C]): TestArrow[A, C] =
    AndThen[A, B, C](self, that)

  def &&[A1 <: A](that: TestArrow[A1, Boolean])(implicit ev: B <:< Boolean): TestArrow[A1, Boolean] =
    And(self.asInstanceOf[TestArrow[A1, Boolean]], that)

  def ||[A1 <: A](that: TestArrow[A1, Boolean])(implicit ev: B <:< Boolean): TestArrow[A1, Boolean] =
    Or(self.asInstanceOf[TestArrow[A1, Boolean]], that)

  def unary_![A1 <: A](implicit ev: B <:< Boolean): TestArrow[A1, Boolean] =
    Not(self.asInstanceOf[TestArrow[A1, Boolean]])
}

object TestArrow {

  def succeed[A](value: => A): TestArrow[Any, A] = TestArrowF(_ => TestTrace.succeed(value))

  def fromFunction[A, B](f: A => B): TestArrow[A, B] = make(f andThen TestTrace.succeed)

  def suspend[A, B](f: A => TestArrow[Any, B]): TestArrow[A, B] = TestArrow.Suspend(f)

  def make[A, B](f: A => TestTrace[B]): TestArrow[A, B] =
    makeEither(e => TestTrace.die(e).annotate(TestTrace.Annotation.Rethrow), f)

  def makeEither[A, B](onFail: Throwable => TestTrace[B], onSucceed: A => TestTrace[B]): TestArrow[A, B] =
    TestArrowF {
      case Left(error)  => onFail(error)
      case Right(value) => onSucceed(value)
    }

  private def attempt[A](expr: => TestTrace[A]): TestTrace[A] =
    try {
      expr
    } catch {
      case NonFatal(exception) =>
        val trace = exception.getStackTrace
        var met   = false
        val newTrace = trace.filterNot { trace =>
          if (trace.toString.contains("zio.test.TestArrow")) {
            met = true
          }
          met
        }
        exception.setStackTrace(newTrace)
        TestTrace.die(exception)
    }

  def run[A, B](arrow: TestArrow[A, B], in: Either[Throwable, A]): TestTrace[B] = {
    implicit val trace: Trace = Tracer.newTrace

    def loop[A, B](arrow: TestArrow[A, B], in: Either[Throwable, A])(implicit trace: Trace): UIO[TestTrace[B]] =
      arrow match {
        case TestArrowF(f) =>
          ZIO.succeed(f(in))

        case AndThen(f, g) =>
          loop(f, in).flatMap { t1 =>
            t1.result match {
              case Result.Fail           => ZIO.succeed(t1.asInstanceOf[TestTrace[B]])
              case Result.Die(err)       => loop(g, Left(err)).map(t1 >>> _)
              case Result.Succeed(value) => loop(g, Right(value)).map(t1 >>> _)
            }
          }

        case And(left, right) =>
          for {
            left  <- loop(left, in)
            right <- loop(right, in)
          } yield left && right

        case Or(left, right) =>
          for {
            left  <- loop(left, in)
            right <- loop(right, in)
          } yield left || right

        case Not(arrow) => loop(arrow, in).map(!_)

        case Suspend(f) =>
          in match {
            case Left(exception) =>
              ZIO.succeedNow(TestTrace.die(exception))
            case Right(value) =>
              loop(f(value), in)
          }

        case Meta(arrow, span, parentSpan, code, location, completeCode, customLabel, genFailureDetails) =>
          loop(arrow, in).map(
            _.withSpan(span)
              .withCode(code)
              .withParentSpan(parentSpan)
              .withLocation(location)
              .withCompleteCode(completeCode)
              .withCustomLabel(customLabel)
              .withGenFailureDetails(genFailureDetails)
          )
      }

    attempt {
      zio.Runtime.default.unsafeRun(loop(arrow, in))
    }
  }

  case class Span(start: Int, end: Int) {
    def substring(str: String): String = str.substring(start, end)
  }

  case class Meta[-A, +B](
    arrow: TestArrow[A, B],
    span: Option[Span],
    parentSpan: Option[Span],
    code: Option[String],
    location: Option[String],
    completeCode: Option[String],
    customLabel: Option[String],
    genFailureDetails: Option[GenFailureDetails]
  ) extends TestArrow[A, B]
  case class TestArrowF[-A, +B](f: Either[Throwable, A] => TestTrace[B])       extends TestArrow[A, B]
  case class AndThen[A, B, C](f: TestArrow[A, B], g: TestArrow[B, C])          extends TestArrow[A, C]
  case class And[A](left: TestArrow[A, Boolean], right: TestArrow[A, Boolean]) extends TestArrow[A, Boolean]
  case class Or[A](left: TestArrow[A, Boolean], right: TestArrow[A, Boolean])  extends TestArrow[A, Boolean]
  case class Not[A](arrow: TestArrow[A, Boolean])                              extends TestArrow[A, Boolean]
  case class Suspend[A, B](f: A => TestArrow[Any, B])                          extends TestArrow[A, B]
}
