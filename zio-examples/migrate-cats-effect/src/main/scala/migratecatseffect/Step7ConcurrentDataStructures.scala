package migratecatseffect

import zio._
import zio.concurrent.CountdownLatch

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Concurrent Data Structures from cats-effect's std Module
 *
 * Replaces:
 *   Queue[F, A]           -> zio.Queue[A]
 *   Semaphore[F]          -> zio.Semaphore
 *   CountDownLatch[F]     -> zio.concurrent.CountdownLatch
 *   AtomicCell[F, A]      -> Ref.Synchronized
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step7ConcurrentDataStructures"
 */
object Step7ConcurrentDataStructures extends ZIOAppDefault {

  def run: Task[Unit] =
    for {
      // Queue — replaces cats.effect.std.Queue
      queue <- Queue.bounded[Int](10)
      _     <- queue.offer(1)
      _     <- queue.offer(2)
      n     <- queue.take
      _     <- ZIO.succeed(println(s"Queue: took $n"))

      // Semaphore — replaces cats.effect.std.Semaphore
      sem <- Semaphore.make(1)
      _   <- sem.withPermit(ZIO.succeed(println("Semaphore: exclusive access granted")))

      // Ref.Synchronized — replaces cats.effect.std.AtomicCell; effectful updates never interleave
      cell   <- Ref.Synchronized.make(0)
      _      <- cell.updateZIO(v => ZIO.succeed(v + 1))
      cellV  <- cell.get
      _      <- ZIO.succeed(println(s"AtomicCell replacement: $cellV"))

      // CountdownLatch — replaces cats.effect.std.CountDownLatch
      latch <- CountdownLatch.make(2)
      w1    <- (ZIO.succeed(println("worker-1 finishing")) *> latch.countDown).fork
      w2    <- (ZIO.succeed(println("worker-2 finishing")) *> latch.countDown).fork
      _     <- w1.join *> w2.join
      _     <- latch.await
      _     <- ZIO.succeed(println("CountdownLatch: all workers finished"))
    } yield ()
}
