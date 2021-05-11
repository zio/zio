package zio.test

import zio.Chunk

import scala.annotation.tailrec
import scala.util.Try

object AssertExamples {
  def optionExample = {
    val option = Assert.succeed(Option.empty[Int]).label("maybeInt") >>>
      Assertions.get[Int] >>> Assert.fromFunction((_: Int) > 10).label(" > 10")
    Assert.run(option, ())
  }

  def main(args: Array[String]): Unit = {
    val a = (Assert.succeed[Int](10) >>>
      Assert.fromFunction[Int, Int](_ + 10).label(" + 10") >>>
      Assert.fromFunction[Any, Int](_ => throw new Error("BANG")).label("BOOM") >>>
      Assert.fromFunction((_: Int) % 2 == 0).label(" % 2 == 0") >>>
      Assert.fromFunction((_: Boolean) == true).label(" == true")).fails
    val result = Assert.run(a, 10)

    println(result)
    println("")
    result.debug
    println("")
    val tree = TraceTree.fromTrace(result)
    println(tree)
  }
}

sealed trait Assert[-A, +B] { self =>
  import Assert._

  def label(label: String): Assert[A, B] =
    Label(self, label)

  def >>>[C](that: Assert[B, C]): Assert[A, C] = AndThen[A, B, C](self, rethrow, that)

  def fold[C](handle: Assert[Throwable, C], that: Assert[B, C]): Assert[A, C] = AndThen[A, B, C](self, handle, that)

  def fails: Assert[A, Throwable] = fold(Assert.identityA, Assert.Arrow(_ => Trace.halt("Expected failure")))
}

object Assertions {
  def get[A]: Assert[Option[A], A] =
    Assert
      .Arrow[Option[A], A] {
        case Some(value) => Trace.succeed(value)
        case None =>
          Trace.halt("Option was None")
      }
      .label(".get")
}

object Assert {
  def succeed[A](value: A): Assert[Any, A]        = Arrow(_ => Trace.succeed(value))
  def fromFunction[A, B](f: A => B): Assert[A, B] = Arrow(f andThen Trace.succeed)

  val rethrow: Assert[Throwable, Nothing]     = Arrow(Trace.fail)
  val identityA: Assert[Throwable, Throwable] = Arrow(a => Trace.succeed(a))

  private def attempt[A](f: => Trace[A]): Trace[A] = Try(f).fold(e => Trace.fail(e), identity)

  def run[A, B](assert: Assert[A, B], in: A): Trace[B] = attempt {
    println("RUNNING")
    println(assert)
    println(in)
    println("---")

    assert match {
      case Arrow(f) =>
        f(in)

      case AndThen(f, handler, g) =>
        val t1 = run(f, in)
        t1 match {
          case Trace.Halt() => t1.asInstanceOf[Trace[B]]
          case Trace.Fail(err) =>
            val t2 = run(handler, err)
            t2.removingConsecutiveErrors(err) match {
              case Some(t2) => t1 >>> t2
              case None     => t1.asInstanceOf[Trace[B]]
            }
          case Trace.Succeed(value) => t1 >>> run(g, value)
        }

      case Zip(lhs, rhs) =>
        run(lhs, in) <*> run(rhs, in)

      case Label(assert, label0) =>
        run(assert, in).label(label0)

    }
  }

  case class Label[-A, +B](assert: Assert[A, B], label: String) extends Assert[A, B]
  case class Arrow[-A, +B](f: A => Trace[B])                    extends Assert[A, B] {}

  case class AndThen[A, B, C](f: Assert[A, B], fail: Assert[Throwable, C], g: Assert[B, C]) extends Assert[A, C]
  case class Zip[A, B, C](lhs: Assert[A, B], rhs: Assert[A, C])                             extends Assert[A, (B, C)]
}
