package zio

// import java.util.concurrent.Callable
// import java.util.concurrent.atomic.AtomicInteger

// import com.github.ghik.silencer.silent
// import zio.Cause.{ die, fail, Fail, Then }
// import zio.duration._
// import zio.internal.PlatformLive
// import zio.clock.Clock
import zio.LatchOps._
import zio.test._
import zio.test.Assertion._
import zio.test.TestUtils.nonFlaky

// import scala.annotation.tailrec
// import scala.util.{ Failure, Success }

object Helper {
  val Stub = ZIO.succeed(1).map(v => assert(v, equalTo(v)))
}

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
            Helper.Stub
          },
          testM("now must be eager") {
            Helper.Stub
          },
          testM("effectSuspend must be lazy") {
            Helper.Stub
          },
          testM("effectSuspendTotal must not catch throwable") {
            Helper.Stub
          },
          testM("effectSuspend must catch throwable") {
            Helper.Stub
          },
          testM("effectSuspendWith must catch throwable") {
            Helper.Stub
          },
          testM("suspend must be evaluatable") {
            Helper.Stub
          },
          testM("point, bind, map") {
            Helper.Stub
          },
          testM("effect, bind, map") {
            Helper.Stub
          },
          testM("effect, bind, map, redeem") {
            Helper.Stub
          },
          testM("sync effect") {
            Helper.Stub
          },
          testM("deep effects") {
            Helper.Stub
          },
          testM("flip must make error into value") {
            Helper.Stub
          },
          testM("flip must make value into error") {
            Helper.Stub
          },
          testM("flipping twice returns identical value") {
            Helper.Stub
          }
        ),
        suite("RTS failure")(
          testM("error in sync effect") {
            Helper.Stub
          },
          testM("attempt . fail") {
            Helper.Stub
          },
          testM("deep attempt sync effect error") {
            Helper.Stub
          },
          testM("deep attempt fail error") {
            Helper.Stub
          },
          testM("attempt . sandbox . terminate") {
            Helper.Stub
          },
          testM("fold . sandbox . terminate") {
            Helper.Stub
          },
          testM("catch sandbox terminate") {
            Helper.Stub
          },
          testM("uncaught fail") {
            Helper.Stub
          },
          testM("uncaught fail supervised") {
            Helper.Stub
          },
          testM("uncaught sync effect error") {
            Helper.Stub
          },
          testM("uncaught supervised sync effect error") {
            Helper.Stub
          },
          testM("deep uncaught sync effect error") {
            Helper.Stub
          },
          testM("deep uncaught fail") {
            Helper.Stub
          },
          testM("catch failing finalizers with fail") {
            Helper.Stub
          },
          testM("catch failing finalizers with terminate") {
            Helper.Stub
          },
          testM("run preserves interruption status") {
            Helper.Stub
          },
          testM("run swallows inner interruption") {
            Helper.Stub
          },
          testM("timeout a long computation") {
            Helper.Stub
          },
          testM("catchAllCause") {
            Helper.Stub
          },
          testM("exception in fromFuture does not kill fiber") {
            Helper.Stub
          }
        ),
        suite("RTS finalizers")(
          testM("fail ensuring") {
            Helper.Stub
          },
          testM("fail on error") {
            Helper.Stub
          },
          testM("finalizer errors not caught") {
            Helper.Stub
          },
          testM("finalizer errors reported") {
            Helper.Stub
          },
          testM("bracket exit is usage result") {
            Helper.Stub
          },
          testM("error in just acquisition") {
            Helper.Stub
          },
          testM("error in just release") {
            Helper.Stub
          },
          testM("error in just usage") {
            Helper.Stub
          },
          testM("rethrown caught error in acquisition") {
            Helper.Stub
          },
          testM("rethrown caught error in release") {
            Helper.Stub
          },
          testM("rethrown caught error in usage") {
            Helper.Stub
          },
          testM("test eval of async fail") {
            Helper.Stub
          },
          testM("bracket regression 1") {
            Helper.Stub
          },
          testM("interrupt waits for finalizer") {
            Helper.Stub
          }
        ),
        suite("RTS synchronous stack safety")(
          testM("deep map of now") {
            Helper.Stub
          },
          testM("deep map of sync effect") {
            Helper.Stub
          },
          testM("deep attempt") {
            Helper.Stub
          },
          testM("deep flatMap") {
            Helper.Stub
          },
          testM("deep absolve/attempt is identity") {
            Helper.Stub
          },
          testM("deep async absolve/attempt is identity") {
            Helper.Stub
          }
        ),
        suite("RTS asynchronous correctness")(
          testM("simple async must return") {
            Helper.Stub
          },
          testM("simple asyncIO must return") {
            Helper.Stub
          },
          testM("deep asyncIO doesn't block threads") {
            Helper.Stub
          },
          testM("interrupt of asyncPure register") {
            Helper.Stub
          },
          testM("sleep 0 must return") {
            Helper.Stub
          },
          testM("shallow bind of async chain") {
            Helper.Stub
          },
          testM("effectAsyncM can fail before registering") {
            Helper.Stub
          },
          testM("effectAsyncM can defect before registering") {
            Helper.Stub
          },
          testM("second callback call is ignored") {
            Helper.Stub
          }
        ),
        suite("RTS concurrency correctness")(
          testM("shallow fork/join identity") {
            Helper.Stub
          },
          testM("deep fork/join identity") {
            Helper.Stub
          },
          testM("asyncPure creation is interruptible") {
            Helper.Stub
          },
          testM("asyncInterrupt runs cancel token on interrupt") {
            Helper.Stub
          },
          testM("supervising returns fiber refs") {
            Helper.Stub
          },
          testM("supervising in unsupervised returns Nil") {
            Helper.Stub
          },
          testM("supervise fibers") {
            Helper.Stub
          },
          testM("supervise fibers in supervised") {
            Helper.Stub
          },
          testM("supervise fibers in race") {
            Helper.Stub
          },
          testM("supervise fibers in fork") {
            Helper.Stub
          },
          testM("race of fail with success") {
            Helper.Stub
          },
          testM("race of terminate with success") {
            Helper.Stub
          },
          testM("race of fail with fail") {
            Helper.Stub
          },
          testM("race of value & never") {
            Helper.Stub
          },
          testM("firstSuccessOf of values") {
            Helper.Stub
          },
          testM("firstSuccessOf of failures") {
            Helper.Stub
          },
          testM("firstSuccessOF of failures & 1 success") {
            Helper.Stub
          },
          testM("raceAttempt interrupts loser on success") {
            Helper.Stub
          },
          testM("raceAttempt interrupts loser on failure") {
            Helper.Stub
          },
          testM("par regression") {
            Helper.Stub
          },
          testM("par of now values") {
            Helper.Stub
          },
          testM("mergeAll") {
            Helper.Stub
          },
          testM("mergeAllEmpty") {
            Helper.Stub
          },
          testM("reduceAll") {
            Helper.Stub
          },
          testM("reduceAll Empty List") {
            Helper.Stub
          },
          testM("timeout of failure") {
            Helper.Stub
          },
          testM("timeout of terminate") {
            Helper.Stub
          }
        ),
        suite("RTS regression tests")(
          testM("deadlock regression 1") {
            Helper.Stub
          },
          testM("check interruption regression 1") {
            Helper.Stub
          },
          testM("max yield Ops 1") {
            Helper.Stub
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
            Helper.Stub
          },
          testM("sync forever is interruptible") {
            Helper.Stub
          },
          testM("interrupt of never") {
            Helper.Stub
          },
          testM("asyncPure is interruptible") {
            Helper.Stub
          },
          testM("async is interruptible") {
            Helper.Stub
          },
          testM("bracket is uninterruptible") {
            Helper.Stub
          },
          testM("bracket0 is uninterruptible") {
            Helper.Stub
          },
          testM("bracket use is interruptible") {
            Helper.Stub
          },
          testM("bracket0 use is interruptible") {
            Helper.Stub
          },
          testM("bracket release called on interrupt") {
            Helper.Stub
          },
          testM("bracket0 release called on interrupt") {
            Helper.Stub
          },
          testM("redeem + ensuring + interrupt") {
            Helper.Stub
          },
          testM("finalizer can detect interruption") {
            Helper.Stub
          },
          testM("interruption of raced") {
            Helper.Stub
          },
          testM("cancelation is guaranteed") {
            Helper.Stub
          },
          testM("interruption of unending bracket") {
            Helper.Stub
          },
          testM("recovery of error in finalizer") {
            Helper.Stub
          },
          testM("recovery of interruptible") {
            Helper.Stub
          },
          testM("sandbox of interruptible") {
            Helper.Stub
          },
          testM("run of interruptible") {
            Helper.Stub
          },
          testM("alternating interruptibility") {
            Helper.Stub
          },
          testM("interruption after defect") {
            Helper.Stub
          },
          testM("interruption after defect 2") {
            Helper.Stub
          },
          testM("cause reflects interruption") {
            Helper.Stub
          },
          testM("bracket use inherits interrupt status") {
            Helper.Stub
          },
          testM("bracket use inherits interrupt status 2") {
            Helper.Stub
          },
          testM("async can be uninterruptible") {
            Helper.Stub
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
