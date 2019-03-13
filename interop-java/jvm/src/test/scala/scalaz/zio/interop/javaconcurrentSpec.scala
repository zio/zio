package scalaz.zio
package interop

import java.util.concurrent.{ CompletableFuture, CompletionStage, Future }

import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.Exit.Cause.{ Die, Fail }
import scalaz.zio.interop.javaconcurrent._

class javaconcurrentSpec(implicit ee: ExecutionEnv) extends TestRuntime {

  def is = s2"""
  `IO.fromFutureJava` must
    be lazy on the `Future` parameter                    $lazyOnParamRef
    be lazy on the `Future` parameter inline             $lazyOnParamInline
    catch exceptions thrown by lazy block                $catchBlockException
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFuture
    return an `IO` that produces the value from `Future` $produceValueFromFuture
  `IO.fromCompletionStage` must
    be lazy on the `Future` parameter                    $lazyOnParamRefCs
    be lazy on the `Future` parameter inline             $lazyOnParamInlineCs
    catch exceptions thrown by lazy block                $catchBlockExceptionCs
    return an `IO` that fails if `Future` fails          $propagateExceptionFromCs
    return an `IO` that produces the value from `Future` $produceValueFromCs
  `IO.toCompletableFuture` must
    produce always a successful `IO` of `Future`         $toCompletableFutureAlwaysSucceeds
    be polymorphic in error type                         $toCompletableFuturePoly
    return a `CompletableFuture` that fails if `IO` fails           $toCompletableFutureFailed
    return a `CompletableFuture` that produces the value from `IO`  $toCompletableFutureValue
  `IO.toCompletableFutureE` must
    convert error of type `E` to `Throwable`             $toCompletableFutureE
  `Fiber.fromFutureJava` must
    be lazy on the `Future` parameter                    $lazyOnParamRefFiber
    be lazy on the `Future` parameter inline             $lazyOnParamInlineFiber
    catch exceptions thrown by lazy block                $catchBlockExceptionFiber
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFutureFiber
    return an `IO` that produces the value from `Future` $produceValueFromFutureFiber
  """

  val lazyOnParamRef = {
    var evaluated         = false
    def ftr: Future[Unit] = CompletableFuture.supplyAsync(evaluated)
    IO.fromFutureJava(ftr)
    evaluated must beFalse
  }

  val lazyOnParamInline = {
    var evaluated = false
    IO.fromFutureJava(CompletableFuture.supplyAsync(evaluated))
    evaluated must beFalse
  }

  val catchBlockException = {
    val ex                     = new Exception("no future for you!")
    def noFuture: Future[Unit] = throw ex
    unsafeRun(IO.fromFutureJava(noFuture)) must (throwA(FiberFailure(Die(ex))))
  }

  val propagateExceptionFromFuture = {
    val ex                    = new Exception("no value for you!")
    def noValue: Future[Unit] = CompletableFuture.supplyAsync(throw ex)
    unsafeRun(IO.fromFutureJava(noValue)) must throwA(FiberFailure(Fail(ex)))
  }

  val produceValueFromFuture = {
    def someValue: Future[Int] = CompletableFuture.completedFuture(42)
    unsafeRun(IO.fromFutureJava(someValue)) must_=== 42
  }

  val lazyOnParamRefCs = {
    var evaluated                 = false
    def cs: CompletionStage[Unit] = CompletableFuture.supplyAsync(evaluated)
    IO.fromCompletionStage(cs)
    evaluated must beFalse
  }

  val lazyOnParamInlineCs = {
    var evaluated = false
    IO.fromCompletionStage(CompletableFuture.supplyAsync(evaluated))
    evaluated must beFalse
  }

  val catchBlockExceptionCs = {
    val ex                              = new Exception("no future for you!")
    def noFuture: CompletionStage[Unit] = throw ex
    unsafeRun(IO.fromCompletionStage(noFuture)) must (throwA(FiberFailure(Die(ex))))
  }

  val propagateExceptionFromCs = {
    val ex                             = new Exception("no value for you!")
    def noValue: CompletionStage[Unit] = CompletableFuture.supplyAsync(throw ex)
    unsafeRun(IO.fromCompletionStage(noValue)) must throwA(FiberFailure(Fail(ex)))
  }

  val produceValueFromCs = {
    def someValue: CompletionStage[Int] = CompletableFuture.completedFuture(42)
    unsafeRun(IO.fromCompletionStage(someValue)) must_=== 42
  }

  val toCompletableFutureAlwaysSucceeds = {
    val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toCompletableFuture) must beAnInstanceOf[CompletableFuture[Unit]]
  }

  val toCompletableFuturePoly = {
    val unitIO: Task[Unit]                          = IO.unit
    val polyIO: IO[String, CompletableFuture[Unit]] = unitIO.toCompletableFuture
    val _                                           = polyIO // avoid warning
    ok
  }

  val toCompletableFutureFailed = {
    val failedIO: Task[Unit] = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toCompletableFuture).get() must throwA[Exception](message = "IOs also can fail")
  }

  val toCompletableFutureValue = {
    val someIO = IO.succeed[Int](42)
    unsafeRun(someIO.toCompletableFuture).get() must beEqualTo(42)
  }

  val toCompletableFutureE = {
    val failedIO: IO[String, Unit] = IO.fail[String]("IOs also can fail")
    unsafeRun(failedIO.toCompletableFutureE(new Exception(_))).get() must throwA[Exception](
      message = "IOs also can fail"
    )
  }

  val lazyOnParamRefFiber = {
    var evaluated         = false
    def ftr: Future[Unit] = CompletableFuture.supplyAsync(evaluated)
    Fiber.fromFutureJava(ftr)
    evaluated must beFalse
  }

  val lazyOnParamInlineFiber = {
    var evaluated = false
    Fiber.fromFutureJava(CompletableFuture.supplyAsync(evaluated))
    evaluated must beFalse
  }

  val catchBlockExceptionFiber = {
    val ex                     = new Exception("no future for you!")
    def noFuture: Future[Unit] = throw ex
    unsafeRun(Fiber.fromFutureJava(noFuture).join) must (throwA(FiberFailure(Die(ex))))
  }

  val propagateExceptionFromFutureFiber = {
    val ex                    = new Exception("no value for you!")
    def noValue: Future[Unit] = CompletableFuture.supplyAsync(throw ex)
    unsafeRun(Fiber.fromFutureJava(noValue).join) must (throwA(FiberFailure(Fail(ex))))
  }

  val produceValueFromFutureFiber = {
    def someValue: Future[Int] = CompletableFuture.completedFuture(42)
    unsafeRun(Fiber.fromFutureJava(someValue).join) must_=== 42
  }

}
