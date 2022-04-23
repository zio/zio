package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class NewEncodingBenchmark {

  @Param(Array("5", "10")) //, "20", "40"))
  var depth: Int = _

  @Benchmark
  def classicBroadFlatMap(): Int = {
    def fib(n: Int): Classic[Int] =
      if (n <= 1) Classic.succeed[Int](n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => Classic.succeed(a + b)))

    fib(depth).unsafeRun()
  }

  @Benchmark
  def experimentBroadFlatMap(): Int = {
    def fib(n: Int): Experiment[Int] =
      if (n <= 1) Experiment.succeed(n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => Experiment.succeed(a + b)))

    fib(depth).unsafeRun()
  }
}

sealed trait Classic[+A] { self =>
  def unsafeRun(): A = Classic.eval(self)

  def flatMap[B](f: A => Classic[B]): Classic[B] = Classic.FlatMap(self, f)

  def map[B](f: A => B): Classic[B] = self.flatMap(a => Classic.succeed(f(a)))
}
object Classic {
  case class Succeed[A](thunk: () => A)                                 extends Classic[A]
  case class FlatMap[A, B](first: Classic[A], andThen: A => Classic[B]) extends Classic[B]

  def succeed[A](a: => A): Classic[A] = Succeed(() => a)

  private def eval[A](effect: Classic[A]): A = {
    type Erased  = Classic[Any]
    type ErasedK = Any => Classic[Any]

    val stack = zio.internal.Stack[ErasedK]()

    var cur: Erased = effect
    var done: A     = null.asInstanceOf[A]

    while (done == null) {
      cur match {
        case FlatMap(first, andThen) =>
          cur = first
          stack.push(andThen)

        case Succeed(thunk) =>
          val value = thunk()

          if (stack.isEmpty) {
            cur = null
            done = value.asInstanceOf[A]
          } else {
            cur = stack.pop()(value)
          }
      }
    }

    done
  }
}

sealed trait Experiment[+A] { self =>
  def unsafeRun(): A = Experiment.eval(self)

  def flatMap[B](f: A => Experiment[B]): Experiment[B] = Experiment.FlatMap(self, f)

  def map[B](f: A => B): Experiment[B] = self.flatMap(a => Experiment.succeed(f(a)))
}
object Experiment {
  case class Succeed[A](thunk: () => A)                                       extends Experiment[A]
  case class FlatMap[A, B](first: Experiment[A], andThen: A => Experiment[B]) extends Experiment[B]

  def succeed[A](a: => A): Experiment[A] = Succeed(() => a)

  private def eval[A](effect: Experiment[A]): A = {
    type Erased  = Experiment[Any]
    type ErasedK = Any => Experiment[Any]

    var cur: Erased = effect
    var done: A     = null.asInstanceOf[A]

    while (done == null) {
      cur match {
        case FlatMap(first, andThen) =>
          cur = andThen.asInstanceOf[ErasedK](eval(first))

        case Succeed(thunk) =>
          val value = thunk()

          done = value.asInstanceOf[A]
      }
    }

    done
  }
}

package stack_versus_heap_10 {
  sealed trait Experiment[+A] { self =>
    def unsafeRun(): A = Experiment.eval(self)

    def flatMap[B](f: A => Experiment[B]): Experiment[B] = Experiment.FlatMap(self, f)

    def map[B](f: A => B): Experiment[B] = self.flatMap(a => Experiment.succeed(f(a)))
  }
  object Experiment {
    case class Succeed[A](thunk: () => A)                                       extends Experiment[A]
    case class FlatMap[A, B](first: Experiment[A], andThen: A => Experiment[B]) extends Experiment[B]

    def succeed[A](a: => A): Experiment[A] = Succeed(() => a)

    private def eval[A](effect: Experiment[A]): A = {
      type Erased  = Experiment[Any]
      type ErasedK = Any => Experiment[Any]

      var cur: Erased                             = effect
      var done: A                                 = null.asInstanceOf[A]
      var k1, k2, k3, k4, k5, k6, k7, k8, k9, k10 = null.asInstanceOf[ErasedK]

      while (done == null) {
        cur match {
          case FlatMap(first, andThen) =>
            if (k10 ne null) {
              cur = andThen(eval(first))
            } else {
              k10 = k9; k9 = k8; k8 = k7; k7 = k6; k6 = k5;
              k5 = k4; k4 = k3; k3 = k2; k2 = k1; k1 = andThen.asInstanceOf[ErasedK]

              cur = first
            }

          case Succeed(thunk) =>
            val value = thunk()

            if (k1 ne null) {
              cur = k1(value)

              k1 = k2; k2 = k3; k3 = k4; k4 = k5; k5 = k6;
              k6 = k7; k7 = k8; k8 = k9; k9 = k10; k10 = null.asInstanceOf[ErasedK]
            } else {
              cur = null
              done = value.asInstanceOf[A]
            }
        }
      }

      done
    }
  }
}
