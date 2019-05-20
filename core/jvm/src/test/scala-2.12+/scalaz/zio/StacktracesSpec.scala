package scalaz.zio

import org.specs2.execute.Result
import org.specs2.matcher.{ Expectable, Matcher }
import org.specs2.mutable
import scalaz.zio.duration._
import scalaz.zio.internal.stacktracer.ZTraceElement
import scalaz.zio.internal.stacktracer.ZTraceElement.SourceLocation

class StacktracesSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with mutable.SpecificationLike {

  // Using mutable Spec here to easily run individual tests from Intellij to inspect result traces

  // set to true to show traces in log
  private val debug = true

  "basic test" >> basicTest

  "foreach" >> foreachTrace
  "foreach fail" >> foreachFail
  "foreachPar fail" >> foreachParFail

  "left-associative fold" >> leftAssociativeFold
  "nested left binds" >> nestedLeftBinds

  "fiber ancestry" >> fiberAncestry
  "fiber ancestry example with uploads" >> fiberAncestryUploadExample

  "blocking trace" >> blockingTrace

  "tracing regions" >> tracingRegions
  "tracing region is inherited on fork" >> tracingRegionsInheritance

  "execution trace example with conditional" >> executionTraceConditionalExample

  "single effect for-comprehension" >> singleTaskForComp
  "single effectTotal for-comprehension" >> singleUIOForComp
  "single effectTotalWith for-comprehension" >> singleEffectTotalWithForComp

  private def show(trace: ZTrace): Unit        = if (debug) println(trace.prettyPrint)
  private def show(cause: Exit.Cause[_]): Unit = if (debug) println(cause.prettyPrint)

