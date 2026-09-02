package migratefrommonix.monix

import cats.effect.{ContextShift, ExitCode}
import monix.catnap.{ConcurrentQueue, Semaphore}
import monix.eval.{Task, TaskApp}
import monix.execution.Scheduler

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Concurrent Data Structures
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step7ConcurrentDataStructures"
 */
object Step7ConcurrentDataStructures extends TaskApp {
  // ContextShift[Task] is required by monix-catnap's concurrent builders
  implicit val cs: ContextShift[Task] = Task.contextShift(Scheduler.global)

  def run(args: List[String]): Task[ExitCode] =
    for {
      // ConcurrentQueue(capacity=1) — simulates a single-slot channel (MVar)
      mv <- ConcurrentQueue[Task].bounded[Int](1)
      _  <- mv.offer(42)
      n  <- mv.poll
      _  <- Task.eval(println(s"ConcurrentQueue(1) value: $n"))

      // ConcurrentQueue — multi-slot
      q  <- ConcurrentQueue[Task].bounded[String](10)
      _  <- q.offer("hello")
      s  <- q.poll
      _  <- Task.eval(println(s"Queue value: $s"))

      // Semaphore
      sem <- Semaphore[Task](2)
      _   <- sem.acquire
      _   <- Task.eval(println("In critical section"))
      _   <- sem.release
    } yield ExitCode.Success
}
