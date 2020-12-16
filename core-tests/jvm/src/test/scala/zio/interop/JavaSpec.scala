package zio
package interop

import zio.blocking.Blocking
import zio.interop.javaz._
import zio.test.Assertion._
import zio.test._

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import java.util.concurrent.{ CompletableFuture, CompletionStage, Future }

object JavaSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec: Spec[Blocking, TestFailure[Any], TestSuccess] = suite("JavaSpec")(
    suite("`Task.fromFutureJava` must")(
      testM("be lazy on the `Future` parameter") {
        var evaluated         = false
        def ftr: Future[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        assertM(ZIO.fromFutureJava(ftr).when(false).as(evaluated))(isFalse)
      },
      testM("execute the `Future` parameter only once") {
        var count            = 0
        def ftr: Future[Int] = CompletableFuture.supplyAsync { () => count += 1; count }
        assertM(ZIO.fromFutureJava(ftr).run)(succeeds(equalTo(1)))
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                          = new Exception("no future for you!")
        lazy val noFuture: Future[Unit] = throw ex
        assertM(ZIO.fromFutureJava(noFuture).run)(fails(equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                         = new Exception("no value for you!")
        lazy val noValue: Future[Unit] = CompletableFuture_.failedFuture(ex)
        assertM(ZIO.fromFutureJava(noValue).run)(fails(equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (supplyAsync)") {
        val ex                         = new Exception("no value for you!")
        lazy val noValue: Future[Unit] = CompletableFuture.supplyAsync(() => throw ex)
        assertM(ZIO.fromFutureJava(noValue).run)(fails(equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that produces the value from `Future`") {
        lazy val someValue: Future[Int] = CompletableFuture.completedFuture(42)
        assertM(ZIO.fromFutureJava(someValue))(equalTo(42))
      },
      testM("handle null produced by the completed `Future`") {
        lazy val someValue: Future[String] = CompletableFuture.completedFuture[String](null)
        assertM(ZIO.fromFutureJava(someValue).map(Option(_)))(isNone)
      } @@ zioTag(errors),
      testM("be referentially transparent") {
        var n    = 0
        val task = ZIO.fromFutureJava(CompletableFuture.supplyAsync(() => n += 1))
        for {
          _ <- task
          _ <- task
        } yield assert(n)(equalTo(2))
      }
    ) @@ zioTag(future),
    suite("`Task.fromCompletionStage` must")(
      testM("be lazy on the `Future` parameter") {
        var evaluated                 = false
        def cs: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        assertM(ZIO.fromCompletionStage(cs).when(false).as(evaluated))(isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                                   = new Exception("no future for you!")
        lazy val noFuture: CompletionStage[Unit] = throw ex
        assertM(ZIO.fromCompletionStage(noFuture).run)(fails(equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                                  = new Exception("no value for you!")
        lazy val noValue: CompletionStage[Unit] = CompletableFuture_.failedFuture(ex)
        assertM(ZIO.fromCompletionStage(noValue).run)(fails[Throwable](equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (supplyAsync)") {
        val ex                                  = new Exception("no value for you!")
        lazy val noValue: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => throw ex)
        assertM(ZIO.fromCompletionStage(noValue).run)(fails[Throwable](equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that produces the value from `Future`") {
        lazy val someValue: CompletionStage[Int] = CompletableFuture.completedFuture(42)
        assertM(ZIO.fromCompletionStage(someValue))(equalTo(42))
      },
      testM("handle null produced by the completed `Future`") {
        lazy val someValue: CompletionStage[String] = CompletableFuture.completedFuture[String](null)
        assertM(ZIO.fromCompletionStage(someValue).map(Option(_)))(isNone)
      } @@ zioTag(errors),
      testM("be referentially transparent") {
        var n    = 0
        val task = ZIO.fromCompletionStage(CompletableFuture.supplyAsync(() => n += 1))
        for {
          _ <- task
          _ <- task
        } yield assert(n)(equalTo(2))
      }
    ) @@ zioTag(future),
    suite("`Task.toCompletableFuture` must")(
      testM("produce always a successful `IO` of `Future`") {
        val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
        assertM(failedIO.toCompletableFuture)(isSubtype[CompletableFuture[Unit]](anything))
      },
      test("be polymorphic in error type") {
        val unitIO: Task[Unit]                          = Task.unit
        val polyIO: IO[String, CompletableFuture[Unit]] = unitIO.toCompletableFuture
        assert(polyIO)(anything)
      } @@ zioTag(errors),
      testM("return a `CompletableFuture` that fails if `IO` fails") {
        val ex                       = new Exception("IOs also can fail")
        val failedIO: Task[Unit]     = IO.fail[Throwable](ex)
        val failedFuture: Task[Unit] = failedIO.toCompletableFuture.flatMap(f => Task(f.get()))
        assertM(failedFuture.run)(
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      } @@ zioTag(errors),
      testM("return a `CompletableFuture` that produces the value from `IO`") {
        val someIO = Task.succeed[Int](42)
        assertM(someIO.toCompletableFuture.map(_.get()))(equalTo(42))
      }
    ) @@ zioTag(future),
    suite("`Task.toCompletableFutureE` must")(
      testM("convert error of type `E` to `Throwable`") {
        val failedIO: IO[String, Unit] = IO.fail[String]("IOs also can fail")
        val failedFuture: Task[Unit] =
          failedIO.toCompletableFutureWith(new Exception(_)).flatMap(f => Task(f.get()))
        assertM(failedFuture.run)(
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      } @@ zioTag(errors)
    ) @@ zioTag(future),
    suite("`Fiber.fromCompletionStage`")(
      test("be lazy on the `Future` parameter") {
        var evaluated                  = false
        def ftr: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        Fiber.fromCompletionStage(ftr)
        assert(evaluated)(isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                              = new Exception("no future for you!")
        def noFuture: CompletionStage[Unit] = throw ex
        assertM(Fiber.fromCompletionStage(noFuture).join.run)(fails(equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                             = new Exception("no value for you!")
        def noValue: CompletionStage[Unit] = CompletableFuture_.failedFuture(ex)
        assertM(Fiber.fromCompletionStage(noValue).join.run)(fails[Throwable](equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (supplyAsync)") {
        val ex                             = new Exception("no value for you!")
        def noValue: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => throw ex)
        assertM(Fiber.fromCompletionStage(noValue).join.run)(fails[Throwable](equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that produces the value from `Future`") {
        def someValue: CompletionStage[Int] = CompletableFuture.completedFuture(42)
        assertM(Fiber.fromCompletionStage(someValue).join.run)(succeeds(equalTo(42)))
      }
    ) @@ zioTag(future),
    suite("`Fiber.fromFutureJava` must")(
      test("be lazy on the `Future` parameter") {
        var evaluated         = false
        def ftr: Future[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        Fiber.fromFutureJava(ftr)
        assert(evaluated)(isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                     = new Exception("no future for you!")
        def noFuture: Future[Unit] = throw ex
        assertM(Fiber.fromFutureJava(noFuture).join.run)(fails(equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                    = new Exception("no value for you!")
        def noValue: Future[Unit] = CompletableFuture_.failedFuture(ex)
        assertM(Fiber.fromFutureJava(noValue).join.run)(fails[Throwable](equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                    = new Exception("no value for you!")
        def noValue: Future[Unit] = CompletableFuture.supplyAsync(() => throw ex)
        assertM(Fiber.fromFutureJava(noValue).join.run)(fails[Throwable](equalTo(ex)))
      } @@ zioTag(errors),
      testM("return an `IO` that produces the value from `Future`") {
        def someValue: Future[Int] = CompletableFuture.completedFuture(42)
        assertM(Fiber.fromFutureJava(someValue).join.run)(succeeds(equalTo(42)))
      }
    ) @@ zioTag(future),
    suite("`Task.withCompletionHandler` must")(
      testM("write and read to and from AsynchronousSocketChannel") {
        val list: List[Byte] = List(13)
        val address          = new InetSocketAddress(54321)
        val server           = AsynchronousServerSocketChannel.open().bind(address)
        val client           = AsynchronousSocketChannel.open()

        val taskServer = for {
          c <- ZIO.effectAsyncWithCompletionHandler[AsynchronousSocketChannel](server.accept((), _))
          w <- ZIO.effectAsyncWithCompletionHandler[Integer](c.write(ByteBuffer.wrap(list.toArray), (), _))
        } yield w

        val taskClient = for {
          _     <- ZIO.effectAsyncWithCompletionHandler[Void](client.connect(address, (), _))
          buffer = ByteBuffer.allocate(1)
          r     <- ZIO.effectAsyncWithCompletionHandler[Integer](client.read(buffer, (), _))
        } yield (r, buffer.array.toList)

        val task = for {
          fiberServer  <- taskServer.fork
          fiberClient  <- taskClient.fork
          resultServer <- fiberServer.join
          resultClient <- fiberClient.join
          _            <- ZIO.effectTotal(server.close())
        } yield (resultServer, resultClient)

        assertM(task.run)(
          succeeds[(Integer, (Integer, List[Byte]))](equalTo((Integer.valueOf(1), (Integer.valueOf(1), list))))
        )
      }
    ) @@ zioTag(future)
  )
}
