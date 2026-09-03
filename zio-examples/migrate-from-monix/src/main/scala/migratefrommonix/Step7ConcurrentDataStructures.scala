package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Concurrent Data Structures
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step7ConcurrentDataStructures"
 */
object Step7ConcurrentDataStructures extends ZIOAppDefault {
  def run: Task[Unit] = ZIO.scoped {
    for {
      // Queue.bounded(1) — replace MVar
      mv <- Queue.bounded[Int](1)
      _  <- mv.offer(42)
      n  <- mv.take
      _  <- ZIO.succeed(println(s"Queue(1) value: $n"))

      // Queue.bounded(n) — replace ConcurrentQueue
      q  <- Queue.bounded[String](10)
      _  <- q.offer("hello")
      s  <- q.poll
      _  <- ZIO.succeed(println(s"Queue value: $s"))

      // Semaphore — withPermit replaces acquire/release
      sem <- Semaphore.make(2)
      _   <- sem.withPermit(ZIO.succeed(println("In critical section")))

      // Hub — replace ConcurrentChannel (broadcast)
      hub  <- Hub.bounded[String](16)
      sub1 <- hub.subscribe
      sub2 <- hub.subscribe
      _    <- hub.publish("hello, all")
      m1   <- sub1.take
      m2   <- sub2.take
      _    <- ZIO.succeed(println(s"Hub sub1=$m1 sub2=$m2"))
    } yield ()
  }
}
