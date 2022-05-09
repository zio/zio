package zio

import zio.test._
import scala.concurrent._

object NewEncodingSpec extends ZIOBaseSpec {
  import scala.util.control.NoStackTrace
  import scala.annotation.tailrec

  sealed trait Effect[+E, +A] { self =>
    def catchAll[E2, A1 >: A](t: E => Effect[E2, A1])(implicit trace: ZTraceElement): Effect[E2, A1] =
      self.catchAllCause { cause =>
        cause.failureOrCause.fold(t, Effect.failCause(_))
      }

    def catchAllCause[E2, A1 >: A](t: Cause[E] => Effect[E2, A1])(implicit trace: ZTraceElement): Effect[E2, A1] =
      Effect.OnFailure(trace, self, t)

    def exit: Effect[Nothing, Exit[E, A]] =
      map(Exit.succeed(_)).catchAll(cause => Effect.succeed(Exit.fail(cause)))

    def flatMap[E1 >: E, B](f: A => Effect[E1, B])(implicit trace: ZTraceElement): Effect[E1, B] =
      Effect.OnSuccess(trace, self, f)

    def map[B](f: A => B)(implicit trace: ZTraceElement): Effect[E, B] =
      self.flatMap(a => Effect.succeed(f(a)))

    def mapError[E2](f: E => E2)(implicit trace: ZTraceElement): Effect[E2, A] =
      self.catchAll(e => Effect.fail(f(e)))
  }
  object Effect {
    implicit class EffectThrowableSyntax[A](self: Effect[Throwable, A]) {
      def unsafeRun(): A = Effect.eval(self)
    }
    sealed trait Continuation[-A, +E, +B] { self =>
      def trace: ZTraceElement

      def onSuccess(a: A): Effect[E, B]

      def erase: Continuation[Any, Any, Any] = self.asInstanceOf[Continuation[Any, Any, Any]]
    }
    sealed trait FailureCont[-A, -E1, +E2, +B] extends Continuation[A, E2, B] { self =>
      def onFailure(t: Cause[E1]): Effect[E2, B]

      override def erase: FailureCont[Any, Any, Any, Any] = self.asInstanceOf[FailureCont[Any, Any, Any, Any]]
    }
    sealed trait ChangeInterruptibility extends Continuation[Any, Any, Any] {
      final def trace = ZTraceElement.empty

      def interruptible: Boolean
    }
    object ChangeInterruptibility {
      def apply(b: Boolean): ChangeInterruptibility = if (b) MakeInterruptible else MakeUninterruptible
    }
    case object MakeInterruptible extends ChangeInterruptibility {
      def onSuccess(a: Any): Effect[Any, Any] = Effect.succeed(a)

      def interruptible: Boolean = true
    }
    case object MakeUninterruptible extends ChangeInterruptibility {
      def onSuccess(a: Any): Effect[Any, Any] = Effect.succeed(a)

      def interruptible: Boolean = false
    }

    final case class Sync[A](eval: () => A)                                        extends Effect[Nothing, A]
    final case class Async[E, A](registerCallback: (Effect[E, A] => Unit) => Unit) extends Effect[E, A]
    final case class OnSuccess[A, E, B](trace: ZTraceElement, first: Effect[E, A], andThen: A => Effect[E, B])
        extends Effect[E, B]
        with Continuation[A, E, B] {
      def onSuccess(a: A): Effect[E, B] = andThen(a)
    }
    final case class OnFailure[E1, E2, A](
      trace: ZTraceElement,
      first: Effect[E1, A],
      rescuer: Cause[E1] => Effect[E2, A]
    ) extends Effect[E2, A]
        with FailureCont[A, E1, E2, A] {
      def onSuccess(a: A): Effect[Nothing, A] = Effect.succeed(a)

      def onFailure(t: Cause[E1]): Effect[E2, A] = rescuer(t)
    }
    final case class ChangeInterruptionWithin[E, A](newInterruptible: Boolean, scope: Boolean => Effect[E, A])
        extends Effect[E, A]

