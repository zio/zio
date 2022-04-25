package zio.test

import zio.ZIO
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import zio.ZTraceElement

case class Assert(arrow: TestArrow[Any, Boolean]) {
  def &&(that: Assert): Assert = Assert(arrow && that.arrow)

  def ||(that: Assert): Assert = Assert(arrow || that.arrow)

  def unary_! : Assert = Assert(!arrow)
}

object Assert {
  def all(asserts: Assert*): Assert = asserts.reduce(_ && _)

  def any(asserts: Assert*): Assert = asserts.reduce(_ || _)

  implicit def trace2TestResult(assert: Assert): TestResult = {
    val trace = TestArrow.run(assert.arrow, Right(()))
    Trace.prune(trace, false) match {
      case Some(_) =>
        BoolAlgebra.failure(AssertionResult.TraceResult(trace))
      case None =>
        BoolAlgebra.success(AssertionResult.TraceResult(trace))
    }
  }

  implicit def traceM2TestResult[R, E](zio: ZIO[R, E, Assert])(implicit trace: ZTraceElement): ZIO[R, E, TestResult] =
    zio.map(trace2TestResult)

}

sealed trait TestArrow[-A, +B] { self =>
  import TestArrow._

  def meta(
    span: Option[Span] = None,
    parentSpan: Option[Span] = None,
    code: Option[String] = None,
    location: Option[String] = None,
    completeCode: Option[String] = None
  ): TestArrow[A, B] = self match {
    case meta: Meta[A, B] =>
      meta.copy(
        span = meta.span.orElse(span),
        parentSpan = meta.parentSpan.orElse(parentSpan),
        code = code.orElse(meta.code),
        location = meta.location.orElse(location),
        completeCode = meta.completeCode.orElse(completeCode)
      )
    case _ =>
      Meta(arrow = self, span = span, parentSpan = parentSpan, code = code, location = location, completeCode)
  }

  def span(span: (Int, Int)): TestArrow[A, B] =
    meta(span = Some(Span(span._1, span._2)))

  def withCode(code: String): TestArrow[A, B] =
    meta(code = Some(code))

  def withCompleteCode(completeCode: String): TestArrow[A, B] =
    meta(completeCode = Some(completeCode))

  def withLocation(implicit trace: ZTraceElement): TestArrow[A, B] =
    trace match {
      case ZTraceElement(_, file, line) =>
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

  def succeed[A](value: => A): TestArrow[Any, A] = TestArrowF(_ => Trace.succeed(value))

  def fromFunction[A, B](f: A => B): TestArrow[A, B] = make(f andThen Trace.succeed)

  def suspend[A, B](f: A => TestArrow[Any, B]): TestArrow[A, B] = TestArrow.Suspend(f)

  def make[A, B](f: A => Trace[B]): TestArrow[A, B] =
    makeEither(e => Trace.die(e).annotate(Trace.Annotation.Rethrow), f)

  def makeEither[A, B](onFail: Throwable => Trace[B], onSucceed: A => Trace[B]): TestArrow[A, B] =
    TestArrowF {
      case Left(error)  => onFail(error)
      case Right(value) => onSucceed(value)
    }

  private def attempt[A](f: => Trace[A]): Trace[A] =
    Try(f) match {
      case Failure(exception) => Trace.die(exception)
      case Success(value)     => value
    }

  def run[A, B](arrow: TestArrow[A, B], in: Either[Throwable, A]): Trace[B] = attempt {
    arrow match {
      case TestArrowF(f) =>
        f(in)

      case AndThen(f, g) =>
        val t1 = run(f, in)
        t1.result match {
          case Result.Fail           => t1.asInstanceOf[Trace[B]]
          case Result.Die(err)       => t1 >>> run(g, Left(err))
          case Result.Succeed(value) => t1 >>> run(g, Right(value))
        }

      case And(lhs, rhs) =>
        run(lhs, in) && run(rhs, in)

      case Or(lhs, rhs) =>
        run(lhs, in) || run(rhs, in)

      case Not(arrow) =>
        !run(arrow, in)

      case Suspend(f) =>
        in match {
          case Left(exception) =>
            Trace.die(exception)
          case Right(value) =>
            run(f(value), in)
        }

      case Meta(arrow, span, parentSpan, code, location, completeCode) =>
        run(arrow, in)
          .withSpan(span)
          .withCode(code)
          .withParentSpan(parentSpan)
          .withLocation(location)
          .withCompleteCode(completeCode)
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
    completeCode: Option[String]
  ) extends TestArrow[A, B]
  case class TestArrowF[-A, +B](f: Either[Throwable, A] => Trace[B])           extends TestArrow[A, B]
  case class AndThen[A, B, C](f: TestArrow[A, B], g: TestArrow[B, C])          extends TestArrow[A, C]
  case class And[A](left: TestArrow[A, Boolean], right: TestArrow[A, Boolean]) extends TestArrow[A, Boolean]
  case class Or[A](left: TestArrow[A, Boolean], right: TestArrow[A, Boolean])  extends TestArrow[A, Boolean]
  case class Not[A](arrow: TestArrow[A, Boolean])                              extends TestArrow[A, Boolean]
  case class Suspend[A, B](f: A => TestArrow[Any, B])                          extends TestArrow[A, B]
}
