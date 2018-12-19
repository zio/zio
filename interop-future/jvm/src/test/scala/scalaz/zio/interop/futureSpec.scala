package scalaz.zio
package interop

import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.Exit.Cause.{ Checked, Unchecked }
import scalaz.zio.interop.future._

import scala.concurrent.{ ExecutionContext, Future }

class futureSpec(implicit ee: ExecutionEnv) extends AbstractRTSSpec {

  def is = s2"""
  `IO.fromFuture` must
    be lazy on the `Future` parameter                    $lazyOnParamRef
    be lazy on the `Future` parameter inline             $lazyOnParamInline
    catch exceptions thrown by lazy block                $catchBlockException
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFuture
    return an `IO` that produces the value from `Future` $produceValueFromFuture
  `Task.fromFuture` must
    catch exceptions thrown by lazy block                 $catchBlockExceptionTask
    return a `Task` that fails if `Future` fails          $propagateExceptionFromFutureTask
    return a `Task` that produces the value from `Future` $produceValueFromFutureTask
  `IO.fromFutureAction` must
    catch exceptions thrown by lazy block                $futureActionCatchBlockException
    return an `IO` that fails if `Future` fails          $futureActionPropagateExceptionFromFuture
    return an `IO` that produces the value from `Future` $futureActionProduceValueFromFuture
  `IO.toFuture` must
    produce always a successful `IO` of `Future`         $toFutureAlwaysSucceeds
    be polymorphic in error type                         $toFuturePoly
    return a `Future` that fails if `IO` fails           $toFutureFailed
    return a `Future` that produces the value from `IO`  $toFutureValue
  `IO.toFutureE` must
    convert error of type `E` to `Throwable`             $toFutureE
  `Fiber.fromFuture` must
    be lazy on the `Future` parameter                    $lazyOnParamRefFiber
    be lazy on the `Future` parameter inline             $lazyOnParamInlineFiber
    catch exceptions thrown by lazy block                $catchBlockExceptionFiber
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFutureFiber
    return an `IO` that produces the value from `Future` $produceValueFromFutureFiber
  `RTS.unsafeToFuture` must
    return a `Future` that produces a value from `IO`    $unsafeToFutureSucceeds
    return a failing `Future` when the `IO` fails        $unsafeToFutureFails
    return a failing `Future` with FiberFailure when 
    `IO` terminates                                      $unsafeToFutureTerminates
  """

  val ec = ee.executionContext

  private val lazyOnParamRef = {
    var evaluated = false
    def ftr       = Future { evaluated = true }
    IO.fromFuture(ec)(ftr _)
    evaluated must beFalse
  }

  private val lazyOnParamInline = {
    var evaluated = false
    IO.fromFuture(ec)(() => Future { evaluated = true })
    evaluated must beFalse
  }

  private val catchBlockException = {
    val ex                     = new Exception("no future for you!")
    def noFuture: Future[Unit] = throw ex
    unsafeRun(IO.fromFuture(ec)(noFuture _)) must (throwA(FiberFailure(Unchecked(ex))))
  }

  private val catchBlockExceptionTask = {
    val ex                     = new Exception("no value for you!")
    val noFuture: Future[Unit] = Future.failed(ex)
    unsafeRun(Task.fromFutureTask(ec)(Task { noFuture })) must throwA(FiberFailure(Checked(ex)))
  }

  private val propagateExceptionFromFuture = {
    val ex                    = new Exception("no value for you!")
    def noValue: Future[Unit] = Future { throw ex }
    unsafeRun(IO.fromFuture(ec)(noValue _)) must throwA(FiberFailure(Checked(ex)))
  }

  private val propagateExceptionFromFutureTask = {
    val ex                    = new Exception("no value for you!")
    val noValue: Future[Unit] = Future.failed(ex)
    unsafeRun(Task.fromFutureTask(ec)(Task { noValue })) must throwA(FiberFailure(Checked(ex)))
  }

  private val produceValueFromFuture = {
    def someValue: Future[Int] = Future { 42 }
    unsafeRun(IO.fromFuture(ec)(someValue _)) must_=== 42
  }

  private val produceValueFromFutureTask = {
    val someValue: Future[Int] = Future { 42 }
    unsafeRun(Task.fromFutureTask(ec)(Task { someValue })) must_=== 42
  }

  private val futureActionCatchBlockException = {
    val ex = new Exception("no future for you!")
    def noFuture(ec: ExecutionContext): Future[Unit] = {
      val _ = ec
      throw ex
    }
    unsafeRun(IO.fromFutureAction(noFuture)) must (throwA(FiberFailure(Unchecked(ex))))
  }

  private val futureActionPropagateExceptionFromFuture = {
    val ex                                          = new Exception("no value for you!")
    def noValue(ec: ExecutionContext): Future[Unit] = Future { throw ex }(ec)
    unsafeRun(IO.fromFutureAction(noValue)) must throwA(FiberFailure(Checked(ex)))
  }

  private val futureActionProduceValueFromFuture = {
    def someValue(ec: ExecutionContext): Future[Int] = Future { 42 }(ec)
    unsafeRun(IO.fromFutureAction(someValue)) must_=== 42
  }

  private val toFutureAlwaysSucceeds = {
    val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toFuture) must beAnInstanceOf[Future[Unit]]
  }

  private val toFuturePoly = {
    val unitIO: IO[Throwable, Unit]      = IO.unit
    val polyIO: IO[String, Future[Unit]] = unitIO.toFuture
    val _                                = polyIO // avoid warning
    ok
  }

  private val toFutureFailed = {
    val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toFuture) must throwA[Exception](message = "IOs also can fail").await
  }

  val toFutureValue = {
    val someIO = IO.succeed[Int](42)
    unsafeRun(someIO.toFuture) must beEqualTo(42).await
  }

  private val toFutureE = {
    val failedIO = IO.fail[String]("IOs also can fail")
    unsafeRun(failedIO.toFutureE(new Exception(_))) must throwA[Exception](message = "IOs also can fail").await
  }

  private val lazyOnParamRefFiber = {
    var evaluated = false
    def ftr       = Future { evaluated = true }
    Fiber.fromFuture(ec)(ftr _)
    evaluated must beFalse
  }

  private val lazyOnParamInlineFiber = {
    var evaluated = false
    Fiber.fromFuture(ec)(() => Future { evaluated = true })
    evaluated must beFalse
  }

  private val catchBlockExceptionFiber = {
    val ex                     = new Exception("no future for you!")
    def noFuture: Future[Unit] = throw ex
    unsafeRun(Fiber.fromFuture(ec)(noFuture _).join) must (throwA(FiberFailure(Unchecked(ex))))
  }

  private val propagateExceptionFromFutureFiber = {
    val ex                    = new Exception("no value for you!")
    def noValue: Future[Unit] = Future { throw ex }
    unsafeRun(Fiber.fromFuture(ec)(noValue _).join) must (throwA(FiberFailure(Checked(ex))))
  }

  private val produceValueFromFutureFiber = {
    def someValue: Future[Int] = Future { 42 }
    unsafeRun(Fiber.fromFuture(ec)(someValue _).join) must_=== 42
  }

  private val unsafeToFutureSucceeds = {
    this.unsafeToFuture(IO.succeed(5)) must be_===(5).await
  }

  private val unsafeToFutureFails = {
    val e: Throwable = new Exception("Ouch")
    this.unsafeToFuture(IO.fail(e)).failed must be_===(e).await
  }

  private val unsafeToFutureTerminates = {
    this.unsafeToFuture(IO.die(new Exception("Bang"))) must throwA[FiberFailure].await
  }

}