    case object AsyncReturn

    type Erased  = Effect[Any, Any]
    type ErasedK = Continuation[Any, Any, Any]

    sealed abstract class ReifyStack extends Exception with NoStackTrace {
      def stack: ChunkBuilder[ErasedK]

      final def addAndThrow(k: ErasedK): Nothing = {
        stack += (k)
        throw this
      }
    }

    final case class AsyncJump(registerCallback: (Effect[Any, Any] => Unit) => Unit, stack: ChunkBuilder[ErasedK])
        extends ReifyStack

    final case class Trampoline(effect: Effect[Any, Any], stack: ChunkBuilder[ErasedK]) extends ReifyStack

    final case class ErrorWrapper(cause: Cause[Any], stack: ChunkBuilder[ErasedK]) extends ReifyStack

    def succeed[A](a: => A): Effect[Nothing, A] = Sync(() => a)

    def fail[E](e: => E): Effect[E, Nothing] = failCause(Cause.fail(e))

    def failCause[E](c: => Cause[E]): Effect[E, Nothing] = succeed(throw ErrorWrapper(c, ChunkBuilder.make()))

    def async[E, A](registerCallback: (Effect[E, A] => Unit) => Unit): Effect[E, A] = Async(registerCallback)

    def evalAsync[E, A](effect: Effect[E, A], onDone: Exit[E, A] => Unit): Exit[E, A] = {
      def loop[A](effect: Effect[Any, Any], depth: Int, stack: Chunk[ErasedK], initialInterruptible: Boolean): A = {
        var cur: Erased   = effect
        var done: A       = null.asInstanceOf[A]
        var stackIndex    = 0
        var interruptible = initialInterruptible

        if (depth > 1000) {
          throw Trampoline(effect, ChunkBuilder.make())
        } else {
          try {
            while (done == null) {
              try {
                cur match {
                  case flatMap @ OnSuccess(_, first, andThen) =>
                    try {
                      cur = flatMap.erase.onSuccess(loop(first, depth + 1, null, interruptible))
                    } catch {
                      case reifyStack: ReifyStack => reifyStack.addAndThrow(flatMap.erase)
                    }

                  case Async(registerCallback) => throw AsyncJump(registerCallback, ChunkBuilder.make())

                  case Sync(thunk) =>
                    val value = thunk()

                    if ((stack ne null) && stackIndex < stack.length) {
                      cur = stack(stackIndex).onSuccess(value)

                      stackIndex += 1
                    } else {
                      done = value.asInstanceOf[A]
                    }

                  case rescue @ OnFailure(_, first, rescuer) =>
                    try {
                      val value = loop(first, depth + 1, null, interruptible)

                      if ((stack ne null) && stackIndex < stack.length) {
                        cur = stack(stackIndex).onSuccess(value)

                        stackIndex += 1
                      } else {
                        done = value.asInstanceOf[A]
                      }
                    } catch {
                      case ErrorWrapper(cause, stack) =>
                        val _ = stack // TODO: Use stack to attach trace to throwable

                        cur = rescuer(cause)

                      case reifyStack: ReifyStack => reifyStack.addAndThrow(rescue.erase)
                    }

                  case ChangeInterruptionWithin(newInterruptible, scope) =>
                    val oldInterruptible = interruptible

                    interruptible = newInterruptible

                    try {
                      loop(scope(oldInterruptible), depth + 1, null, interruptible)

                      interruptible = oldInterruptible
                    } catch {
                      case reifyStack: ReifyStack => reifyStack.addAndThrow(ChangeInterruptibility(oldInterruptible))
                    }
                }
              } catch {
                case error @ ErrorWrapper(cause, _) =>
                  // TODO: attach trace to throwable
                  cur = null

                  if (stack ne null) {
                    while ((cur eq null) && stackIndex < stack.length) {
                      stack(stackIndex) match {
                        case failure: FailureCont[_, _, _, _] =>
                          cur = failure.erase.onFailure(cause)

                        case _: OnSuccess[_, _, _] => ()

                        case change: ChangeInterruptibility =>
                          interruptible = change.interruptible
                      }
                      stackIndex += 1
                    }
                  }

                  if (cur eq null) throw error
              }
            }
          } catch {
            case reifyStack: ReifyStack =>
              if ((stack ne null) && stackIndex <= stack.length) {
                reifyStack.stack ++= stack.drop(stackIndex)
              }

              throw reifyStack
          }
        }

        done
      }

      def resumeOuterLoop[E, A](effect: Effect[Any, Any], stack: Chunk[ErasedK], onDone: Exit[E, A] => Unit): Unit =
        scala.concurrent.ExecutionContext.global.execute { () =>
          outerLoop[E, A](effect, stack, onDone, true) // TODO: Interruptibility
          ()
        }

      @tailrec
      def outerLoop[E, A](
        effect: Effect[Any, Any],
        stack: Chunk[ErasedK],
        onDone: Exit[E, A] => Unit,
        interruptible: Boolean
      ): Exit[E, A] =
        try {
          val exit: Exit[Nothing, A] = Exit.succeed(loop(effect, 0, stack, interruptible))

          onDone(exit)

          exit
        } catch {
          case t: Trampoline => outerLoop(t.effect, t.stack.result(), onDone, interruptible)

          case a: AsyncJump =>
            a.registerCallback { value =>
              resumeOuterLoop[E, A](value, a.stack.result(), onDone)
            }

            null

          case e: ErrorWrapper =>
            val exit = Exit.failCause(e.cause.asInstanceOf[Cause[E]]) // TODO: attach e.stack

            onDone(exit)

            exit
        }

      outerLoop[E, A](effect, null, onDone, true)
    }

