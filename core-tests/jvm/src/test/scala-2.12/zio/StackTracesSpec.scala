package zio

import org.specs2.execute.Result
import org.specs2.matcher.{ Expectable, Matcher }
import org.specs2.mutable
import scala.concurrent.duration.{ Duration => SDuration }
import zio.duration._
import zio.internal.stacktracer.ZTraceElement
import zio.internal.stacktracer.ZTraceElement.SourceLocation
import java.util.concurrent.TimeUnit

import zio.test.Assertion._
import zio.test._

object StackTracesSpecUtil {
  // set to true to print traces
  private val debug = false

  def show(trace: ZTrace): Unit = if (debug) println(trace.prettyPrint)

  def show(cause: Cause[Any]): Unit = if (debug) println(cause.prettyPrint)

  def basicTest: ZIO[Any, Nothing, ZTrace] =
    for {
      _     <- ZIO.unit
      trace <- ZIO.trace
    } yield trace

  def foreachTest: ZIO[Any, Nothing, ZTrace] = {
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
}

object StackTracesSpec_ToZioMigration
    extends ZIOBaseSpec(
      suite("StackTracesSpec")(
        testM("basic test") {
          for {
            trace <- StackTracesSpecUtil.basicTest
          } yield {
            StackTracesSpecUtil.show(trace)

            assert(trace.executionTrace.size, equalTo(4)) &&
            assert(trace.executionTrace.count(_.prettyPrint.contains("basicTest")), equalTo(1)) &&
            assert(trace.stackTrace.size, equalTo(6)) &&
            assert(trace.stackTrace.count(_.prettyPrint.contains("basicTest")), equalTo(1))
          }
        },
        testM("foreachTrace") {
          for {
            trace <- StackTracesSpecUtil.foreachTest
          } yield {
            StackTracesSpecUtil.show(trace)

            assert(trace.stackTrace.size, equalTo(6)) &&
            assert(trace.stackTrace.exists(_.prettyPrint.contains("foreachTest")), isTrue) &&
            assert(trace.executionTrace.exists(_.prettyPrint.contains("foreachTest")), isTrue) &&
            assert(trace.executionTrace.exists(_.prettyPrint.contains("foreach_")), isTrue) &&
            assert(trace.executionTrace.exists(_.prettyPrint.contains("effectTotal")), isTrue)
          }
        },
        testM("foreach fail") {
          for {
            trace <- StackTracesSpecUtil.foreachFail
          } yield {
            val (trace1, trace2) = trace

            assert(trace1.stackTrace.exists(_.prettyPrint.contains("foreach_")), isTrue) &&
            assert(trace1.stackTrace.exists(_.prettyPrint.contains("foreachFail")), isTrue) &&
            assert(trace1.executionTrace.exists(_.prettyPrint.contains("foreach_")), isTrue) &&
            assert(trace1.executionTrace.exists(_.prettyPrint.contains("foreachFail")), isTrue) &&
            assert(trace2.stackTrace.size, equalTo(6)) &&
            assert(trace2.stackTrace.exists(_.prettyPrint.contains("foreachFail")), isTrue) &&
            assert(trace2.executionTrace.exists(_.prettyPrint.contains("foreach_")), isTrue) &&
            assert(trace2.executionTrace.exists(_.prettyPrint.contains("foreachFail")), isTrue)

          }
        }
      )
    )

class StackTracesSpec_AwayFromSpecs2Migration(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with mutable.SpecificationLike {
  override val DefaultTimeout: SDuration = SDuration(60, TimeUnit.SECONDS)

  // Using mutable Spec here to easily run individual tests from Intellij to inspect result traces

  // set to true to print traces
  private val debug = false

  "foreachPar fail" >> foreachParFail
  "foreachParN fail" >> foreachParNFail

  "left-associative fold" >> leftAssociativeFold
  "nested left binds" >> nestedLeftBinds

  "fiber ancestry" >> fiberAncestry
  "fiber ancestry example with uploads" >> fiberAncestryUploadExample
  "fiber ancestry has a limited size" >> fiberAncestryIsLimited

  "blocking trace" >> blockingTrace

  "tracing regions" >> tracingRegions
  "tracing region is inherited on fork" >> tracingRegionsInheritance

  "execution trace example with conditional" >> executionTraceConditionalExample

  "mapError fully preserves previous stack trace" >> mapErrorPreservesTrace

  "catchSome with optimized effect path" >> catchSomeWithOptimizedEffect
  "catchAll with optimized effect path" >> catchAllWithOptimizedEffect
  "foldM with optimized effect path" >> foldMWithOptimizedEffect

  "single effect for-comprehension" >> singleEffectForComp
  "single effectTotal for-comprehension" >> singleEffectTotalForComp
  "single suspendWith for-comprehension" >> singleSuspendWithForComp

  private def show(trace: ZTrace): Unit = if (debug) println(trace.prettyPrint)

  private def show(cause: Cause[Any]): Unit = if (debug) println(cause.prettyPrint)

  private def mentionsMethod(method: String, trace: ZTraceElement): Boolean =
    trace match {
      case s: SourceLocation => s.method contains method
      case _                 => false
    }

  private def mentionMethod(method: String)(implicit dummy: DummyImplicit): Matcher[ZTraceElement] =
    new Matcher[ZTraceElement] {
      def apply[S <: ZTraceElement](expectable: Expectable[S]) =
        result(
          mentionsMethod(method, expectable.value),
          expectable.description + s" mentions method `$method`",
          expectable.description + s" does not mention method `$method`",
          expectable
        )
    }

  private def mentionMethod(method: String): Matcher[List[ZTraceElement]] =
    new Matcher[List[ZTraceElement]] {
      def apply[S <: List[ZTraceElement]](expectable: Expectable[S]) =
        result(
          expectable.value.exists(mentionsMethod(method, _)),
          expectable.description + s" mentions method `$method`",
          expectable.description + s" does not mention method `$method`",
          expectable
        )
    }

  private def mentionedMethod(method: String): Matcher[List[ZTraceElement]] = mentionMethod(method)

  private implicit final class CauseMust[R >: ZEnv](io: ZIO[R, Any, Any]) {
    def causeMust(check: Cause[Any] => Result): Result =
      unsafeRunSync(io).fold[Result](
        cause => {
          show(cause)
          check(cause)
        },
        _ => failure
      )
  }

  def foreachParFail = {
    val io = for {
      _ <- ZIO.foreachPar(1 to 10) { i =>
            ZIO.sleep(1.second) *> (if (i >= 7) UIO(i / 0) else UIO(i / 10))
          }
    } yield ()

    io causeMust {
      _.traces.head.stackTrace must have size 2 and contain {
        (_: ZTraceElement) match {
          case s: SourceLocation => s.method contains "foreachParFail"
          case _                 => false
        }
      }
    }
  }

  def foreachParNFail = {
    val io = for {
      _ <- ZIO.foreachParN(4)(1 to 10) { i =>
            ZIO.sleep(1.second) *> (if (i >= 7) UIO(i / 0) else UIO(i / 10))
          }
    } yield ()

    io causeMust {
      _.traces.head.stackTrace must have size 2 and contain {
        (_: ZTraceElement) match {
          case s: SourceLocation => s.method contains "foreachParNFail"
          case _                 => false
        }
      }
    }
  }

  def leftAssociativeFold = {
    val io: ZIO[Any, Nothing, ZTrace] =
      (1 to 10)
        .foldLeft(ZIO.unit *> ZIO.unit) { (acc, _) =>
          acc *> UIO(())
        } *>
        ZIO.unit *>
        ZIO.unit *>
        ZIO.unit *>
        ZIO.trace

    unsafeRun(io) must { trace: ZTrace =>
      show(trace)

      (trace.stackTrace must have size 1) and
        (trace.executionTrace must forall[ZTraceElement](mentionMethod("leftAssociativeFold")))
    }
  }

  def nestedLeftBinds = {
    import nestedLeftBindsFixture._

    unsafeRun(io) must { r: (ZTrace, ZTrace) =>
      val (trace1, trace2) = r
      show(trace1)
      show(trace2)

      // due to the way tracing works, we've accumulated quite a stack
      // but we haven't yet passed a single flatMap while going through
      // left binds, so our exec trace is empty.
      (trace1.executionTrace must beEmpty) and
        (trace1.stackTrace must have size 5) and
        (trace1.stackTrace must mentionMethod("method2")) and
        (trace1.stackTrace must mentionMethod("method1")) and
        (trace1.stackTrace must mentionMethod("io")) and
        (trace2.stackTrace must have size 2) and
        (trace2.stackTrace must mentionMethod("tuple")) and
        (trace2.executionTrace must mentionMethod("method2")) and
        (trace2.executionTrace must mentionMethod("method1")) and
        (trace2.executionTrace must mentionMethod("io"))
    }
  }

  object nestedLeftBindsFixture {
    def method2 =
      for {
        trace <- ZIO.trace
        _     <- ZIO.unit
        _     <- ZIO.unit
        _     <- ZIO.unit
        _     <- UIO(())
      } yield trace

    def method1 =
      for {
        t <- method2
        _ <- ZIO.unit
        _ <- ZIO.unit
        _ <- ZIO.unit
      } yield t

    def tuple(t: ZTrace): ZTrace => (ZTrace, ZTrace) = t2 => (t, t2)

    val io =
      (for {
        t <- method1
        _ <- ZIO.unit
        _ <- ZIO.unit
        _ <- ZIO.unit
      } yield t)
        .flatMap(
          t =>
            IO.trace
              .map(tuple(t))
        )
  }

  def fiberAncestry = {

    def fiber0 =
      for {
        f1 <- fiber1.fork
        _  <- f1.join
      } yield ()

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

    fiber0 causeMust { cause =>
      (cause.traces must not be empty) and
        (cause.traces.head.parentTrace must not be empty) and
        (cause.traces.head.parentTrace.get.parentTrace must not be empty) and
        (cause.traces.head.parentTrace.get.parentTrace.get.parentTrace must beEmpty)
    }
  }

  def fiberAncestryUploadExample = {
    import fiberAncestryUploadExampleFixture._

    uploadUsers(List(new User)) causeMust { cause =>
      (cause.traces.head.stackTrace must have size 2) and
        (cause.traces.head.stackTrace.head must mentionMethod("uploadUsers")) and
        (cause.traces(1).stackTrace must have size 0) and
        (cause.traces(1).executionTrace must have size 1) and
        (cause.traces(1).executionTrace.head must mentionMethod("uploadTo")) and
        (cause.traces(1).parentTrace must not be empty) and
        (cause.traces(1).parentTrace.get.parentTrace.get.stackTrace must mentionMethod("uploadUsers"))
    }
  }

  object fiberAncestryUploadExampleFixture {

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

  def fiberAncestryIsLimited = {
    import fiberAncestryIsLimitedFixture._

    recursiveFork(10000) causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.parents must have size 10) and
        (cause.traces.head.executionTrace must mentionMethod("recursiveFork")) and
        (cause.traces.head.parents.head.stackTrace must mentionMethod("recursiveFork"))
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

  def blockingTrace = {
    val io = for {
      _ <- blocking.effectBlocking {
            throw new Exception()
          }
    } yield ()

    io causeMust { cause =>
      val trace = cause.traces.head

      // the bottom items on exec trace and stack trace refer to this line
      (trace.stackTrace must mentionMethod("blockingTrace")) and
        (trace.executionTrace.last must mentionMethod("blockingTrace"))
    }

  }

  def tracingRegions = {
    import tracingRegionsFixture._

    val io = (for {
      _ <- ZIO.unit
      _ <- ZIO.unit
      _ <- ZIO.effect(traceThis()).traced.traced.traced
      _ <- ZIO.unit
      _ <- ZIO.unit
      _ <- ZIO.fail("end")
    } yield ()).untraced

    io causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.executionTrace must mentionMethod("traceThis")) and
        (cause.traces.head.executionTrace must not have mentionedMethod("tracingRegions")) and
        (cause.traces.head.stackTrace must have size 1)
    }
  }

  object tracingRegionsFixture {
    val traceThis: () => String = () => "trace this!"
  }

  def tracingRegionsInheritance = {
    val io: ZIO[Any, Nothing, Unit] = for {
      _ <- ZIO.unit
      _ <- ZIO.unit
      untraceableFiber <- (ZIO.unit *> (ZIO.unit *> ZIO.unit *> ZIO.dieMessage("error!") *> ZIO.checkTraced(
                           ZIO.succeed
                         )).fork).untraced
      tracingStatus <- untraceableFiber.join
      _ <- ZIO.when(tracingStatus.isTraced) {
            ZIO.dieMessage("Expected disabled tracing")
          }
    } yield ()

    io causeMust { cause =>
      (cause.traces must have size 1) and
        cause.traces.head.executionTrace.isEmpty and
        cause.traces.head.stackTrace.isEmpty and
        cause.traces.head.parentTrace.isEmpty
    }
  }

  def executionTraceConditionalExample = {
    import executionTraceConditionalExampleFixture._

    val io = doWork(true)

    io causeMust { cause =>
      val trace = cause.traces.head

      (trace.executionTrace.last must mentionMethod("doSideWork")) and
        (trace.executionTrace must mentionMethod("doMainWork")) and
        (trace.stackTrace.head must mentionMethod("doWork"))
    }
  }

  object executionTraceConditionalExampleFixture {
    def doWork(condition: Boolean) =
      for {
        _ <- IO.when(condition)(doSideWork)
        _ <- doMainWork
      } yield ()

    def doSideWork() = Task(())

    def doMainWork() = Task(throw new Exception("Worker failed!"))
  }

  def singleEffectForComp = {
    import singleTaskForCompFixture._

    selectHumans causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.stackTrace must have size 2) and
        (cause.traces.head.stackTrace.head must mentionMethod("selectHumans"))
    }
  }

  object singleTaskForCompFixture {
    def asyncDbCall(): Task[Unit] =
      Task(throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  def singleEffectTotalForComp = {
    import singleUIOForCompFixture._

    selectHumans causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.stackTrace must have size 2) and
        (cause.traces.head.stackTrace must mentionMethod("selectHumans"))
    }
  }

  object singleUIOForCompFixture {
    def asyncDbCall(): Task[Unit] =
      UIO(throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  def singleSuspendWithForComp = {
    import singleEffectTotalWithForCompFixture._

    selectHumans causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.stackTrace must have size 2) and
        (cause.traces.head.stackTrace must mentionMethod("selectHumans"))
    }
  }

  object singleEffectTotalWithForCompFixture {
    def asyncDbCall(): Task[Unit] =
      Task.effectSuspendTotalWith(_ => throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  def catchSomeWithOptimizedEffect = {
    import catchSomeWithOptimizedEffectFixture._

    val io = for {
      t <- Task(fail())
            .flatMap(badMethod)
            .catchSome {
              case _: ArithmeticException => ZIO.fail("impossible match!")
            }
    } yield t

    io causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.executionTrace must have size 1) and
        (cause.traces.head.executionTrace must mentionMethod("fail")) and
        (cause.traces.head.stackTrace must have size 4) and
        (cause.traces.head.stackTrace.head must mentionMethod("badMethod")) and
        (cause.traces.head.stackTrace(1) must mentionMethod("apply")) and // PartialFunction.apply
        (cause.traces.head.stackTrace(2) must mentionMethod("catchSomeWithOptimizedEffect"))
    }
  }

  object catchSomeWithOptimizedEffectFixture {
    val fail      = () => throw new Exception("error!")
    val badMethod = ZIO.succeed(_: ZTrace)
  }

  def catchAllWithOptimizedEffect = {
    import catchAllWithOptimizedEffectFixture._

    val io = for {
      t <- Task(fail())
            .flatMap(succ)
            .catchAll(refailAndLoseTrace)
    } yield t

    io causeMust { cause =>
      // after we refail and lose the trace, the only continuation we have left is the map from yield
      (cause.traces must have size 1) and
        (cause.traces.head.executionTrace must have size 2) and
        (cause.traces.head.executionTrace.head must mentionMethod("refailAndLoseTrace")) and
        (cause.traces.head.executionTrace.last must mentionMethod("fail")) and
        (cause.traces.head.stackTrace must have size 2) and
        (cause.traces.head.stackTrace.head must mentionMethod("catchAllWithOptimizedEffect"))
    }
  }

  object catchAllWithOptimizedEffectFixture {
    val succ               = ZIO.succeed(_: ZTrace)
    val fail               = () => throw new Exception("error!")
    val refailAndLoseTrace = (_: Any) => ZIO.fail("bad!")
  }

  def mapErrorPreservesTrace = {
    import mapErrorPreservesTraceFixture._

    val io = for {
      t <- Task(fail())
            .flatMap(succ)
            .mapError(mapError)
    } yield t

    io causeMust { cause =>
      // mapError does not change the trace in any way from its state during `fail()`
      // as a consequence, `executionTrace` is not updated with finalizer info, etc...
      // but overall it's a good thing since you're not losing traces at the border between your domain errors & Throwable
      (cause.traces must have size 1) and
        (cause.traces.head.executionTrace must have size 1) and
        (cause.traces.head.executionTrace.head must mentionMethod("fail")) and
        (cause.traces.head.stackTrace must have size 4) and
        (cause.traces.head.stackTrace.head must mentionMethod("succ")) and
        (cause.traces.head.stackTrace(1) must mentionMethod("mapError")) and
        (cause.traces.head.stackTrace(2) must mentionMethod("mapErrorPreservesTrace"))
    }
  }

  object mapErrorPreservesTraceFixture {
    val succ     = ZIO.succeed(_: ZTrace)
    val fail     = () => throw new Exception("error!")
    val mapError = (_: Any) => ()
  }

  def foldMWithOptimizedEffect = {
    import foldMWithOptimizedEffectFixture._

    val io = for {
      t <- Task(fail())
            .flatMap(badMethod1)
            .foldM(mkTrace, badMethod2)
    } yield t

    unsafeRun(io) must { trace: ZTrace =>
      show(trace)

      (trace.stackTrace must have size 2) and
        (trace.stackTrace must mentionMethod("foldMWithOptimizedEffect")) and
        (trace.executionTrace must have size 2) and
        (trace.executionTrace.head must mentionMethod("mkTrace")) and
        (trace.executionTrace.last must mentionMethod("fail"))
    }
  }

  object foldMWithOptimizedEffectFixture {
    val mkTrace    = (_: Any) => ZIO.trace
    val fail       = () => throw new Exception("error!")
    val badMethod1 = ZIO.succeed(_: ZTrace)
    val badMethod2 = ZIO.succeed(_: ZTrace)
  }

}
