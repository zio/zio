package zio.test

import zio.Chunk

import scala.util.Try

object Assertions {
  def get[A]: Assert[Option[A], A] =
    Assert
      .make[Option[A], A] {
        case Some(value) => Trace.succeed(value)
        case None        => Trace.halt("Option was None")
      }
      .label(".get")

  val throws: Assert[Any, Throwable] = Assert.makeEither(
    Trace.succeed,
    _ => Trace.halt("Expected failure")
  )
}

sealed trait Assert[-A, +B] { self =>

  import Assert._

  def label(label: String): Assert[A, B] =
    Label(self, label)

  def >>>[C](that: Assert[B, C]): Assert[A, C] = AndThen[A, B, C](self, that)

  def ++[A1 <: A, C](that: Assert[A1, C]): Assert[A1, (B, C)] = Zip(self, that)

  def &&(that: Assert[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Assert[Any, Boolean] =
    (self.asInstanceOf[Assert[Any, Boolean]] ++ that) >>>
      make { case (a, b) => Trace.succeed(a && b).label("AND") }
}

object Assert {
  def succeed[A](value: A): Assert[Any, A] = Arrow(_ => Trace.succeed(value))

  def fromFunction[A, B](f: A => B): Assert[A, B] = make(f andThen Trace.succeed)

  def make[A, B](f: A => Trace[B]): Assert[A, B] =
    makeEither(Trace.fail, f)

  def makeEither[A, B](onFail: Throwable => Trace[B], onSucceed: A => Trace[B]): Assert[A, B] =
    Arrow {
      case Left(error)  => onFail(error)
      case Right(value) => onSucceed(value)
    }

  private def attempt[A](f: => Trace[A]): Trace[A] = Try(f).fold(e => Trace.fail(e), identity)

  def run[A, B](assert: Assert[A, B], in: Either[Throwable, A]): Trace[B] = attempt {
    println("RUNNING")
    println(assert)
    println(in)
    println("---")

    assert match {
      case Arrow(f) =>
        f(in)

      case AndThen(f, g) =>
        val t1 = run(f, in)
        t1 match {
          case Trace.Halt() => t1.asInstanceOf[Trace[B]]
          case Trace.Fail(err) =>
            val t2 = run(g, Left(err))
            t2.removingConsecutiveErrors(err) match {
              case Some(t2) => t1 >>> t2
              case None     => t1.asInstanceOf[Trace[B]]
            }
          case Trace.Succeed(value) => t1 >>> run(g, Right(value))
        }

      case Zip(lhs, rhs) =>
        run(lhs, in) <*> run(rhs, in)

      case Label(assert, label0) =>
        run(assert, in).label(label0)

    }
  }

  case class Label[-A, +B](assert: Assert[A, B], label: String) extends Assert[A, B]
  case class Arrow[-A, +B](f: Either[Throwable, A] => Trace[B]) extends Assert[A, B] {}

  case class AndThen[A, B, C](f: Assert[A, B], g: Assert[B, C]) extends Assert[A, C]
  case class Zip[A, B, C](lhs: Assert[A, B], rhs: Assert[A, C]) extends Assert[A, (B, C)]
}
