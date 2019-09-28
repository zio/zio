package zio

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

// import com.github.ghik.silencer.silent
// import zio.Cause.{ die, fail, Fail, Then }
import zio.Cause.die
import zio.LatchOps._
import zio.RTSSpecHelper._
import zio.clock.Clock
import zio.duration._
import zio.internal.PlatformLive
import zio.test._
import zio.test.Assertion._
import zio.test.TestUtils.nonFlaky

import scala.annotation.tailrec
// import scala.util.{ Failure, Success }

object RTSSpec
    extends ZIOBaseSpec(
      suite("RTSSpec")(
        suite("RTS synchronous correctness")(
          testM("widen Nothing") {
            val op1 = IO.effectTotal[String]("1")
            val op2 = IO.effectTotal[String]("2")

            for {
              r1 <- op1
              r2 <- op2
            } yield assert(r1 + r2, equalTo("12"))
          },
          testM("blocking caches threads") {
            import zio.blocking.Blocking

            def runAndTrack(ref: Ref[Set[Thread]]): ZIO[Blocking with Clock, Nothing, Boolean] =
              blocking.blocking {
                UIO(Thread.currentThread())
                  .flatMap(thread => ref.modify(set => (set.contains(thread), set + thread))) <* ZIO
                  .sleep(1.millis)
              }

            val io =
              for {
                accum <- Ref.make(Set.empty[Thread])
                b     <- runAndTrack(accum).repeat(Schedule.doUntil[Boolean](_ == true))
              } yield b

            val env = new Clock.Live with Blocking.Live

            assertM(io.provide(env), isTrue)
          },
          testM("now must be eager") {
            val io =
              try {
                IO.succeed(throw ExampleError)
                IO.succeed(false)
              } catch {
                case _: Throwable => IO.succeed(true)
              }

            assertM(io, isTrue)
          },
          testM("effectSuspend must be lazy") {
            val io =
              try {
                IO.effectSuspend(throw ExampleError)
                IO.succeed(false)
              } catch {
                case _: Throwable => IO.succeed(true)
              }

            assertM(io, isFalse)
          },
          testM("effectSuspendTotal must not catch throwable") {
            Stub
          },
          testM("effectSuspend must catch throwable") {
            val zio = ZIO.effectSuspend[Any, Nothing](throw ExampleError).either
            assertM(zio, isLeft(equalTo(ExampleError)))
          },
          testM("effectSuspendWith must catch throwable") {
            val zio = ZIO.effectSuspendWith[Any, Nothing](_ => throw ExampleError).either
            assertM(zio, isLeft(equalTo(ExampleError)))
          },
          testM("suspend must be evaluatable") {
            assertM(IO.effectSuspendTotal(IO.effectTotal(42)), equalTo(42))
          },
          testM("point, bind, map") {
            def fibIo(n: Int): Task[BigInt] =
              if (n <= 1) IO.succeed(n)
              else
                for {
                  a <- fibIo(n - 1)
                  b <- fibIo(n - 2)
                } yield a + b

            assertM(fibIo(10), equalTo(fib(10)))
          },
          testM("effect, bind, map") {
            def fibIo(n: Int): Task[BigInt] =
              if (n <= 1) IO.effect(n)
              else
                for {
                  a <- fibIo(n - 1)
                  b <- fibIo(n - 2)
                } yield a + b

            assertM(fibIo(10), equalTo(fib(10)))
          },
          testM("effect, bind, map, redeem") {
            def fibIo(n: Int): Task[BigInt] =
              if (n <= 1) Task.effect[BigInt](throw ExampleError).catchAll(_ => Task.effect(n))
              else
                for {
                  a <- fibIo(n - 1)
                  b <- fibIo(n - 2)
                } yield a + b

            assertM(fibIo(10), equalTo(fib(10)))
          },
          testM("sync effect") {
            def sumIo(n: Int): Task[Int] =
              if (n <= 0) IO.effectTotal(0)
              else IO.effectTotal(n).flatMap(b => sumIo(n - 1).map(a => a + b))

            assertM(sumIo(1000), equalTo(sum(1000)))
          },
          testM("deep effects") {
            def incLeft(n: Int, ref: Ref[Int]): Task[Int] =
              if (n <= 0) ref.get
              else incLeft(n - 1, ref) <* ref.update(_ + 1)

            def incRight(n: Int, ref: Ref[Int]): Task[Int] =
              if (n <= 0) ref.get
              else ref.update(_ + 1) *> incRight(n - 1, ref)

            val l =
              for {
                ref <- Ref.make(0)
                v   <- incLeft(100, ref)
              } yield v == 0

            val r =
              for {
                ref <- Ref.make(0)
                v   <- incRight(1000, ref)
              } yield v == 1000

            assertM(l.zipWith(r)(_ && _), isTrue)
          },
          testM("flip must make error into value") {
            val io = IO.fail(ExampleError).flip
            assertM(io, equalTo(ExampleError))
          },
          testM("flip must make value into error") {
            val io = IO.succeed(42).flip
            assertM(io.either, isLeft(equalTo(42)))
          },
          testM("flipping twice returns identical value") {
            val io = IO.succeed(42)
            assertM(io.flip.flip, equalTo(42))
          }
        ),
        suite("RTS failure")(
          testM("error in sync effect") {
            val io = IO.effect[Unit](throw ExampleError).fold[Option[Throwable]](Some(_), _ => None)
            assertM(io, isSome(equalTo(ExampleError)))
          },
          testM("attempt . fail") {
            val io1 = TaskExampleError.either
            val io2 = IO.effectSuspendTotal(IO.effectSuspendTotal(TaskExampleError).either)

            (io1 <*> io2).map {
              case (r1, r2) =>
                assert(r1, isLeft(equalTo(ExampleError))) && assert(r2, isLeft(equalTo(ExampleError)))
            }
          },
          testM("deep attempt sync effect error") {
            assertM(deepErrorEffect(100).either, isLeft(equalTo(ExampleError)))
          },
          testM("deep attempt fail error") {
            assertM(deepErrorFail(100).either, isLeft(equalTo(ExampleError)))
          },
          testM("attempt . sandbox . terminate") {
            val io = IO.effectTotal[Int](throw ExampleError).sandbox.either
            assertM(io, isLeft(equalTo(die(ExampleError))))
          },
          testM("fold . sandbox . terminate") {
            val io = IO.effectTotal[Int](throw ExampleError).sandbox.fold(Some(_), Function.const(None))
            assertM(io, isSome(equalTo(die(ExampleError))))
          },
          testM("catch sandbox terminate") {
            Stub
          },
          testM("uncaught fail") {
            Stub
          },
          testM("uncaught fail supervised") {
            Stub
          },
          testM("uncaught sync effect error") {
            Stub
          },
          testM("uncaught supervised sync effect error") {
            Stub
          },
          testM("deep uncaught sync effect error") {
            Stub
          },
          testM("deep uncaught fail") {
            Stub
          },
          testM("catch failing finalizers with fail") {
            Stub
          },
          testM("catch failing finalizers with terminate") {
            Stub
          },
          testM("run preserves interruption status") {
            Stub
          },
          testM("run swallows inner interruption") {
            Stub
          },
          testM("timeout a long computation") {
            Stub
          },
          testM("catchAllCause") {
            Stub
          },
          testM("exception in fromFuture does not kill fiber") {
            Stub
          }
        ),
        suite("RTS finalizers")(
          testM("fail ensuring") {
            Stub
          },
          testM("fail on error") {
            Stub
          },
          testM("finalizer errors not caught") {
            Stub
          },
          testM("finalizer errors reported") {
            Stub
          },
          testM("bracket exit is usage result") {
            Stub
          },
          testM("error in just acquisition") {
            Stub
          },
          testM("error in just release") {
            Stub
          },
          testM("error in just usage") {
            Stub
          },
          testM("rethrown caught error in acquisition") {
            Stub
          },
          testM("rethrown caught error in release") {
            Stub
          },
          testM("rethrown caught error in usage") {
            Stub
          },
          testM("test eval of async fail") {
            Stub
          },
          testM("bracket regression 1") {
            Stub
          },
          testM("interrupt waits for finalizer") {
            Stub
          }
        ),
        suite("RTS synchronous stack safety")(
          testM("deep map of now") {
            assertM(deepMapNow(10000), equalTo(10000))
          },
          testM("deep map of sync effect") {
            assertM(deepMapEffect(10000), equalTo(10000))
          },
          testM("deep attempt") {
            val io = (0 until 10000).foldLeft(IO.effect(())) { (acc, _) =>
              acc.either.unit
            }
            assertM(io, equalTo(()))
          },
          testM("deep flatMap") {
            def fib(n: Int, a: BigInt = 0, b: BigInt = 1): IO[Error, BigInt] =
              IO.succeed(a + b).flatMap { b2 =>
                if (n > 0)
                  fib(n - 1, b, b2)
                else
                  IO.succeed(b2)
              }

            val expected = BigInt(
              "113796925398360272257523782552224175572745930353730513145086634176691092536145985470146129334641866902783673042322088625863396052888690096969577173696370562180400527049497109023054114771394568040040412172632376"
            )

            assertM(fib(1000), equalTo(expected))
          },
          testM("deep absolve/attempt is identity") {
            val io = (0 until 1000).foldLeft(IO.succeed(42)) { (acc, _) =>
              IO.absolve(acc.either)
            }

            assertM(io, equalTo(42))
          },
          testM("deep async absolve/attempt is identity") {
            val io = (0 until 1000).foldLeft(IO.effectAsync[Int, Int](k => k(IO.succeed(42)))) { (acc, _) =>
              IO.absolve(acc.either)
            }

            assertM(io, equalTo(42))
          }
        ),
        suite("RTS asynchronous correctness")(
          testM("simple async must return") {
            val io = IO.effectAsync[Throwable, Int](k => k(IO.succeed(42)))
            assertM(io, equalTo(42))
          },
          testM("simple asyncIO must return") {
            val io = IO.effectAsyncM[Throwable, Int](k => IO.effectTotal(k(IO.succeed(42))))
            assertM(io, equalTo(42))
          },
          testM("deep asyncIO doesn't block threads") {
            Stub
          },
          testM("interrupt of asyncPure register") {
            for {
              release <- Promise.make[Nothing, Unit]
              acquire <- Promise.make[Nothing, Unit]
              fiber <- IO
                        .effectAsyncM[Nothing, Unit] { _ =>
                          acquire.succeed(()).bracket(_ => release.succeed(()))(_ => IO.never)
                        }
                        .fork
              _ <- acquire.await
              _ <- fiber.interrupt.fork
              a <- release.await
            } yield assert(a, isUnit)
          },
          testM("sleep 0 must return") {
            Stub
          },
          testM("shallow bind of async chain") {
            val io = (0 until 10).foldLeft[Task[Int]](IO.succeed[Int](0)) { (acc, _) =>
              acc.flatMap(n => IO.effectAsync[Throwable, Int](_(IO.succeed(n + 1))))
            }

            assertM(io, equalTo(10))
          },
          testM("effectAsyncM can fail before registering") {
            val zio = ZIO
              .effectAsyncM[Any, String, Nothing](_ => ZIO.fail("Ouch"))
              .flip

            assertM(zio, equalTo("Ouch"))
          },
          testM("effectAsyncM can defect before registering") {
            val zio = ZIO
              .effectAsyncM[Any, String, Unit](_ => ZIO.effectTotal(throw new Error("Ouch")))
              .run
              .map(_.fold(_.defects.headOption.map(_.getMessage), _ => None))

            assertM(zio, isSome(equalTo("Ouch")))
          },
          testM("second callback call is ignored") {
            for {
              _ <- IO.effectAsync[Throwable, Int] { k =>
                    k(IO.succeed(42))
                    Thread.sleep(500)
                    k(IO.succeed(42))
                  }
              res <- IO.effectAsync[Throwable, String] { k =>
                      Thread.sleep(1000)
                      k(IO.succeed("ok"))
                    }
            } yield assert(res, equalTo("ok"))
          }
        ),
        suite("RTS concurrency correctness")(
          testM("shallow fork/join identity") {
            Stub
          },
          testM("deep fork/join identity") {
            Stub
          },
          testM("asyncPure creation is interruptible") {
            Stub
          },
          testM("asyncInterrupt runs cancel token on interrupt") {
            Stub
          },
          testM("supervising returns fiber refs") {
            Stub
          },
          testM("supervising in unsupervised returns Nil") {
            Stub
          },
          testM("supervise fibers") {
            Stub
          },
          testM("supervise fibers in supervised") {
            Stub
          },
          testM("supervise fibers in race") {
            Stub
          },
          testM("supervise fibers in fork") {
            Stub
          },
          testM("race of fail with success") {
            Stub
          },
          testM("race of terminate with success") {
            Stub
          },
          testM("race of fail with fail") {
            Stub
          },
          testM("race of value & never") {
            Stub
          },
          testM("firstSuccessOf of values") {
            Stub
          },
          testM("firstSuccessOf of failures") {
            Stub
          },
          testM("firstSuccessOF of failures & 1 success") {
            Stub
          },
          testM("raceAttempt interrupts loser on success") {
            Stub
          },
          testM("raceAttempt interrupts loser on failure") {
            Stub
          },
          testM("par regression") {
            Stub
          },
          testM("par of now values") {
            Stub
          },
          testM("mergeAll") {
            Stub
          },
          testM("mergeAllEmpty") {
            Stub
          },
          testM("reduceAll") {
            Stub
          },
          testM("reduceAll Empty List") {
            Stub
          },
          testM("timeout of failure") {
            Stub
          },
          testM("timeout of terminate") {
            Stub
          }
        ),
        suite("RTS regression tests")(
          testM("deadlock regression 1") {
            import java.util.concurrent.Executors

            val rts = new DefaultRuntime {}
            val e   = Executors.newSingleThreadExecutor()

            (0 until 10000).foreach { _ =>
              rts.unsafeRun {
                IO.effectAsync[Nothing, Int] { k =>
                  val c: Callable[Unit] = () => k(IO.succeed(1))
                  val _                 = e.submit(c)
                }
              }
            }

            assertM(ZIO.effect(e.shutdown()), isUnit)
          },
          testM("check interruption regression 1") {
            val c = new AtomicInteger(0)

            def test =
              IO.effect(if (c.incrementAndGet() <= 1) throw new RuntimeException("x"))
                .forever
                .ensuring(IO.unit)
                .either
                .forever

            val zio =
              for {
                f <- test.fork
                c <- (IO.effectTotal[Int](c.get) <* clock.sleep(1.millis))
                      .repeat(ZSchedule.doUntil[Int](_ >= 1)) <* f.interrupt
              } yield c

            assertM(zio.provide(Clock.Live), isGreaterThanEqualTo(1))
          },
          testM("max yield Ops 1") {
            val rts = new DefaultRuntime {
              override val Platform = PlatformLive.makeDefault(1)
            }

            val io =
              for {
                _ <- UIO.unit
                _ <- UIO.unit
              } yield true

            assertM(ZIO.effect(rts.unsafeRun(io)), isTrue)
          }
        ),
        suite("RTS option tests")(
          testM("lifting a value to an option") {
            assertM(ZIO.some(42), isSome(equalTo(42)))
          },
          testM("using the none value") {
            assertM(ZIO.none, isNone)
          }
        ),
        suite("RTS either helper tests")(
          testM("lifting a value into right") {
            assertM(ZIO.right(42), isRight(equalTo(42)))
          },
          testM("lifting a value into left") {
            assertM(ZIO.left(42), isLeft(equalTo(42)))
          }
        ),
        suite("RTS interruption")(
          testM("blocking IO is effect blocking") {
            Stub
          },
          testM("sync forever is interruptible") {
            Stub
          },
          testM("interrupt of never") {
            Stub
          },
          testM("asyncPure is interruptible") {
            Stub
          },
          testM("async is interruptible") {
            Stub
          },
          testM("bracket is uninterruptible") {
            Stub
          },
          testM("bracket0 is uninterruptible") {
            Stub
          },
          testM("bracket use is interruptible") {
            Stub
          },
          testM("bracket0 use is interruptible") {
            Stub
          },
          testM("bracket release called on interrupt") {
            Stub
          },
          testM("bracket0 release called on interrupt") {
            Stub
          },
          testM("redeem + ensuring + interrupt") {
            Stub
          },
          testM("finalizer can detect interruption") {
            Stub
          },
          testM("interruption of raced") {
            Stub
          },
          testM("cancelation is guaranteed") {
            Stub
          },
          testM("interruption of unending bracket") {
            Stub
          },
          testM("recovery of error in finalizer") {
            Stub
          },
          testM("recovery of interruptible") {
            Stub
          },
          testM("sandbox of interruptible") {
            Stub
          },
          testM("run of interruptible") {
            Stub
          },
          testM("alternating interruptibility") {
            Stub
          },
          testM("interruption after defect") {
            Stub
          },
          testM("interruption after defect 2") {
            Stub
          },
          testM("cause reflects interruption") {
            Stub
          },
          testM("bracket use inherits interrupt status") {
            Stub
          },
          testM("bracket use inherits interrupt status 2") {
            Stub
          },
          testM("async can be uninterruptible") {
            Stub
          }
        ),
        suite("RTS environment")(
          testM("provide is modular") {
            val zio =
              for {
                v1 <- ZIO.environment[Int]
                v2 <- ZIO.environment[Int].provide(2)
                v3 <- ZIO.environment[Int]
              } yield (v1, v2, v3)

            assertM(zio.provide(4), equalTo((4, 2, 4)))
          },
          testM("provideManaged is modular") {
            def managed(v: Int): ZManaged[Any, Nothing, Int] =
              ZManaged.make(IO.succeed(v))(_ => IO.effectTotal(()))

            val zio =
              for {
                v1 <- ZIO.environment[Int]
                v2 <- ZIO.environment[Int].provideManaged(managed(2))
                v3 <- ZIO.environment[Int]
              } yield (v1, v2, v3)

            assertM(zio.provideManaged(managed(4)), equalTo((4, 2, 4)))
          },
          testM("effectAsync can use environment") {
            val zio = ZIO.effectAsync[Int, Nothing, Int](cb => cb(ZIO.environment[Int]))
            assertM(zio.provide(10), equalTo(10))
          }
        ),
        suite("RTS forking inheritability")(
          testM("interruption status is heritable") {
            for {
              latch <- Promise.make[Nothing, Unit]
              ref   <- Ref.make(InterruptStatus.interruptible)
              _     <- ZIO.uninterruptible((ZIO.checkInterruptible(ref.set) *> latch.succeed(())).fork *> latch.await)
              v     <- ref.get
            } yield assert(v, equalTo(InterruptStatus.uninterruptible))
          },
          testM("executor is heritable") {
            nonFlaky {
              for {
                ref  <- Ref.make(Option.empty[internal.Executor])
                exec = internal.Executor.fromExecutionContext(100)(scala.concurrent.ExecutionContext.Implicits.global)
                _ <- withLatch(
                      release => IO.descriptor.map(_.executor).flatMap(e => ref.set(Some(e)) *> release).fork.lock(exec)
                    )
                v <- ref.get
              } yield v.contains(exec)
            }.map(assert(_, isTrue))
          },
          testM("supervision is heritable") {
            nonFlaky {
              for {
                latch <- Promise.make[Nothing, Unit]
                ref   <- Ref.make(SuperviseStatus.unsupervised)
                _     <- ((ZIO.checkSupervised(ref.set) *> latch.succeed(())).fork *> latch.await).supervised
                v     <- ref.get
              } yield v == SuperviseStatus.Supervised
            }.map(assert(_, isTrue))
          },
          testM("supervision inheritance") {
            def forkAwaitStart[A](io: UIO[A], refs: Ref[List[Fiber[_, _]]]): UIO[Fiber[Nothing, A]] =
              withLatch(release => (release *> io).fork.tap(f => refs.update(f :: _)))

            nonFlaky {
              val zio =
                for {
                  ref  <- Ref.make[List[Fiber[_, _]]](Nil) // To make strong ref
                  _    <- forkAwaitStart(forkAwaitStart(forkAwaitStart(IO.succeed(()), ref), ref), ref)
                  fibs <- ZIO.children
                } yield fibs.size == 1

              zio.supervised
            }.map(assert(_, isTrue))
          }
        )
      )
    )

