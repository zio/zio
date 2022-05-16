package zio.internal

import zio.{Promise => _, _}

import zio.test._
import scala.concurrent._

object NewEncodingSpec extends ZIOBaseSpec {
  import scala.util.control.NoStackTrace
  import scala.annotation.tailrec

  sealed trait Effect[+E, +A] { self =>
    final def *>[E1 >: E, B](that: => Effect[E1, B]): Effect[E1, B] =
      self.flatMap(_ => that)

    final def <*[E1 >: E](that: => Effect[E1, Any]): Effect[E1, A] =
      self.flatMap(a => that.map(_ => a))

    final def catchAll[E2, A1 >: A](t: E => Effect[E2, A1])(implicit trace: ZTraceElement): Effect[E2, A1] =
      self.catchAllCause { cause =>
        cause.failureOrCause.fold(t, Effect.refailCause(_))
      }

    final def catchAllCause[E2, A1 >: A](t: Cause[E] => Effect[E2, A1])(implicit trace: ZTraceElement): Effect[E2, A1] =
      Effect.OnFailure(trace, self, t)

    final def ensuring(finalizer: Effect[Nothing, Any])(implicit trace: ZTraceElement): Effect[E, A] =
      Effect.uninterruptibleMask { restore =>
        restore(self).foldCauseZIO(cause => finalizer *> Effect.refailCause(cause), a => finalizer.map(_ => a))
      }

    final def exit(implicit trace: ZTraceElement): Effect[Nothing, Exit[E, A]] =
      self.map(Exit.succeed(_)).catchAllCause(cause => Effect.succeed(Exit.failCause(cause)))

    final def flatMap[E1 >: E, B](f: A => Effect[E1, B])(implicit trace: ZTraceElement): Effect[E1, B] =
      Effect.OnSuccess(trace, self, f)

    final def foldCauseZIO[E2, B](onError: Cause[E] => Effect[E2, B], onSuccess: A => Effect[E2, B])(implicit
      trace: ZTraceElement
    ): Effect[E2, B] =
      Effect.OnSuccessAndFailure(trace, self, onSuccess, onError)

    final def interruptible(implicit trace: ZTraceElement): Effect[E, A] =
      Effect.ChangeInterruptionWithin.Interruptible(trace, self)

    final def map[B](f: A => B)(implicit trace: ZTraceElement): Effect[E, B] =
      self.flatMap(a => Effect.succeed(f(a)))

    final def mapError[E2](f: E => E2)(implicit trace: ZTraceElement): Effect[E2, A] =
      self.catchAll(e => Effect.fail(f(e)))

    def trace: ZTraceElement

