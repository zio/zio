package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}
import cats.effect.std.{AtomicCell, CountDownLatch, Queue, Semaphore}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Concurrent Data Structures from cats-effect's std Module
 *
 * The "before" side of migratecatseffect.Step7ConcurrentDataStructures.
 * Note cats-effect's CountDownLatch uses `release`, not `countDown`.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step7ConcurrentDataStructures"
 */
object Step7ConcurrentDataStructures extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // Queue
      queue <- Queue.bounded[IO, Int](10)
      _     <- queue.offer(1)
      _     <- queue.offer(2)
      n     <- queue.take
      _     <- IO(println(s"Queue: took $n"))

      // Semaphore — permit is a Resource, used via .use
      sem <- Semaphore[IO](1)
      _   <- sem.permit.use(_ => IO(println("Semaphore: exclusive access granted")))

      // AtomicCell — effectful updates never interleave
      cell  <- AtomicCell[IO].of(0)
      _     <- cell.update(v => v + 1)
      cellV <- cell.get
      _     <- IO(println(s"AtomicCell: $cellV"))

      // CountDownLatch — release replaces zio.concurrent.CountdownLatch#countDown
      latch <- CountDownLatch[IO](2)
      w1    <- (IO(println("worker-1 finishing")) *> latch.release).start
      w2    <- (IO(println("worker-2 finishing")) *> latch.release).start
      _     <- w1.join *> w2.join
      _     <- latch.await
      _     <- IO(println("CountDownLatch: all workers finished"))
    } yield ()
}