object RTSSpecHelper {
  val Stub = ZIO.succeed(1).map(v => assert(v, equalTo(v)))

  // Utility stuff
  val ExampleError    = new Throwable("Oh noes!")
  val InterruptCause1 = new Throwable("Oh noes 1!")
  val InterruptCause2 = new Throwable("Oh noes 2!")
  val InterruptCause3 = new Throwable("Oh noes 3!")

  val TaskExampleError: Task[Int] = IO.fail[Throwable](ExampleError)

  def asyncExampleError[A]: Task[A] =
    IO.effectAsync[Throwable, A](_(IO.fail(ExampleError)))

  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def deepMapNow(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.succeed(0))
  }

  def deepMapEffect(n: Int): UIO[Int] = {
    @tailrec
    def loop(n: Int, acc: UIO[Int]): UIO[Int] =
      if (n <= 0) acc
      else loop(n - 1, acc.map(_ + 1))

    loop(n, IO.effectTotal(0))
  }

  def deepErrorEffect(n: Int): Task[Unit] =
    if (n == 0) IO.effect(throw ExampleError)
    else IO.unit *> deepErrorEffect(n - 1)

  def deepErrorFail(n: Int): Task[Unit] =
    if (n == 0) IO.fail(ExampleError)
    else IO.unit *> deepErrorFail(n - 1)

  def fib(n: Int): BigInt =
    if (n <= 1) n
    else fib(n - 1) + fib(n - 2)

  def concurrentFib(n: Int): Task[BigInt] =
    if (n <= 1) IO.succeed[BigInt](n)
    else
      for {
        f1 <- concurrentFib(n - 1).fork
        f2 <- concurrentFib(n - 2).fork
        v1 <- f1.join
        v2 <- f2.join
      } yield v1 + v2

  def AsyncUnit[E] = IO.effectAsync[E, Unit](_(IO.unit))
}