    def evalToFuture[A](effect: Effect[Throwable, A]): scala.concurrent.Future[A] = {
      val promise = Promise[A]()

      evalAsync[Throwable, A](effect, exit => promise.complete(exit.toTry)) match {
        case null => promise.future

        case exit => Future.fromTry(exit.toTry)
      }
    }

    def eval[A](effect: Effect[Throwable, A]): A = {
      import java.util.concurrent._
      import scala.concurrent.duration._

      val future = evalToFuture(effect)

      Await.result(future, Duration(1, TimeUnit.HOURS))
    }
  }

  def newFib(n: Int): Effect[Nothing, Int] =
    if (n <= 1) Effect.succeed(n)
    else
      for {
        a <- newFib(n - 1)
        b <- newFib(n - 2)
      } yield a + b

  def oldFib(n: Int): ZIO[Any, Nothing, Int] =
    if (n <= 1) ZIO.succeed(n)
    else
      for {
        a <- oldFib(n - 1)
        b <- oldFib(n - 2)
      } yield a + b

  def runFibTest(num: Int) =
    test(s"fib(${num})") {
      for {
        actual   <- ZIO.succeed(newFib(num).unsafeRun())
        expected <- oldFib(num)
      } yield assertTrue(actual == expected)
    }

  def newSum(n: Int): Effect[Nothing, Int] =
    Effect.succeed(n).flatMap(n => if (n <= 0) Effect.succeed(0) else newSum(n - 1).map(_ + n))

  def oldSum(n: Int): ZIO[Any, Nothing, Int] =
    ZIO.succeed(n).flatMap(n => if (n <= 0) ZIO.succeed(0) else oldSum(n - 1).map(_ + n))

  def runSumTest(num: Int) =
    test(s"sum(${num})") {
      for {
        actual   <- ZIO.succeed(newSum(num).unsafeRun())
        expected <- oldSum(num)
      } yield assertTrue(actual == expected)
    }

  final case class Failed(value: Int) extends Exception

