package zio

import zio.blocking.Blocking
import zio.duration._
import zio.internal.stacktracer.ZTraceElement
import zio.internal.stacktracer.ZTraceElement.{NoLocation, SourceLocation}
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

object StackTracesSpec extends DefaultRunnableSpec {
  def spec: ZSpec[Environment, Failure] = suite("StackTracesSpec")(
    testM("basic test") {
      for {
        trace <- basicTest
      } yield {
        show(trace)

        assert(trace.executionTrace.size)(equalTo(2)) &&
        assert(trace.executionTrace.count(_.prettyPrint.contains("basicTest")))(equalTo(1)) &&
        assert(trace.stackTrace.size)(equalTo(3)) &&
        assert(trace.stackTrace.count(_.prettyPrint.contains("basicTest")))(equalTo(1))
      }
    },
    testM("foreachTrace") {
      for {
        trace <- foreachTest
      } yield {
        show(trace)

        assert(trace.stackTrace.size)(equalTo(3)) &&
        assert(trace.stackTrace.exists(_.prettyPrint.contains("foreachTest")))(isTrue) &&
        assert(trace.executionTrace.exists(_.prettyPrint.contains("foreachTest")))(isTrue) &&
        assert(trace.executionTrace.exists(_.prettyPrint.contains("foreach_")))(isTrue) &&
        assert(trace.executionTrace.exists(_.prettyPrint.contains("effectTotal")))(isTrue)
      }
    },
    testM("foreach fail") {
      for {
        trace <- foreachFail
      } yield {
        val (trace1, trace2) = trace

        assert(trace1.stackTrace.exists(_.prettyPrint.contains("foreach_")))(isTrue) &&
        assert(trace1.stackTrace.exists(_.prettyPrint.contains("foreachFail")))(isTrue) &&
        assert(trace1.executionTrace.exists(_.prettyPrint.contains("foreach_")))(isTrue) &&
        assert(trace1.executionTrace.exists(_.prettyPrint.contains("foreachFail")))(isTrue) &&
        assert(trace2.stackTrace.size)(equalTo(3)) &&
        assert(trace2.stackTrace.exists(_.prettyPrint.contains("foreachFail")))(isTrue) &&
        assert(trace2.executionTrace.exists(_.prettyPrint.contains("foreach_")))(isTrue) &&
        assert(trace2.executionTrace.exists(_.prettyPrint.contains("foreachFail")))(isTrue)

      }
    },
    testM("foreachPar fail") {
      val io = for {
        trace <- TestClock.setTime(1.second) *> foreachParFail
      } yield trace

      io causeMust { cause =>
        assert(cause.traces.head.stackTrace.size)(equalTo(8)) &&
        assert(cause.traces.head.stackTrace.exists {
          (_: ZTraceElement) match {
            case s: SourceLocation => s.method contains "foreachParFail"
            case _                 => false
          }
        })(isTrue)
      }
    },
    testM("foreachParN fail") {
      val io = for {
        _     <- TestClock.setTime(1.second)
        trace <- foreachParNFail
      } yield trace

      io causeMust { cause =>
        assert(cause.traces.head.stackTrace.size)(equalTo(4)) &&
        assert(cause.traces.head.stackTrace.exists {
          (_: ZTraceElement) match {
            case s: SourceLocation => s.method contains "foreachParNFail"
            case _                 => false
          }
        })(isTrue)
      }
    },
    testM("left-associative fold") {
      val inFoldExecutions               = 10
      val inFoldAssociationsPerIteration = 2
      val nonFoldExecution               = 5
      val expectedExecutionTrace         = inFoldExecutions * inFoldAssociationsPerIteration + nonFoldExecution

      for {
        trace <- leftAssociativeFold(inFoldExecutions)
      } yield {
        show(trace)

        assert(trace.stackTrace.size)(equalTo(2)) &&
        assert(trace.executionTrace.count(x => x.prettyPrint.contains("leftAssociativeFold")))(
          equalTo((expectedExecutionTrace))
        )
      }
    },
    testM("nested left binds") {
      for {
        trace <- nestedLeftBinds.io
      } yield {
        val (trace1: ZTrace, trace2: ZTrace) = trace
        show(trace1)
        show(trace2)

        assert(trace1.executionTrace.size)(equalTo(1)) &&
        assert(trace1.stackTrace.size)(equalTo(6)) &&
        assert(trace1.stackTrace.exists(_.prettyPrint.contains("method2")))(isTrue) &&
        assert(trace1.stackTrace.exists(_.prettyPrint.contains("method1")))(isTrue) &&
        assert(trace1.stackTrace.exists(_.prettyPrint.contains("io")))(isTrue) &&
        assert(trace2.stackTrace.size)(equalTo(3)) &&
        assert(trace2.stackTrace.exists(_.prettyPrint.contains("tuple")))(isTrue) &&
        assert(trace2.executionTrace.exists(_.prettyPrint.contains("method2")))(isTrue) &&
        assert(trace2.executionTrace.exists(_.prettyPrint.contains("method1")))(isTrue) &&
        assert(trace2.executionTrace.exists(_.prettyPrint.contains("io")))(isTrue)
      }
    },
    testM("fiber ancestry") {
      val fiber = for {
        trace <- fiberAncestry
      } yield trace

      fiber causeMust { cause =>
        assert(cause.traces)(isNonEmpty) &&
        assert(cause.traces.head.parentTrace.isEmpty)(isFalse) &&
        assert(cause.traces.head.parentTrace.get.parentTrace.isEmpty)(isFalse) &&
        assert(cause.traces.head.parentTrace.get.parentTrace.get.parentTrace.isEmpty)(isFalse)
      }
    },
    testM("fiber ancestry example with uploads") {
      fiberAncestryUploadExample
        .uploadUsers(List(new fiberAncestryUploadExample.User)) causeMust { cause =>
        assert(cause.traces.head.stackTrace.size)(equalTo(7)) &&
        assert(cause.traces.head.stackTrace(4).prettyPrint.contains("uploadUsers"))(isTrue) &&
        assert(cause.traces(1).stackTrace.size)(equalTo(6)) &&
        assert(cause.traces(1).executionTrace.size)(equalTo(6)) &&
        assert(cause.traces(1).executionTrace.head.prettyPrint.contains("uploadTo"))(isTrue) &&
        assert(cause.traces(1).parentTrace.isEmpty)(isFalse) &&
        assert(
          cause
            .traces(1)
            .parentTrace
            .get
            .stackTrace
            .exists(_.prettyPrint.contains("uploadUsers"))
        )(isTrue)
      }
    },
    testM("fiber ancestry has a limited size") {
      fiberAncestryIsLimitedFixture.recursiveFork(10000) causeMust { cause =>
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.parents.size)(equalTo(10)) &&
        assert(cause.traces.head.executionTrace.exists(_.prettyPrint.contains("recursiveFork")))(isTrue) &&
        assert(cause.traces.head.parents.head.stackTrace.exists(_.prettyPrint.contains("recursiveFork")))(isTrue)
      }
    },
    testM("blocking trace") {
      val io: RIO[Blocking, Unit] = for {
        trace <- blockingTrace
      } yield trace

      io causeMust { cause =>
        val trace = cause.traces.head

        assert(trace.stackTrace.exists(_.prettyPrint.contains("blockingTrace")))(isTrue) &&
        assert(trace.executionTrace.exists(_.prettyPrint.contains("blockingTrace")))(isTrue)
      }
    },
    testM("tracing regions") {
      val io = for {
        trace <- tracingRegions
      } yield trace

      io causeMust { cause =>
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.executionTrace.exists(_.prettyPrint.contains("traceThis")))(isTrue) &&
        assert(cause.traces.head.executionTrace.exists {
          case SourceLocation(_, _, m, _) => m == "tracingRegions"
          case NoLocation(_)              => true
        })(isFalse) &&
        assert(cause.traces.head.stackTrace.size)(equalTo(3))
      }
    },
    testM("tracing region is inherited on fork") {
      val io = for {
        trace <- tracingRegionsInheritance
      } yield trace

      io causeMust { cause =>
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.executionTrace.isEmpty)(isTrue) &&
        assert(cause.traces.head.stackTrace.isEmpty)(isTrue) &&
        assert(cause.traces.head.parentTrace.isEmpty)(isTrue)
      }
    },
    testM("execution trace example with conditional") {
      val io = for {
        trace <- executionTraceConditionalExample
      } yield trace

      io causeMust { cause =>
        val trace = cause.traces.head

        assert(trace.executionTrace.exists(_.prettyPrint.contains("doSideWork")))(isTrue) &&
        assert(trace.executionTrace.exists(_.prettyPrint.contains("doMainWork")))(isTrue) &&
        assert(trace.stackTrace.head.prettyPrint.contains("doWork"))(isTrue)
      }
    },
    testM("mapError fully preserves previous stack trace") {
      val io = for {
        trace <- mapErrorPreservesTrace
      } yield trace

      io causeMust { cause =>
        // mapError does not change the trace in any way from its state during `fail()`
        // as a consequence, `executionTrace` is not updated with finalizer info, etc...
        // but overall it's a good thing since you're not losing traces at the border between your domain errors & Throwable
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.executionTrace.size)(equalTo(2)) &&
        assert(cause.traces.head.executionTrace.head.prettyPrint.contains("fail"))(isTrue) &&
        assert(cause.traces.head.stackTrace.size)(equalTo(6)) &&
        assert(cause.traces.head.stackTrace.head.prettyPrint.contains("succ"))(isTrue) &&
        assert(cause.traces.head.stackTrace(1).prettyPrint.contains("mapError"))(isTrue) &&
        assert(cause.traces.head.stackTrace(2).prettyPrint.contains("mapErrorPreservesTrace"))(isTrue)
      }
    },
    testM("catchSome with optimized effect path") {
      val io = for {
        trace <- catchSomeWithOptimizedEffect
      } yield trace

      io causeMust { cause =>
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.executionTrace.size)(equalTo(2)) &&
        assert(cause.traces.head.executionTrace.exists(_.prettyPrint.contains("fail")))(isTrue) &&
        assert(cause.traces.head.stackTrace.size)(equalTo(6)) &&
        assert(cause.traces.head.stackTrace.head.prettyPrint.contains("badMethod"))(isTrue) &&
        assert(cause.traces.head.stackTrace(1).prettyPrint.contains("apply"))(isTrue) && // PartialFunction.apply
        assert(cause.traces.head.stackTrace(2).prettyPrint.contains("catchSomeWithOptimizedEffect"))(isTrue)
      }
    },
    testM("catchAll with optimized effect path") {
      val io = for {
        trace <- catchAllWithOptimizedEffect
      } yield trace

      io causeMust { cause =>
        // after we refail and lose the trace, the only continuation we have left is the map from yield
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.executionTrace.size)(equalTo(3)) &&
        assert(cause.traces.head.executionTrace.head.prettyPrint.contains("refailAndLoseTrace"))(isTrue) &&
        assert(cause.traces.head.executionTrace.exists(_.prettyPrint.contains("fail")))(isTrue) &&
        assert(cause.traces.head.stackTrace.size)(equalTo(4)) &&
        assert(cause.traces.head.stackTrace.head.prettyPrint.contains("catchAllWithOptimizedEffect"))(isTrue)
      }
    },
    testM("foldM with optimized effect path") {
      for {
        trace <- foldMWithOptimizedEffect
      } yield {
        show(trace)

        assert(trace.stackTrace.size)(equalTo(3)) &&
        assert(trace.stackTrace.exists(_.prettyPrint.contains("foldMWithOptimizedEffect")))(isTrue) &&
        assert(trace.executionTrace.size)(equalTo(3)) &&
        assert(trace.executionTrace.head.prettyPrint.contains("mkTrace"))(isTrue) &&
        assert(trace.executionTrace.exists(_.prettyPrint.contains("fail")))(isTrue)

      }
    },
    testM("single effect for-comprehension") {
      singleTaskForCompFixture.selectHumans causeMust { cause =>
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.stackTrace.size)(equalTo(3)) &&
        assert(cause.traces.head.stackTrace.head.prettyPrint.contains("selectHumans"))(isTrue)
      }
    },
    testM("single effectTotal for-comprehension") {
      singleUIOForCompFixture.selectHumans causeMust { cause =>
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.stackTrace.size)(equalTo(3)) &&
        assert(cause.traces.head.stackTrace.exists(_.prettyPrint.contains("selectHumans")))(isTrue)
      }
    },
    testM("single suspendWith for-comprehension") {
      singleEffectTotalWithForCompFixture.selectHumans causeMust { cause =>
        assert(cause.traces.size)(equalTo(1)) &&
        assert(cause.traces.head.stackTrace.size)(equalTo(3)) &&
        assert(cause.traces.head.stackTrace.exists(_.prettyPrint.contains("selectHumans")))(isTrue)
      }
    },
    testM("basic option test") {
      for {
        value <- ZIO.getOrFailUnit(Some("foo"))
      } yield {
        assert(value)(equalTo("foo"))
      }
    },
    testM("side effect unit in option test") {
      for {
        value <- ZIO.getOrFailUnit(None).catchAll { unit =>
                   if (unit.isInstanceOf[Unit]) {
                     ZIO.succeed("Controlling unit side-effect")
                   } else {
                     ZIO.fail("wrong side-effect type ")
                   }
                 }
      } yield {
        assert(value)(equalTo("Controlling unit side-effect"))
      }
    }
  )

  // set to true to print traces
  private val debug = false

  def show(trace: ZTrace): Unit = if (debug) println(trace.prettyPrint)

  def show(cause: Cause[Any]): Unit = if (debug) println(cause.prettyPrint)

  def basicTest: UIO[ZTrace] =
    for {
      _     <- ZIO.unit
      trace <- ZIO.trace
    } yield trace

  def foreachTest: UIO[ZTrace] = {
    import foreachTraceFixture._
    for {
      _     <- effectTotal
      _     <- ZIO.foreach_(1 to 10)(_ => ZIO.unit *> ZIO.trace)
      trace <- ZIO.trace
    } yield trace
  }

  object foreachTraceFixture {
    def effectTotal: UIO[Unit] = ZIO.effectTotal(())
  }

  def foreachFail: ZIO[Any, Throwable, (ZTrace, ZTrace)] =
    for {
      t1 <- ZIO
              .foreach_(1 to 10) { i =>
                if (i == 7)
                  ZIO.unit *> ZIO.fail("Dummy error!")
                else
                  ZIO.unit *> ZIO.trace
              }
              .foldCauseM(e => IO(e.traces.head), _ => ZIO.dieMessage("can't be!"))
      t2 <- ZIO.trace
    } yield (t1, t2)

  def foreachParFail: UIO[Unit] =
    for {
      _ <- ZIO.foreachPar(1 to 10)(i => (if (i >= 7) UIO(i / 0) else UIO(i / 10)))
    } yield ()

  def foreachParNFail: ZIO[Any, Nothing, Unit] =
    for {
      _ <- ZIO.foreachParN(4)(1 to 10)(i => (if (i >= 7) UIO(i / 0) else UIO(i / 10)))
    } yield ()

  def leftAssociativeFold(n: Int): UIO[ZTrace] =
    (1 to n)
      .foldLeft(ZIO.unit *> ZIO.unit) { (acc, _) =>
        acc *> UIO(())
      } *>
      ZIO.unit *>
      ZIO.unit *>
      ZIO.unit *>
      ZIO.trace

  object nestedLeftBinds {
    def method2: ZIO[Any, Nothing, ZTrace] =
      for {
        trace <- ZIO.trace
        _     <- ZIO.unit
        _     <- ZIO.unit
        _     <- ZIO.unit
        _     <- UIO(())
      } yield trace

    def method1: ZIO[Any, Nothing, ZTrace] =
      for {
        t <- method2
        _ <- ZIO.unit
        _ <- ZIO.unit
        _ <- ZIO.unit
      } yield t

    def tuple(t: ZTrace): ZTrace => (ZTrace, ZTrace) = t2 => (t, t2)

    val io: ZIO[Any, Nothing, (ZTrace, ZTrace)] =
      (for {
        t <- method1
        _ <- ZIO.unit
        _ <- ZIO.unit
        _ <- ZIO.unit
      } yield t)
        .flatMap(t =>
          IO.trace
            .map(tuple(t))
        )
  }

  def fiberAncestry: ZIO[Any, Nothing, Unit] = {
    def fiber1 =
      for {
        _  <- ZIO.unit
        _  <- ZIO.unit
        f2 <- fiber2.fork
        _  <- ZIO.unit
        _  <- f2.join
      } yield ()

    def fiber2 =
      for {
        _ <- UIO {
               throw new Exception()
             }
      } yield ()

    for {
      f1 <- fiber1.fork
      _  <- f1.join
    } yield ()
  }

  object fiberAncestryUploadExample {

    sealed trait JSON

    final class User extends JSON

    final class URL

    def userDestination: URL = new URL

    def uploadUsers(users: List[User]): Task[Unit] =
      for {
        _ <- IO.foreachPar(users)(uploadTo(userDestination))
      } yield ()

    def uploadTo(destination: URL)(json: JSON): Task[Unit] = {
      val _ = (destination, json)
      Task(throw new Exception("Expired credentials"))
    }
  }

  object fiberAncestryIsLimitedFixture {
    def recursiveFork(i: Int): UIO[Unit] =
      i match {
        case 0 =>
          UIO(throw new Exception("oops!"))
        case _ =>
          UIO.effectSuspendTotal(recursiveFork(i - 1)).fork.flatMap(_.join)
      }
  }

  def blockingTrace: ZIO[Blocking, Throwable, Unit] =
    for {
      _ <- blocking.effectBlockingInterrupt {
             throw new Exception()
           }
    } yield ()

  def tracingRegions: ZIO[Any, java.io.Serializable, Unit] = {
    import tracingRegionsFixture._

    (for {
      _ <- ZIO.unit
      _ <- ZIO.unit
      _ <- ZIO.effect(traceThis()).traced.traced.traced
      _ <- ZIO.unit
      _ <- ZIO.unit
      _ <- ZIO.fail("end")
    } yield ()).untraced
  }

  object tracingRegionsFixture {
    val traceThis: () => String = () => "trace this!"
  }

  def tracingRegionsInheritance: ZIO[Any, Nothing, Unit] =
    for {
      _ <- ZIO.unit
      _ <- ZIO.unit
      untraceableFiber <- (ZIO.unit *> (ZIO.unit *> ZIO.unit *> ZIO.dieMessage("error!") *> ZIO.checkTraced(
                            ZIO.succeed(_)
                          )).fork).untraced
      tracingStatus <- untraceableFiber.join
      _ <- ZIO.when(tracingStatus.isTraced) {
             ZIO.dieMessage("Expected disabled tracing")
           }
    } yield ()

  def executionTraceConditionalExample: ZIO[Any, Throwable, Unit] = {
    import executionTraceConditionalExampleFixture._

    doWork(true)
  }

  object executionTraceConditionalExampleFixture {
    def doWork(condition: Boolean): ZIO[Any, Throwable, Unit] =
      for {
        _ <- IO.when(condition)(doSideWork())
        _ <- doMainWork()
      } yield ()

    def doSideWork(): Task[Unit] = Task(())

    def doMainWork(): Task[Nothing] = Task(throw new Exception("Worker failed!"))
  }

  def mapErrorPreservesTrace: ZIO[Any, Unit, ZTrace] = {
    import mapErrorPreservesTraceFixture._

    for {
      t <- Task(fail())
             .flatMap(succ)
             .mapError(mapError)
    } yield t
  }

  object mapErrorPreservesTraceFixture {
    val succ: ZTrace => UIO[ZTrace] = ZIO.succeed(_: ZTrace)
    val fail: () => Nothing         = () => throw new Exception("error!")
    val mapError: Any => Unit       = (_: Any) => ()
  }

  def catchSomeWithOptimizedEffect: ZIO[Any, java.io.Serializable, ZTrace] = {
    import catchSomeWithOptimizedEffectFixture._

    for {
      t <- Task(fail())
             .flatMap(badMethod)
             .catchSome { case _: ArithmeticException =>
               ZIO.fail("impossible match!")
             }
    } yield t
  }

  object catchSomeWithOptimizedEffectFixture {
    val fail: () => Nothing              = () => throw new Exception("error!")
    val badMethod: ZTrace => UIO[ZTrace] = ZIO.succeed(_: ZTrace)
  }

  def catchAllWithOptimizedEffect: ZIO[Any, String, ZTrace] = {
    import catchAllWithOptimizedEffectFixture._

    for {
      t <- Task(fail())
             .flatMap(succ)
             .catchAll(refailAndLoseTrace)
    } yield t
  }

  object catchAllWithOptimizedEffectFixture {
    val succ: ZTrace => UIO[ZTrace]                    = ZIO.succeed(_: ZTrace)
    val fail: () => Nothing                            = () => throw new Exception("error!")
    val refailAndLoseTrace: Any => IO[String, Nothing] = (_: Any) => ZIO.fail("bad!")
  }

  def foldMWithOptimizedEffect: ZIO[Any, Nothing, ZTrace] = {
    import foldMWithOptimizedEffectFixture._

    for {
      t <- Task(fail())
             .flatMap(badMethod1)
             .foldM(mkTrace, badMethod2)
    } yield t
  }

  object foldMWithOptimizedEffectFixture {
    val mkTrace: Any => UIO[ZTrace]       = (_: Any) => ZIO.trace
    val fail: () => Nothing               = () => throw new Exception("error!")
    val badMethod1: ZTrace => UIO[ZTrace] = ZIO.succeed(_: ZTrace)
    val badMethod2: ZTrace => UIO[ZTrace] = ZIO.succeed(_: ZTrace)
  }

  object singleTaskForCompFixture {
    def asyncDbCall(): Task[Unit] =
      Task(throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  object singleUIOForCompFixture {
    def asyncDbCall(): Task[Unit] =
      UIO(throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  object singleEffectTotalWithForCompFixture {
    def asyncDbCall(): Task[Unit] =
      Task.effectSuspendTotal(throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  implicit final class CauseMust[R](io: ZIO[R with TestClock, Any, Any]) {
    def causeMust(check: Cause[Any] => TestResult): URIO[R with TestClock, TestResult] =
      io.foldCause[TestResult](
        cause => {
          show(cause)
          check(cause)
        },
        _ => assert(false)(isTrue)
      )
  }
}
