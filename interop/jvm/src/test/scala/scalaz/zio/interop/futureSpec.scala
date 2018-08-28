package scalaz.zio
package interop

import scala.concurrent.Future

import org.specs2.concurrent.ExecutionEnv

import future._

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
    def noFuture: Future[Unit] = throw new Exception("no future for you!")
    unsafeRun(IO.fromFuture(noFuture _)(ec)) must throwA[Exception](message = "no future for you!")
  }

  val propagateExceptionFromFuture = {
    def noValue: Future[Unit] = Future { throw new Exception("no value for you!") }
    unsafeRun(IO.fromFuture(noValue _)(ec)) must throwA[Exception](message = "no value for you!")
  }

  val produceValueFromFuture = {
    def someValue: Future[Int] = Future { 42 }
    unsafeRun(IO.fromFuture(someValue _)(ec)) must_=== 42
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
    Fiber.fromFuture(ftr)(ec)
    evaluated must beFalse
  }

  val lazyOnParamInlineFiber = {
    var evaluated = false
    Fiber.fromFuture(Future { evaluated = true })(ec)
    evaluated must beFalse
  }

  val catchBlockExceptionFiber = {
    def noFuture: Future[Unit] = throw new Exception("no future for you!")
    unsafeRun(Fiber.fromFuture(noFuture)(ec).join) must throwA[Exception](message = "no future for you!")
  }

  val propagateExceptionFromFutureFiber = {
    def noValue: Future[Unit] = Future { throw new Exception("no value for you!") }
    unsafeRun(Fiber.fromFuture(noValue)(ec).join) must throwA[Exception](message = "no value for you!")
  }

  val produceValueFromFutureFiber = {
    def someValue: Future[Int] = Future { 42 }
    unsafeRun(Fiber.fromFuture(someValue)(ec).join) must_=== 42
  }

}
