package migratefrommonix.monix

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp, TaskLocal}
import monix.execution.atomic.Atomic

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Shared State (Atomic → Ref, TaskLocal → FiberRef)
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step6SharedState"
 */
object Step6SharedState extends TaskApp {
  def run(args: List[String]): Task[ExitCode] = {
    // Atomic — synchronous, outside the effect system
    val counter = Atomic(0)
    counter.transform(_ + 1)
    val v1 = counter.get()

    for {
      _ <- Task.eval(println(s"Atomic counter: $v1"))

      // TaskLocal — fiber-local state
      local <- TaskLocal(0)
      _     <- local.write(42)
      v2    <- local.read
      _     <- Task.eval(println(s"TaskLocal: $v2"))
    } yield ExitCode.Success
  }
}