    final def uninterruptible(implicit trace: ZTraceElement): Effect[E, A] =
      Effect.ChangeInterruptionWithin.Uninterruptible(trace, self)
  }
  object Effect {
    implicit class EffectThrowableSyntax[A](self: Effect[Throwable, A]) {
      def unsafeRun(maxDepth: Int = 1000): A = Effect.eval(self, maxDepth)
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
      final case class UpdateTrace(trace: ZTraceElement) extends EvaluationStep
      sealed trait Continuation[E1, E2, A, B] extends EvaluationStep { self =>
        def trace: ZTraceElement

        def onSuccess(a: A): Effect[E2, B]

        def onFailure(c: Cause[E1]): Effect[E2, B]

        def erase: Continuation.Erased = self.asInstanceOf[Continuation.Erased]
      }
      object Continuation {
        type Erased = Continuation[Any, Any, Any, Any]

        def ensuring[E, A](finalizer: Effect[Nothing, Any])(implicit trace0: ZTraceElement): Continuation[E, E, A, A] =
          new Continuation[E, E, A, A] {
            def trace                  = trace0
            def onSuccess(a: A)        = finalizer.flatMap(_ => Effect.succeed(a))
            def onFailure(c: Cause[E]) = finalizer.flatMap(_ => Effect.refailCause(c))
          }

        def fromSuccess[E, A, B](f: A => Effect[E, B])(implicit trace0: ZTraceElement): Continuation[E, E, A, B] =
          new Continuation[E, E, A, B] {
            def trace                  = trace0
            def onSuccess(a: A)        = f(a)
            def onFailure(c: Cause[E]) = Effect.refailCause(c)
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

    final case class Sync[A](trace: ZTraceElement, eval: () => A) extends Effect[Nothing, A]
    final case class Async[E, A](trace: ZTraceElement, registerCallback: (Effect[E, A] => Unit) => Unit)
        extends Effect[E, A]
    sealed trait OnSuccessOrFailure[E1, E2, A, B] extends Effect[E2, B] with EvaluationStep.Continuation[E1, E2, A, B] {
      self =>
      def first: Effect[E1, A]

      final override def erase: OnSuccessOrFailure[Any, Any, Any, Any] =
        self.asInstanceOf[OnSuccessOrFailure[Any, Any, Any, Any]]
    }
    final case class OnSuccessAndFailure[E1, E2, A, B](
      trace: ZTraceElement,
      first: Effect[E1, A],
      successK: A => Effect[E2, B],
      failureK: Cause[E1] => Effect[E2, B]
    ) extends OnSuccessOrFailure[E1, E2, A, B] {
      def onFailure(c: Cause[E1]): Effect[E2, B] = failureK(c)

      def onSuccess(a: A): Effect[E2, B] = successK(a.asInstanceOf[A])
    }
    final case class OnSuccess[A, E, B](trace: ZTraceElement, first: Effect[E, A], successK: A => Effect[E, B])
        extends OnSuccessOrFailure[E, E, A, B] {
      def onFailure(c: Cause[E]): Effect[E, B] = Effect.refailCause(c)

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
    sealed trait ChangeInterruptionWithin[E, A] extends Effect[E, A] {
      def newInterruptible: Boolean

      def scope(oldInterruptible: Boolean): Effect[E, A]
    }
    object ChangeInterruptionWithin {
      final case class Interruptible[E, A](trace: ZTraceElement, effect: Effect[E, A])
          extends ChangeInterruptionWithin[E, A] {
        def newInterruptible: Boolean = true

        def scope(oldInterruptible: Boolean): Effect[E, A] = effect
      }
      final case class Uninterruptible[E, A](trace: ZTraceElement, effect: Effect[E, A])
          extends ChangeInterruptionWithin[E, A] {
        def newInterruptible: Boolean = false

        def scope(oldInterruptible: Boolean): Effect[E, A] = effect
      }
      final case class Dynamic[E, A](trace: ZTraceElement, newInterruptible: Boolean, f: Boolean => Effect[E, A])
          extends ChangeInterruptionWithin[E, A] {
        def scope(oldInterruptible: Boolean): Effect[E, A] = f(oldInterruptible)
      }
    }
    final case class GenerateStackTrace(trace: ZTraceElement) extends Effect[Nothing, ZTrace]
    final case class Stateful[E, A](
      trace: ZTraceElement,
      onState: (FiberState2[E, A], Boolean, ZTraceElement) => Effect[E, A]
    ) extends Effect[E, A] { self =>
      def erase: Stateful[Any, Any] = self.asInstanceOf[Stateful[Any, Any]]
    }
    final case class Refail[E](cause: Cause[E]) extends Effect[E, Nothing] {
      def trace: ZTraceElement = ZTraceElement.empty
    }

    type Erased = Effect[Any, Any]

    sealed abstract class ReifyStack extends Exception with NoStackTrace { self =>
      def addContinuation(continuation: EvaluationStep.Continuation[_, _, _, _]): Nothing =
        self.addAndThrow(continuation)

      def changeInterruptibility(interruptible: Boolean): Nothing =
        self.addAndThrow(EvaluationStep.ChangeInterruptibility(interruptible))

      def stack: ChunkBuilder[EvaluationStep]

      private final def addAndThrow(k: EvaluationStep): Nothing = {
        stack += (k)
        throw this
      }
    }
    object ReifyStack {
      final case class AsyncJump(
        registerCallback: (Effect[Any, Any] => Unit) => Unit,
        stack: ChunkBuilder[EvaluationStep]
      ) extends ReifyStack

      final case class Trampoline(effect: Effect[Any, Any], stack: ChunkBuilder[EvaluationStep]) extends ReifyStack

      final case class TraceGen(stack: ChunkBuilder[EvaluationStep]) extends ReifyStack
    }

    import ReifyStack.{AsyncJump, Trampoline, TraceGen}

    def async[E, A](registerCallback: (Effect[E, A] => Unit) => Unit)(implicit trace: ZTraceElement): Effect[E, A] =
      Async(trace, registerCallback)

    def fail[E](e: => E)(implicit trace: ZTraceElement): Effect[E, Nothing] = failCause(Cause.fail(e))

    def failCause[E](c: => Cause[E])(implicit trace0: ZTraceElement): Effect[E, Nothing] =
      Effect.trace(trace0).flatMap(trace => refailCause(c.traced(trace)))

    def refailCause[E](cause: Cause[E])(implicit trace: ZTraceElement): Effect[E, Nothing] = Refail(cause)

    def succeed[A](a: => A)(implicit trace: ZTraceElement): Effect[Nothing, A] = Sync(trace, () => a)

    def trace(implicit trace: ZTraceElement): Effect[Nothing, ZTrace] =
      GenerateStackTrace(trace)

    final case class ZIOError(cause: Cause[Any]) extends Exception with NoStackTrace

    sealed trait InterruptibilityRestorer {
      def apply[E, A](effect: Effect[E, A])(implicit trace: ZTraceElement): Effect[E, A]
    }
    object InterruptibilityRestorer {
      case object MakeInterruptible extends InterruptibilityRestorer {
        def apply[E, A](effect: Effect[E, A])(implicit trace: ZTraceElement): Effect[E, A] =
          Effect.ChangeInterruptionWithin.Interruptible(trace, effect)
      }
      case object MakeUninterruptible extends InterruptibilityRestorer {
        def apply[E, A](effect: Effect[E, A])(implicit trace: ZTraceElement): Effect[E, A] =
          Effect.ChangeInterruptionWithin.Uninterruptible(trace, effect)
      }
    }

    def uninterruptibleMask[E, A](
      f: InterruptibilityRestorer => Effect[E, A]
    )(implicit trace: ZTraceElement): Effect[E, A] =
      Effect.ChangeInterruptionWithin.Dynamic(
        trace,
        false,
        old =>
          if (old) f(InterruptibilityRestorer.MakeInterruptible)
          else f(InterruptibilityRestorer.MakeUninterruptible)
      )

    val unit: Effect[Nothing, Unit] = Effect.succeed(())(ZTraceElement.empty)

    def yieldNow(implicit trace: ZTraceElement): Effect[Nothing, Unit] =
      async[Nothing, Unit](k => k(Effect.unit))

    private def assertNonNull(a: Any, message: String, location: ZTraceElement): Unit =
      if (a == null) {
        throw new NullPointerException(message + ": " + k.trace.toString)
      }

    def evalAsync[E, A](
      effect: Effect[E, A],
      onDone: Exit[E, A] => Unit,
      maxDepth: Int = 1000,
      fiberRefs0: FiberRefs = FiberRefs.empty
    )(implicit trace0: ZTraceElement): Exit[E, A] = {
      def loop(
        fiberState: FiberState2[Any, Any],
        effect: Effect[Any, Any],
        depth: Int,
        stack: Chunk[EvaluationStep],
        interruptible0: Boolean
      ): AnyRef = {
        import EvaluationStep._

        var cur           = effect
        var done          = null.asInstanceOf[AnyRef]
        var stackIndex    = 0
        var interruptible = interruptible0
        var lastTrace     = null.asInstanceOf[ZTraceElement]

        if (depth > maxDepth) {
          // Save local variables to heap:
          fiberState.setInterruptible(interruptible)

          val builder = ChunkBuilder.make[EvaluationStep]()

          builder ++= stack

          throw Trampoline(effect, builder)
        }

        while (done eq null) {
          val nextTrace = cur.trace
          if (nextTrace ne ZTraceElement.empty) lastTrace = nextTrace

          try {
            cur match {
              case effect0: OnSuccessOrFailure[_, _, _, _] =>
                val effect = effect0.erase

                try {
                  cur = effect.onSuccess(loop(fiberState, effect.first, depth + 1, Chunk.empty, interruptible))
                } catch {
                  case zioError1: ZIOError =>
                    cur =
                      try {
                        effect.onFailure(zioError1.cause)
                      } catch {
                        case zioError2: ZIOError => Refail(zioError1.cause.stripFailures ++ zioError2.cause)
                      }

                  case reifyStack: ReifyStack => reifyStack.addContinuation(effect)
                }

              case effect: Sync[_] =>
                try {
                  val value = effect.eval()

                  cur = null

                  while ((cur eq null) && stackIndex < stack.length) {
                    val element = stack(stackIndex)

                    stackIndex += 1

                    element match {
                      case k: Continuation[_, _, _, _] =>
                        assertNonNull(
                          cur,
                          "The return value of success & failure handlers must always be an effect",
                          k.trace
                        )
                        cur = k.erase.onSuccess(value)

                      case k: ChangeInterruptibility => interruptible = k.interruptible
                      case k: UpdateTrace            => if (k.trace ne ZTraceElement.empty) lastTrace = k.trace
                    }
                  }

                  if (cur eq null) done = value.asInstanceOf[AnyRef]
                } catch {
                  case zioError: ZIOError =>
                    cur = Refail(zioError.cause)
                }

              case effect: Async[_, _] =>
                // Save local variables to heap:
                fiberState.setInterruptible(interruptible)

                throw AsyncJump(effect.registerCallback, ChunkBuilder.make())

              case effect: ChangeInterruptionWithin[_, _] =>
                val oldInterruptible = interruptible

                interruptible = effect.newInterruptible

                cur =
                  try {
                    val value = loop(fiberState, effect.scope(oldInterruptible), depth + 1, Chunk.empty, interruptible)

                    interruptible = oldInterruptible

                    Effect.succeed(value)
                  } catch {
                    case reifyStack: ReifyStack => reifyStack.changeInterruptibility(oldInterruptible)
                  }

              case generateStackTrace: GenerateStackTrace =>
                val builder = ChunkBuilder.make[EvaluationStep]()

                builder += EvaluationStep.UpdateTrace(generateStackTrace.trace)

                // Save local variables to heap:
                fiberState.setInterruptible(interruptible)

                throw TraceGen(builder)

              case stateful: Stateful[_, _] =>
                cur = stateful.erase.onState(fiberState, interruptible, lastTrace)

              case refail: Refail[_] =>
                var cause = refail.cause.asInstanceOf[Cause[Any]]

                cur = null

                while ((cur eq null) && stackIndex < stack.length) {
                  val element = stack(stackIndex)

                  stackIndex += 1

                  element match {
                    case k: Continuation[_, _, _, _] =>
                      try {
                        cur = k.erase.onFailure(cause)

                        assertNonNull(
                          cur,
                          "The return value of success & failure handlers must always be an effect",
                          k.trace
                        )
                      } catch {
                        case zioError: ZIOError =>
                          cause = cause.stripFailures ++ zioError.cause
                      }
                    case k: ChangeInterruptibility => interruptible = k.interruptible
                    case k: UpdateTrace            => if (k.trace ne ZTraceElement.empty) lastTrace = k.trace
                  }
                }

                if (cur eq null) throw ZIOError(cause)
            }
          } catch {
            case zioError: ZIOError => throw zioError

            case reifyStack: ReifyStack =>
              if (stackIndex < stack.length) reifyStack.stack ++= stack.drop(stackIndex)

              throw reifyStack

            case throwable: Throwable => // TODO: If non-fatal
              cur = Refail(Cause.die(throwable))
          }
        }

        done
      }

      def resumeOuterLoop(
        fiberState: FiberState2[Any, Any],
        effect: Effect[Any, Any],
        stack: Chunk[EvaluationStep],
        onDone: Exit[Any, Any] => Unit
      ): Unit =
        scala.concurrent.ExecutionContext.global.execute { () =>
          outerLoop(fiberState, effect, stack, onDone)
          ()
        }

      @tailrec
      def outerLoop(
        fiberState: FiberState2[Any, Any],
        effect: Effect[Any, Any],
        stack: Chunk[EvaluationStep],
        onDone: Exit[Any, Any] => Unit
      ): Exit[Any, Any] =
        try {
          val interruptible = fiberState.getInterruptible()

          val exit: Exit[Nothing, Any] = Exit.succeed(loop(fiberState, effect, 0, stack, interruptible))

          onDone(exit)

          exit
        } catch {
          case trampoline: Trampoline =>
            outerLoop(fiberState, trampoline.effect, trampoline.stack.result(), onDone)

          case asyncJump: AsyncJump =>
            asyncJump.registerCallback { value =>
              resumeOuterLoop(fiberState, value, asyncJump.stack.result(), onDone)
            }

            null

          case zioError: ZIOError =>
            val exit = Exit.failCause(zioError.cause)

            onDone(exit)

            exit

          case traceGen: TraceGen =>
            val stack = traceGen.stack.result() // TODO: Don't build it, just iterate over it!

            val builder = StackTraceBuilder.unsafeMake()

            stack.foreach(k => builder += k.trace)

            val trace = ZTrace(fiberState.fiberId, builder.result())

            outerLoop(fiberState, Effect.succeed(trace), stack, onDone)
        }

      val fiberState = FiberState2[Any, Any](trace0, fiberRefs0)

      outerLoop(fiberState, effect, Chunk.empty, onDone.asInstanceOf[Exit[Any, Any] => Unit]).asInstanceOf[Exit[E, A]]
    }

    def evalToFuture[A](effect: Effect[Throwable, A], maxDepth: Int = 1000): scala.concurrent.Future[A] = {
      val promise = Promise[A]()

      evalAsync[Throwable, A](effect, exit => promise.complete(exit.toTry), maxDepth) match {
        case null => promise.future

        case exit => Future.fromTry(exit.toTry)
      }
    }

    def eval[A](effect: Effect[Throwable, A], maxDepth: Int = 1000): A = {
      import java.util.concurrent._
      import scala.concurrent.duration._

      val future = evalToFuture(effect, maxDepth)

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

  def runFibTest(num: Int, maxDepth: Int = 1000) =
    test(s"fib(${num})") {
      for {
        actual   <- ZIO.succeed(newFib(num).unsafeRun(maxDepth))
        expected <- oldFib(num)
      } yield assertTrue(actual == expected)
    }

  def newSum(n: Int): Effect[Nothing, Int] =
    Effect.succeed(n).flatMap(n => if (n <= 0) Effect.succeed(0) else newSum(n - 1).map(_ + n))

  def oldSum(n: Int): ZIO[Any, Nothing, Int] =
    ZIO.succeed(n).flatMap(n => if (n <= 0) ZIO.succeed(0) else oldSum(n - 1).map(_ + n))

  def runSumTest(num: Int, maxDepth: Int = 1000) =
    test(s"sum(${num})") {
      for {
        actual   <- ZIO.succeed(newSum(num).unsafeRun(maxDepth))
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

  def runFailAfterTest(num: Int, maxDepth: Int = 1000) =
    test(s"failAfter(${num})") {
      for {
        actual   <- ZIO.succeed(newFailAfter(num).unsafeRun(maxDepth))
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

  def runTerminalFailTest(num: Int, maxDepth: Int = 1000) =
    test(s"terminalFail(${num})") {
      for {
        actual   <- ZIO.succeed(newTerminalFail(num).unsafeRun(maxDepth))
        expected <- oldTerminalFail(num)
      } yield assertTrue(actual == expected)
    }

  def runAsyncAfterTest(num: Int, maxDepth: Int = 1000) =
    test(s"asyncAfter(${num})") {
      for {
        actual   <- ZIO.succeed(newAsyncAfter(num).unsafeRun(maxDepth))
        expected <- oldAsyncAfter(num)
      } yield assertTrue(actual == expected)
    }

  def secondLevelCallStack =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- Effect.trace
    } yield t

  def firstLevelCallStack =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- secondLevelCallStack
    } yield t

  def stackTraceTest1 =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- firstLevelCallStack
    } yield t

  def secondLevelCallStackFail =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- Effect.fail("Uh oh!")
    } yield t

  def firstLevelCallStackFail =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- secondLevelCallStackFail
    } yield t

  def stackTraceTest2 =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- firstLevelCallStackFail
    } yield t

  def spec =
    suite("NewEncodingSpec") {
      suite("stack traces") {
        test("2nd-level trace") {
          for {
            t <- ZIO.succeed(stackTraceTest1.unsafeRun())
          } yield assertTrue(t.size == 3) &&
            assertTrue(t.stackTrace(0).toString().contains("secondLevelCallStack")) &&
            assertTrue(t.stackTrace(1).toString().contains("firstLevelCallStack")) &&
            assertTrue(t.stackTrace(2).toString().contains("stackTraceTest1"))
        } +
          test("2nd-level auto-trace through fail") {
            for {
              exit <- ZIO.succeed(stackTraceTest2.exit.unsafeRun())
              t     = exit.causeOption.get.trace
            } yield assertTrue(t.size == 4) &&
              assertTrue(t.stackTrace(0).toString().contains("secondLevelCallStackFail")) &&
              assertTrue(t.stackTrace(1).toString().contains("firstLevelCallStackFail")) &&
              assertTrue(t.stackTrace(2).toString().contains("stackTraceTest2")) &&
              assertTrue(t.stackTrace(3).toString().contains("spec"))
          }
      } +
        suite("fib") {
          runFibTest(0) +
            runFibTest(5) +
            runFibTest(10) +
            runFibTest(20)
        } +
        suite("fib - trampoline stress") {
          runFibTest(0, 2) +
            runFibTest(5, 2) +
            runFibTest(10, 2) +
            runFibTest(20, 2)
        } +
        suite("sum") {
          runSumTest(0) +
            runSumTest(100) +
            runSumTest(1000) +
            runSumTest(10000)
        } +
        suite("sum - trampoline stress") {
          runSumTest(0, 2) +
            runSumTest(100, 2) +
            runSumTest(1000, 2) +
            runSumTest(10000, 2)
        } +
        suite("failAfter") {
          runFailAfterTest(0) +
            runFailAfterTest(100) +
            runFailAfterTest(1000) +
            runFailAfterTest(10000)
        } +
        suite("failAfter - trampoline stress") {
          runFailAfterTest(0, 2) +
            runFailAfterTest(100, 2) +
            runFailAfterTest(1000, 2) +
            runFailAfterTest(10000, 2)
        } +
        suite("asyncAfter") {
          runAsyncAfterTest(0) +
            runAsyncAfterTest(100) +
            runAsyncAfterTest(1000) +
            runAsyncAfterTest(10000)
        } +
        suite("asyncAfter - trampoline stress") {
          runAsyncAfterTest(0, 2) +
            runAsyncAfterTest(100, 2) +
            runAsyncAfterTest(1000, 2) +
            runAsyncAfterTest(10000, 2)
        } +
        suite("terminalFail") {
          runTerminalFailTest(0) +
            runTerminalFailTest(100) +
            runTerminalFailTest(1000) +
            runTerminalFailTest(10000)
        } +
        suite("terminalFail - trampoline stress") {
          runTerminalFailTest(0, 2) +
            runTerminalFailTest(100, 2) +
            runTerminalFailTest(1000, 2) +
            runTerminalFailTest(10000, 2)
        } +
        suite("defects") {
          test("death in succeed") {
            for {
              result <- ZIO.succeed(Effect.succeed(throw TestException).exit.unsafeRun())
            } yield assertTrue(result.causeOption.get.defects(0) == TestException)
          } +
            test("death in succeed after async") {
              for {
                result <-
                  ZIO.succeed((Effect.unit *> Effect.yieldNow *> Effect.succeed(throw TestException)).exit.unsafeRun())
              } yield assertTrue(result.causeOption.get.defects(0) == TestException)
            } +
            suite("finalizers") {
              test("ensuring - success") {
                var finalized = false

                val finalize = Effect.succeed { finalized = true }

                for {
                  _ <- ZIO.succeed(Effect.succeed(()).ensuring(finalize).exit.unsafeRun())
                } yield assertTrue(finalized == true)
              } +
                test("ensuring - success after async") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           (Effect.unit *> Effect.yieldNow *> Effect.succeed(())).ensuring(finalize).exit.unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                } +
                test("ensuring - failure") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(Effect.fail(()).ensuring(finalize).exit.unsafeRun())
                  } yield assertTrue(finalized == true)
                } +
                test("ensuring - failure after async") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           (Effect.unit *> Effect.yieldNow *> Effect.fail(())).ensuring(finalize).exit.unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                } +
                test("ensuring - double failure") {
                  var finalized = false

                  val finalize1 = Effect.succeed(throw TestException)
                  val finalize2 = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           Effect
                             .fail(())
                             .ensuring(finalize1)
                             .ensuring(finalize2)
                             .exit
                             .unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                } +
                test("foldCauseZIO finalization - success") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(Effect.succeed(()).foldCauseZIO(_ => finalize, _ => finalize).exit.unsafeRun())
                  } yield assertTrue(finalized == true)
                } +
                test("foldCauseZIO finalization - failure") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(Effect.fail(()).foldCauseZIO(_ => finalize, _ => finalize).exit.unsafeRun())
                  } yield assertTrue(finalized == true)
                } +
                test("foldCauseZIO nested finalization - double failure") {
                  var finalized = false

                  val finalize1 = Effect.succeed(throw TestException)
                  val finalize2 = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           Effect
                             .fail(())
                             .foldCauseZIO(_ => finalize1, _ => finalize1)
                             .foldCauseZIO(_ => finalize2, _ => finalize2)
                             .exit
                             .unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                }
            }
        }
    }
}

object TestException extends Exception("Test exception")