  private def mentionsMethod(method: String, trace: ZTraceElement): Boolean =
    trace match {
      case s: SourceLocation => s.method contains method
      case _                 => false
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

  private implicit final class CauseMust[R >: Environment](io: ZIO[R, _, _]) {
    def causeMust(check: Exit.Cause[_] => Result): Result =
      unsafeRunSync(io).fold[Result](
        cause => {
          show(cause)
          check(cause)
        },
        _ => failure
      )
  }

  def basicTest = {
    val io = for {
      _     <- ZIO.unit
      trace <- ZIO.trace
    } yield trace

    unsafeRun(io) must { trace: ZTrace =>
      show(trace)

      (trace.executionTrace must have size 1) and
        (trace.executionTrace must mentionMethod("basicTest")) and
        (trace.stackTrace must have size 1) and
        (trace.stackTrace must mentionMethod("basicTest"))
    }
  }

  def foreachTrace = {
    import foreachTraceFixture._

    val io = for {
      _     <- effectTotal
      _     <- ZIO.foreach_(1 to 10)(_ => ZIO.unit *> ZIO.trace)
      trace <- ZIO.trace
    } yield trace

    unsafeRun(io) must { trace: ZTrace =>
      show(trace)

      (trace.stackTrace must have size 1) and
        (trace.stackTrace must mentionMethod("foreachTrace")) and
        (trace.executionTrace must mentionMethod("foreachTrace")) and
        (trace.executionTrace must mentionMethod("foreach_")) and
        (trace.executionTrace must mentionMethod("effectTotal"))
    }
  }

  object foreachTraceFixture {
    def effectTotal = ZIO.effectTotal(())
  }

  def foreachFail = {
    val io = for {
      t1 <- ZIO
             .foreach_(1 to 10) { i =>
               if (i == 7)
                 ZIO.unit *>
                   ZIO.fail("Dummy error!")
               else
                 ZIO.unit *>
                   ZIO.trace
             }
             .foldCauseM(e => IO(e.traces.head), _ => ZIO.dieMessage("can't be!"))
      t2 <- ZIO.trace
    } yield (t1, t2)

    unsafeRun(io) must { r: (ZTrace, ZTrace) =>
      val (trace1, trace2) = r

      (trace1.stackTrace must mentionMethod("foreach_")) and
        (trace1.stackTrace must mentionMethod("foreachFail")) and
        (trace1.executionTrace must mentionMethod("foreach_")) and
        (trace1.executionTrace must mentionMethod("foreachFail")) and
        (trace2.stackTrace must have size 1) and
        (trace2.stackTrace must mentionMethod("foreachFail")) and
        (trace2.executionTrace must mentionMethod("foreach_")) and
        (trace2.executionTrace must mentionMethod("foreachFail"))
    }
  }

  def foreachParFail = {
    val io = for {
      _ <- ZIO.foreachPar(1 to 10) { i =>
            ZIO.sleep(1.second) *> (if (i >= 7) UIO(i / 0) else UIO(i / 10))
          }
    } yield ()

    io causeMust {
      _.traces.head.stackTrace must have size 1 and contain {
        (_: ZTraceElement) match {
          case s: SourceLocation => s.method contains "foreachParFail"
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

      (trace.stackTrace must beEmpty) and
        (trace.executionTrace must forall[ZTraceElement](mentionsMethod("leftAssociativeFold", _)))
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
        (trace1.stackTrace must have size 4) and
        (trace1.stackTrace must mentionMethod("method2")) and
        (trace1.stackTrace must mentionMethod("method1")) and
        (trace1.stackTrace must mentionMethod("io")) and
        (trace2.stackTrace must have size 1) and
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
        .foldM(
          failure = _ => IO.fail(()),
          success = t =>
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
        _ <- UIO { throw new Exception() }
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
      (cause.traces.head.stackTrace must have size 1) and
        mentionsMethod("uploadUsers", cause.traces.head.stackTrace.head) and
        (cause.traces(1).stackTrace must beEmpty) and
        (cause.traces(1).executionTrace must have size 1) and
        mentionsMethod("uploadTo", cause.traces(1).executionTrace.head) and
        (cause.traces(1).parentTrace must not be empty) and
        (cause.traces(1).parentTrace.get.stackTrace must mentionMethod("uploadUsers"))
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

  def blockingTrace = {
    val io = for {
      _ <- blocking.effectBlocking { throw new Exception() }
    } yield ()

    io causeMust { cause =>
      val trace = cause.traces.head

      // the first items on exec trace and stack trace refer to this line
      mentionsMethod("blockingTrace", trace.stackTrace.head) and
        mentionsMethod("blockingTrace", trace.executionTrace.head)
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
        (cause.traces.head.stackTrace must beEmpty)
    }
  }

  object tracingRegionsFixture {
    val traceThis: () => String = () => "trace this!"
  }

  def tracingRegionsInheritance = {
    val io: ZIO[Any, Nothing, Unit] = for {
      _                <- ZIO.unit
      _                <- ZIO.unit
      untraceableFiber <- (ZIO.unit *> (ZIO.unit *> ZIO.unit *> ZIO.dieMessage("error!") *> ZIO.unit).fork).untraced
      _                <- untraceableFiber.join
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

      mentionsMethod("doSideWork", trace.executionTrace.head) and
        (trace.executionTrace must mentionMethod("doMainWork")) and
        mentionsMethod("doWork", trace.stackTrace.head)
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

  def singleTaskForComp = {
    import singleTaskForCompFixture._

    selectHumans causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.stackTrace must have size 1) and
        mentionsMethod("selectHumans", cause.traces.head.stackTrace.head)
    }
  }

  object singleTaskForCompFixture {
    def asyncDbCall(): Task[Unit] =
      Task(throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  def singleUIOForComp = {
    import singleUIOForCompFixture._

    selectHumans causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.stackTrace must have size 1) and
        mentionsMethod("selectHumans", cause.traces.head.stackTrace.head)
    }
  }

  object singleUIOForCompFixture {
    def asyncDbCall(): Task[Unit] =
      UIO(throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

  def singleEffectTotalWithForComp = {
    import singleEffectTotalWithForCompFixture._

    selectHumans causeMust { cause =>
      (cause.traces must have size 1) and
        (cause.traces.head.stackTrace must have size 1) and
        mentionsMethod("selectHumans", cause.traces.head.stackTrace.head)
    }
  }

  object singleEffectTotalWithForCompFixture {
    def asyncDbCall(): Task[Unit] =
      UIO.effectTotalWith(_ => throw new Exception)

    val selectHumans: Task[Unit] = for {
      _ <- asyncDbCall()
    } yield ()
  }

}
