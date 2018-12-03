package scalaz.zio
package interop

import scala.concurrent.Future
import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.ExitResult.Cause.{ Checked, Unchecked }
import scalaz.zio.interop.future._

class futureSpec(implicit ee: ExecutionEnv) extends AbstractRTSSpec {

  def is = s2"""
  `IO.fromFuture` must
    be lazy on the `Future` parameter                    $lazyOnParamRef
    be lazy on the `Future` parameter inline             $lazyOnParamInline
    catch exceptions thrown by lazy block                $catchBlockException
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFuture
    return an `IO` that produces the value from `Future` $produceValueFromFuture
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
  """

  val ec = ee.executionContext

  val lazyOnParamRef = {
    var evaluated = false
    def ftr       = Future { evaluated = true }
    IO.fromFuture(ftr _)(ec)
    evaluated must beFalse
  }

  val lazyOnParamInline = {
    var evaluated = false
    IO.fromFuture(() => Future { evaluated = true })(ec)
    evaluated must beFalse
  }

  val catchBlockException = {
    val ex                     = new Exception("no future for you!")
    def noFuture: Future[Unit] = throw ex
    unsafeRun(IO.fromFuture(noFuture _)(ec)) must (throwA(FiberFailure(Unchecked(ex))))
  }

  val catchBlockExceptionTask = {
    val ex                     = new Exception("no value for you!")
    val noFuture: Future[Unit] = Future.failed(ex)
    unsafeRun(Task.fromFuture(Task { noFuture })(ec)) must throwA(FiberFailure(Checked(ex)))
  }

  val propagateExceptionFromFuture = {
    val ex                    = new Exception("no value for you!")
    def noValue: Future[Unit] = Future { throw ex }
    unsafeRun(IO.fromFuture(noValue _)(ec)) must throwA(FiberFailure(Checked(ex)))
  }

  val propagateExceptionFromFutureTask = {
    val ex                    = new Exception("no value for you!")
    val noValue: Future[Unit] = Future.failed(ex)
    unsafeRun(Task.fromFuture(Task { noValue })(ec)) must throwA(FiberFailure(Checked(ex)))
  }

  val produceValueFromFuture = {
    def someValue: Future[Int] = Future { 42 }
    unsafeRun(IO.fromFuture(someValue _)(ec)) must_=== 42
  }

  val produceValueFromFutureTask = {
    val someValue: Future[Int] = Future { 42 }
    unsafeRun(Task.fromFuture(Task { someValue })(ec)) must_=== 42
  }

  val toFutureAlwaysSucceeds = {
    val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toFuture) must beAnInstanceOf[Future[Unit]]
  }

  val toFuturePoly = {
    val unitIO: IO[Throwable, Unit]      = IO.unit
    val polyIO: IO[String, Future[Unit]] = unitIO.toFuture
    val _                                = polyIO // avoid warning
    ok
  }

  val toFutureFailed = {
    val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toFuture) must throwA[Exception](message = "IOs also can fail").await
  }

  val toFutureValue = {
    val someIO = IO.now[Int](42)
    unsafeRun(someIO.toFuture) must beEqualTo(42).await
  }

  val toFutureE = {
    val failedIO = IO.fail[String]("IOs also can fail")
    unsafeRun(failedIO.toFutureE(new Exception(_))) must throwA[Exception](message = "IOs also can fail").await
  }

  val lazyOnParamRefFiber = {
    var evaluated = false
    def ftr       = Future { evaluated = true }
    Fiber.fromFuture(ftr _)(ec)
    evaluated must beFalse
  }

  val lazyOnParamInlineFiber = {
    var evaluated = false
    def ftr       = Future { evaluated = true }
    Fiber.fromFuture(ftr _)(ec)
    evaluated must beFalse
  }

  val catchBlockExceptionFiber = {
    val ex                     = new Exception("no future for you!")
    def noFuture: Future[Unit] = throw ex
    unsafeRun(Fiber.fromFuture(noFuture _)(ec).join) must (throwA(FiberFailure(Unchecked(ex))))
  }

  val propagateExceptionFromFutureFiber = {
    val ex                    = new Exception("no value for you!")
    def noValue: Future[Unit] = Future { throw ex }
    unsafeRun(Fiber.fromFuture(noValue _)(ec).join) must (throwA(FiberFailure(Checked(ex))))
  }

  val produceValueFromFutureFiber = {
    def someValue: Future[Int] = Future { 42 }
    unsafeRun(Fiber.fromFuture(someValue _)(ec).join) must_=== 42
  }

}
