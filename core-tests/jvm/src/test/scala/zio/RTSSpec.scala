package zio

import zio.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.{nonFlaky, silent}
import zio.test._
import zio.test.environment.Live

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

object RTSSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec: ZSpec[Environment, Failure] = suite("Has[Blocking] specs (to be migrated to ZIOSpecJvm)")(
    testM("blocking caches threads") {
      import zio.Blocking

      def runAndTrack(ref: Ref[Set[Thread]]): ZIO[Has[Blocking] with Has[Clock], Nothing, Boolean] =
        Blocking.blocking {
          UIO(Thread.currentThread())
            .flatMap(thread => ref.modify(set => (set.contains(thread), set + thread))) <* ZIO
            .sleep(1.millis)
        }

      val io =
        for {
          accum <- Ref.make(Set.empty[Thread])
          b     <- runAndTrack(accum).repeatUntil(_ == true)
        } yield b
      assertM(Live.live(io))(isTrue)
    },
    testM("blocking IO is effect blocking") {
      for {
        done  <- Ref.make(false)
        start <- Promise.make[Nothing, Unit]
        fiber <- Blocking.effectBlockingInterrupt { start.unsafeDone(IO.unit); Thread.sleep(60L * 60L * 1000L) }
                   .ensuring(done.set(true))
                   .fork
        _     <- start.await
        res   <- fiber.interrupt
        value <- done.get
      } yield assert(res)(isInterrupted) && assert(value)(isTrue)
    } @@ nonFlaky,
    testM("cancelation is guaranteed") {
      val io =
        for {
          release <- Promise.make[Nothing, Int]
          latch   <- Promise.make[Nothing, Unit]
          async = IO.effectAsyncInterrupt[Nothing, Unit] { _ =>
                    latch.unsafeDone(IO.unit); Left(release.succeed(42).unit)
                  }
          fiber  <- async.fork
          _      <- latch.await
          _      <- fiber.interrupt.fork
          result <- release.await
        } yield result == 42

      assertM(io)(isTrue)
    } @@ nonFlaky,
    testM("Fiber dump looks correct") {
      for {
        promise <- Promise.make[Nothing, Int]
        fiber   <- promise.await.fork
        dump    <- fiber.dump
        dumpStr <- dump.prettyPrintM
        _       <- Console.printLine(dumpStr)
      } yield assert(dumpStr)(anything)
    } @@ silent,
    testM("interruption causes") {
      for {
        queue    <- Queue.bounded[Int](100)
        producer <- queue.offer(42).forever.fork
        rez      <- producer.interrupt
        _        <- Console.printLine(rez.fold(_.prettyPrint, _ => ""))
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
                        )(a => startLatch.succeed(a) *> IO.never *> IO.succeed(1))
          fiber      <- bracketed.fork
          startValue <- startLatch.await
          _          <- fiber.interrupt.fork
          exitValue  <- exitLatch.await
        } yield (startValue + exitValue) == 42

      assertM(io)(isTrue)
    } @@ zioTag(interruption) @@ nonFlaky,
    testM("deadlock regression 1") {
      import java.util.concurrent.Executors

      val rts = new BootstrapRuntime {}
      val e   = Executors.newSingleThreadExecutor()

      (0 until 10000).foreach { _ =>
        rts.unsafeRun {
          IO.effectAsync[Nothing, Int] { k =>
            val c: Callable[Unit] = () => k(IO.succeed(1))
            val _                 = e.submit(c)
          }
        }
      }

      assertM(ZIO.effect(e.shutdown()))(isUnit)
    } @@ zioTag(regression),
    testM("second callback call is ignored") {
      for {
        _ <- IO.effectAsync[Throwable, Int] { k =>
               k(IO.succeed(42))
               Thread.sleep(500)
               k(IO.succeed(42))
             }
        res <- IO.effectAsync[Throwable, String] { k =>
                 Thread.sleep(1000)
                 k(IO.succeed("ok"))
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
          c <- (IO.effectTotal[Int](c.get) <* Clock.sleep(1.millis))
                 .repeatUntil(_ >= 1) <* f.interrupt
        } yield c

      assertM(Live.live(zio))(isGreaterThanEqualTo(1))
    } @@ zioTag(interruption, regression),
    testM("unsafeRunAsync runs effects on ZIO thread pool") {
      for {
        runtime <- ZIO.runtime[Any]
        promise <- Promise.make[Nothing, String]
        _ <- UIO.effectTotal {
               val thread = new Thread("user-thread") {
                 override def run(): Unit =
                   runtime.unsafeRunAsync_ {
                     UIO.effectTotal(Thread.currentThread.getName).to(promise)
                   }
               }
               thread.start()
             }
        value <- promise.await
      } yield assert(value)(startsWithString("zio-default-async"))
    }
  )
}
