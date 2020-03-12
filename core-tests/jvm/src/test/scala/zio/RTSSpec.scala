package zio

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.{ jvm, nonFlaky, silent }
import zio.test._
import zio.test.environment.Live

object RTSSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("Blocking specs (to be migrated to ZIOSpecJvm)")(
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
      assertM(Live.live(io))(isTrue)
    },
    testM("blocking IO is effect blocking") {
      for {
        done  <- Ref.make(false)
        start <- IO.succeedNow(internal.OneShot.make[Unit])
        fiber <- blocking.effectBlockingInterrupt { start.set(()); Thread.sleep(60L * 60L * 1000L) }
                  .ensuring(done.set(true))
                  .fork
        _     <- IO.succeedNow(start.get())
        res   <- fiber.interrupt
        value <- done.get
      } yield assert(res)(isInterrupted) && assert(value)(isTrue)
    },
    testM("cancelation is guaranteed") {
      val io =
        for {
          release <- zio.Promise.make[Nothing, Int]
          latch   = internal.OneShot.make[Unit]
          async   = IO.effectAsyncInterrupt[Nothing, Unit] { _ => latch.set(()); Left(release.succeed(42).unit) }
          fiber   <- async.fork
          _       <- IO.effectTotal(latch.get(1000))
          _       <- fiber.interrupt.fork
          result  <- release.await
        } yield result == 42

      assertM(io)(isTrue)
    },
    testM("Fiber dump looks correct") {
      for {
        promise <- Promise.make[Nothing, Int]
        fiber   <- promise.await.fork
        dump    <- fiber.dump
        dumpStr <- dump.prettyPrintM
        _       <- console.putStrLn(dumpStr)
      } yield assert(dumpStr)(anything)
    } @@ silent,
    testM("interruption causes") {
      for {
        queue    <- Queue.bounded[Int](100)
        producer <- queue.offer(42).forever.fork
        rez      <- producer.interrupt
        _        <- console.putStrLn(rez.fold(_.prettyPrint, _ => ""))
      } yield assert(rez)(anything)
    } @@ zioTag(interruption) @@ silent,
    testM("interruption of unending bracket") {
      val io =
        for {
          startLatch <- Promise.make[Nothing, Int]
          exitLatch  <- Promise.make[Nothing, Int]
          bracketed = IO
            .succeed(21)
            .bracketExit((r: Int, exit: Exit[Any, Any]) =>
              if (exit.interrupted) exitLatch.succeed(r)
              else IO.die(new Error("Unexpected case"))
            )(a => startLatch.succeed(a) *> IO.never *> IO.succeedNow(1))
          fiber      <- bracketed.fork
          startValue <- startLatch.await
          _          <- fiber.interrupt.fork
          exitValue  <- exitLatch.await
        } yield (startValue + exitValue) == 42

      assertM(io)(isTrue)
    } @@ zioTag(interruption) @@ jvm(nonFlaky),
    testM("deadlock regression 1") {
      import java.util.concurrent.Executors

      val rts = new BootstrapRuntime {}
      val e   = Executors.newSingleThreadExecutor()

      (0 until 10000).foreach { _ =>
        rts.unsafeRun {
          IO.effectAsync[Nothing, Int] { k =>
            val c: Callable[Unit] = () => k(IO.succeedNow(1))
            val _                 = e.submit(c)
          }
        }
      }

      assertM(ZIO.effect(e.shutdown()))(isUnit)
    } @@ zioTag(regression),
    testM("second callback call is ignored") {
      for {
        _ <- IO.effectAsync[Throwable, Int] { k =>
              k(IO.succeedNow(42))
              Thread.sleep(500)
              k(IO.succeedNow(42))
            }
        res <- IO.effectAsync[Throwable, String] { k =>
                Thread.sleep(1000)
                k(IO.succeedNow("ok"))
              }
      } yield assert(res)(equalTo("ok"))
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
                .repeat(Schedule.doUntil[Int](_ >= 1)) <* f.interrupt
        } yield c

      assertM(Live.live(zio))(isGreaterThanEqualTo(1))
    } @@ zioTag(interruption, regression)
  )
}