  def newFailAfter(n: Int): Effect[Nothing, Int] = {
    def loop(i: Int): Effect[Failed, Nothing] =
      if (i >= n) Effect.fail(Failed(i))
      else Effect.succeed(i).flatMap(j => loop(j + 1))

    loop(0).catchAll { case Failed(i) =>
      Effect.succeed(i)
    }
  }

  def oldFailAfter(n: Int): ZIO[Any, Nothing, Int] = {
    def loop(i: Int): ZIO[Any, Throwable, Nothing] =
      if (i >= n) ZIO.fail(Failed(i))
      else ZIO.succeed(i).flatMap(j => loop(j + 1))

    loop(0).catchAll {
      case Failed(i) => ZIO.succeed(i)
      case _         => ???
    }
  }

  def runFailAfterTest(num: Int) =
    test(s"failAfter(${num})") {
      for {
        actual   <- ZIO.succeed(newFailAfter(num).unsafeRun())
        expected <- oldFailAfter(num)
      } yield assertTrue(actual == expected)
    }

  def newAsyncAfter(n: Int): Effect[Nothing, Int] = {
    def loop(i: Int): Effect[Nothing, Int] =
      if (i >= n) Effect.async[Nothing, Int](k => k(Effect.succeed(i)))
      else Effect.succeed(i).flatMap(j => loop(j + 1)).map(_ + i)

    loop(0)
  }

  def oldAsyncAfter(n: Int): ZIO[Any, Nothing, Int] = {
    def loop(i: Int): ZIO[Any, Nothing, Int] =
      if (i >= n) ZIO.async[Any, Nothing, Int](k => k(ZIO.succeed(i)))
      else ZIO.succeed(i).flatMap(j => loop(j + 1)).map(_ + i)

    loop(0)
  }

  def newTerminalFail(n: Int): Effect[Nothing, Exit[Failed, Int]] = {
    def loop(i: Int): Effect[Failed, Nothing] =
      if (i >= n) Effect.fail(Failed(i))
      else Effect.succeed(i).flatMap(j => loop(j + 1))

    loop(0).exit
  }

  def oldTerminalFail(n: Int): ZIO[Any, Nothing, Exit[Failed, Int]] = {
    def loop(i: Int): ZIO[Any, Failed, Nothing] =
      if (i >= n) ZIO.fail(Failed(i))
      else ZIO.succeed(i).flatMap(j => loop(j + 1))

    loop(0).exit
  }

  def runTerminalFailTest(num: Int) =
    test(s"terminalFail(${num})") {
      for {
        actual   <- ZIO.succeed(newTerminalFail(num).unsafeRun())
        expected <- oldTerminalFail(num)
      } yield assertTrue(actual == expected)
    }

  def runAsyncAfterTest(num: Int) =
    test(s"asyncAfter(${num})") {
      for {
        actual   <- ZIO.succeed(newAsyncAfter(num).unsafeRun())
        expected <- oldAsyncAfter(num)
      } yield assertTrue(actual == expected)
    }

  def spec =
    suite("NewEncodingSpec") {
      suite("fib") {
        runFibTest(0) +
          runFibTest(5) +
          runFibTest(10) +
          runFibTest(20)
      } +
        suite("sum") {
          runSumTest(0) +
            runSumTest(100) +
            runSumTest(1000) +
            runSumTest(10000)
        } +
        suite("failAfter") {
          runFailAfterTest(0) +
            runFailAfterTest(100) +
            runFailAfterTest(1000) +
            runFailAfterTest(10000)
        } +
        suite("asyncAfter") {
          runAsyncAfterTest(0) +
            runAsyncAfterTest(100) +
            runAsyncAfterTest(1000) +
            runAsyncAfterTest(10000)
        } +
        suite("terminalFail") {
          runTerminalFailTest(0) +
            runTerminalFailTest(100) +
            runTerminalFailTest(1000) +
            runTerminalFailTest(10000)
        }
    }
}
