package zio.test

import zio.ZIO

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

case class Assert(arrow: Arrow[Any, Boolean]) {
  def &&(that: Assert): Assert = Assert(arrow && that.arrow)

  def ||(that: Assert): Assert = Assert(arrow || that.arrow)

  def unary_! : Assert = Assert(!arrow)
}

object Assert {
  def all(asserts: Assert*): Assert = asserts.reduce(_ && _)

  def any(asserts: Assert*): Assert = asserts.reduce(_ || _)

  implicit def trace2TestResult(assert: Assert): TestResult = {
    val trace = Arrow.run(assert.arrow, Right(()))
    if (trace.isSuccess) BoolAlgebra.success(AssertionResult.TraceResult(trace))
    else BoolAlgebra.failure(AssertionResult.TraceResult(trace))
  }

  implicit def traceM2TestResult[R, E](zio: ZIO[R, E, Assert]): ZIO[R, E, TestResult] =
    zio.map(trace2TestResult)

}

sealed trait Arrow[-A, +B] { self =>
  import Arrow._

  def meta(
    span: Option[Span] = None,
    parentSpan: Option[Span] = None,
    code: Option[String] = None,
    location: Option[String] = None
  ): Arrow[A, B] = self match {
    case meta: Meta[A, B] =>
      meta.copy(
        span = meta.span.orElse(span),
        parentSpan = meta.parentSpan.orElse(parentSpan),
        code = meta.code.orElse(code),
        location = meta.location.orElse(location)
      )
    case _ =>
      Meta(assert = self, span = span, parentSpan = parentSpan, code = code, location = location)
  }

  def span(span: (Int, Int)): Arrow[A, B] =
    meta(span = Some(Span(span._1, span._2)))

  def withCode(code: String): Arrow[A, B] =
    meta(code = Some(code))

  def withLocation(implicit location: SourceLocation): Arrow[A, B] =
    meta(location = Some(s"${location.path}:${location.line}"))

  def withParentSpan(span: (Int, Int)): Arrow[A, B] =
    meta(parentSpan = Some(Span(span._1, span._2)))

  def >>>[C](that: Arrow[B, C]): Arrow[A, C] = AndThen[A, B, C](self, that)

  def &&(that: Arrow[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Arrow[Any, Boolean] =
    And(self.asInstanceOf[Arrow[Any, Boolean]], that)

  def ||(that: Arrow[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Arrow[Any, Boolean] =
    Or(self.asInstanceOf[Arrow[Any, Boolean]], that)

  def unary_!(implicit ev: Any <:< A, ev2: B <:< Boolean): Arrow[Any, Boolean] =
    Not(self.asInstanceOf[Arrow[Any, Boolean]])
}

object Arrow {

  def succeed[A](value: => A): Arrow[Any, A] = ArrowF(_ => Trace.succeed(value))

  def fromFunction[A, B](f: A => B): Arrow[A, B] = make(f andThen Trace.succeed)

  def suspend[A, B](f: A => Arrow[Any, B]): Arrow[A, B] = Arrow.Suspend(f)

  def make[A, B](f: A => Trace[B]): Arrow[A, B] =
    makeEither(e => Trace.die(e).annotate(Trace.Annotation.Rethrow), f)

  def makeEither[A, B](onFail: Throwable => Trace[B], onSucceed: A => Trace[B]): Arrow[A, B] =
    ArrowF {
      case Left(error)  => onFail(error)
      case Right(value) => onSucceed(value)
    }

  private def attempt[A](f: => Trace[A]): Trace[A] =
    Try(f) match {
      case Failure(exception) => Trace.die(exception)
      case Success(value)     => value
    }

  def run[A, B](assert: Arrow[A, B], in: Either[Throwable, A]): Trace[B] = attempt {
    assert match {
      case ArrowF(f) =>
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

      case Not(assert) =>
        !run(assert, in)

      case Suspend(f) =>
        in match {
          case Left(exception) =>
            Trace.die(exception)
          case Right(value) =>
            run(f(value), in)
        }

      case Meta(assert, span, parentSpan, code, location) =>
        run(assert, in)
          .withSpan(span)
          .withCode(code)
          .withParentSpan(parentSpan)
          .withLocation(location)
    }
  }

  case class Span(start: Int, end: Int) {
    def substring(str: String): String = str.substring(start, end)

  }

  case class Meta[-A, +B](
    assert: Arrow[A, B],
    span: Option[Span],
    parentSpan: Option[Span],
    code: Option[String],
    location: Option[String]
  ) extends Arrow[A, B]
  case class ArrowF[-A, +B](f: Either[Throwable, A] => Trace[B])        extends Arrow[A, B] {}
  case class AndThen[A, B, C](f: Arrow[A, B], g: Arrow[B, C])           extends Arrow[A, C]
  case class And(left: Arrow[Any, Boolean], right: Arrow[Any, Boolean]) extends Arrow[Any, Boolean]
  case class Or(left: Arrow[Any, Boolean], right: Arrow[Any, Boolean])  extends Arrow[Any, Boolean]
  case class Not(assert: Arrow[Any, Boolean])                           extends Arrow[Any, Boolean]
  case class Suspend[A, B](f: A => Arrow[Any, B])                       extends Arrow[A, B]
}
