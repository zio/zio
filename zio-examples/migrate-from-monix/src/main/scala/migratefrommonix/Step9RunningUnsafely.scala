package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Running Effects Unsafely
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step9RunningUnsafely"
 */
object Step9RunningUnsafely extends App {
  // Synchronous run — returns Exit[E, A]
  val exit: Exit[Throwable, String] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.attempt("hello"))
    }

  println(s"Exit: $exit")

  // Extract value with getOrThrow
  val value: String =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.attempt("hello")).getOrThrow()
    }

  println(s"Value: $value")

  // Run to Future — for interop
  val future =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.runToFuture(ZIO.attempt("async"))
    }

  Thread.sleep(100)
  println(s"Future value: ${future.value}")
}
