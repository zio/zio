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

    def ensuring(finalizer: Effect[Nothing, Any])(implicit trace: ZTraceElement): Effect[E, A] =
      Effect.Ensuring(self, finalizer, trace)

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
    sealed trait EvaluationStep { self =>
      def trace: ZTraceElement
    }
    object EvaluationStep {
      sealed trait ChangeInterruptibility extends EvaluationStep {
        final def trace = ZTraceElement.empty

        def interruptible: Boolean
      }
      object ChangeInterruptibility {
        def apply(b: Boolean): ChangeInterruptibility = if (b) MakeInterruptible else MakeUninterruptible

        case object MakeInterruptible extends ChangeInterruptibility {
          def onSuccess(a: Any): Effect[Any, Any] = Effect.succeed(a)

          def interruptible: Boolean = true
        }
        case object MakeUninterruptible extends ChangeInterruptibility {
          def onSuccess(a: Any): Effect[Any, Any] = Effect.succeed(a)

          def interruptible: Boolean = false
        }
      }
      sealed trait Continuation[E1, E2, A, B] extends EvaluationStep { self =>
        def trace: ZTraceElement

        def onSuccess(a: A): Effect[E2, B]

        def onFailure(c: Cause[E1]): Effect[E2, B]

        def erase: Continuation.Erased = self.asInstanceOf[Continuation.Erased]
      }
      object Continuation {
        type Erased = Continuation[Any, Any, Any, Any]

        def fromSuccess[E, A, B](f: A => Effect[E, B])(implicit trace0: ZTraceElement): Continuation[E, E, A, B] =
          new Continuation[E, E, A, B] {
            def trace                  = trace0
            def onSuccess(a: A)        = f(a)
            def onFailure(c: Cause[E]) = Effect.failCause(c)
          }

        def fromFailure[E1, E2, A](
          f: Cause[E1] => Effect[E2, A]
        )(implicit trace0: ZTraceElement): Continuation[E1, E2, A, A] =
          new Continuation[E1, E2, A, A] {
            def trace                   = trace0
            def onSuccess(a: A)         = Effect.succeed(a)
            def onFailure(c: Cause[E1]) = f(c)
          }
      }
    }

    final case class Sync[A](eval: () => A)                                        extends Effect[Nothing, A]
    final case class Async[E, A](registerCallback: (Effect[E, A] => Unit) => Unit) extends Effect[E, A]
    sealed trait OnSuccessOrFailure[E1, E2, A, B] extends Effect[E2, B] with EvaluationStep.Continuation[E1, E2, A, B] {
      self =>
      def first: Effect[E1, A]

      final override def erase: OnSuccessOrFailure[Any, Any, Any, Any] =
        self.asInstanceOf[OnSuccessOrFailure[Any, Any, Any, Any]]
    }
    final case class OnSuccess[A, E, B](trace: ZTraceElement, first: Effect[E, A], successK: A => Effect[E, B])
        extends OnSuccessOrFailure[E, E, A, B] {
      def onFailure(c: Cause[E]): Effect[E, B] = Effect.failCause(c)

      def onSuccess(a: A): Effect[E, B] = successK(a.asInstanceOf[A])
    }
    final case class OnFailure[E1, E2, A](
      trace: ZTraceElement,
      first: Effect[E1, A],
      failureK: Cause[E1] => Effect[E2, A]
    ) extends OnSuccessOrFailure[E1, E2, A, A] {
      def onFailure(c: Cause[E1]): Effect[E2, A] = failureK(c)

      def onSuccess(a: A): Effect[E2, A] = Effect.succeed(a)
    }
    final case class ChangeInterruptionWithin[E, A](newInterruptible: Boolean, scope: Boolean => Effect[E, A])
        extends Effect[E, A]
    final case class Ensuring[E, A](first: Effect[E, A], finalizer: Effect[Nothing, Any], trace: ZTraceElement)
        extends Effect[E, A]
    // final case class Stateful[E, A](onState: FiberState[E, A] => Effect[E, A]) extends Effect[E, A]

    case object AsyncReturn

    type Erased = Effect[Any, Any]

    sealed abstract class ReifyStack extends Exception with NoStackTrace {
      def stack: ChunkBuilder[EvaluationStep]

      final def addAndThrow(k: EvaluationStep): Nothing = {
        stack += (k)
        throw this
      }

      final def toStackTrace(fiberId: FiberId): ZTrace =
        ZTrace(
          fiberId,
          stack.result().collect {
            case k if k.trace != ZTraceElement.empty => k.trace
          }
        )
    }

    final case class AsyncJump(
      registerCallback: (Effect[Any, Any] => Unit) => Unit,
      stack: ChunkBuilder[EvaluationStep]
    ) extends ReifyStack

    final case class Trampoline(effect: Effect[Any, Any], stack: ChunkBuilder[EvaluationStep]) extends ReifyStack

    final case class RaisingError(cause: Cause[Any], stack: ChunkBuilder[EvaluationStep]) extends ReifyStack {
      def tracedCause(fiberId: FiberId): Cause[Any] = cause.traced(toStackTrace(fiberId))
    }

    def succeed[A](a: => A): Effect[Nothing, A] = Sync(() => a)

    def fail[E](e: => E): Effect[E, Nothing] = failCause(Cause.fail(e))

    def failCause[E](c: => Cause[E]): Effect[E, Nothing] = succeed(throw RaisingError(c, ChunkBuilder.make()))

    def async[E, A](registerCallback: (Effect[E, A] => Unit) => Unit): Effect[E, A] = Async(registerCallback)

    def evalAsync[E, A](effect: Effect[E, A], onDone: Exit[E, A] => Unit): Exit[E, A] = {
      val fiberId = FiberId.None // TODO: FiberId

      def loop[A](effect: Effect[Any, Any], depth: Int, stack: Chunk[EvaluationStep], interruptible0: Boolean): A = {
        import EvaluationStep._

        var cur           = effect
        var done          = null.asInstanceOf[A with AnyRef]
        var stackIndex    = 0
        var interruptible = interruptible0

        if (depth > 1000) throw Trampoline(effect, ChunkBuilder.make())

        try {
          while (done eq null) {
            try {
              cur match {
                case effect0: OnSuccessOrFailure[_, _, _, _] =>
                  val effect = effect0.erase

                  try {
                    cur = effect.onSuccess(loop(effect.first, depth + 1, null, interruptible))
                  } catch {
                    case raisingError @ RaisingError(_, _) =>
                      cur = effect.onFailure(raisingError.tracedCause(fiberId))

                    case reifyStack: ReifyStack => reifyStack.addAndThrow(effect)
                  }

                case effect @ Sync(_) =>
                  val value = effect.eval()

                  cur = null

                  if (stack ne null) {
                    while ((cur eq null) && stackIndex < stack.length) {
                      stack(stackIndex) match {
                        case k: Continuation[_, _, _, _] => cur = k.erase.onSuccess(value)
                        case k: ChangeInterruptibility   => interruptible = k.interruptible
                      }

                      stackIndex += 1
                    }
                  }

                  if (cur eq null) done = value.asInstanceOf[A with AnyRef]

                case effect @ Async(registerCallback) =>
                  throw AsyncJump(effect.registerCallback, ChunkBuilder.make())

                case effect @ Ensuring(_, _, _) =>
                  ???

                case effect @ ChangeInterruptionWithin(_, _) =>
                  val oldInterruptible = interruptible

                  interruptible = effect.newInterruptible

                  try {
                    loop(effect.scope(oldInterruptible), depth + 1, null, interruptible)

                    interruptible = oldInterruptible
                  } catch {
                    case reifyStack: ReifyStack =>
                      // TODO: Persist `oldInterruptible` in the fiber state
                      reifyStack.addAndThrow(ChangeInterruptibility(oldInterruptible))
                  }
              }
            } catch {
              case raisingError @ RaisingError(_, _) =>
                cur = null

                if (stack ne null) {
                  while ((cur eq null) && stackIndex < stack.length) {
                    stack(stackIndex) match {
                      case k: Continuation[_, _, _, _] => cur = k.erase.onFailure(raisingError.tracedCause(fiberId))
                      case k: ChangeInterruptibility   => interruptible = k.interruptible
                    }

                    stackIndex += 1
                  }
                }

                if (cur eq null) throw raisingError

              case reifyStack: ReifyStack => throw reifyStack

              case throwable: Throwable => // TODO: If non-fatal
                cur = Effect.failCause(Cause.die(throwable))
            }
          }
        } catch {
          case reifyStack: ReifyStack =>
            if ((stack ne null) && stackIndex <= stack.length) {
              reifyStack.stack ++= stack.drop(stackIndex)
            }

            throw reifyStack
        }

        done
      }

      def resumeOuterLoop[E, A](
        effect: Effect[Any, Any],
        stack: Chunk[EvaluationStep],
        onDone: Exit[E, A] => Unit
      ): Unit =
        scala.concurrent.ExecutionContext.global.execute { () =>
          outerLoop[E, A](effect, stack, onDone, true) // TODO: Interruptibility
          ()
        }

      @tailrec
      def outerLoop[E, A](
        effect: Effect[Any, Any],
        stack: Chunk[EvaluationStep],
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

          case raisingError: RaisingError =>
            val exit = Exit.failCause(raisingError.tracedCause(fiberId).asInstanceOf[Cause[E]])

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
        } + 
        suite("defects") {
          test("death in succeed") {
            for {
              result <- ZIO.succeed(Effect.succeed(throw TestObj).exit.unsafeRun())
            } yield assertTrue(result.causeOption.get.defects(0) == TestObj)
          }
        }
    }
}

object TestObj extends Exception("Test exception")