package threadlocalbridge

import zio._

/** Title: Understanding ThreadLocal in Async Code
  * Description: This example demonstrates the problem with using Java ThreadLocal
  * in asynchronous code and why ThreadLocalBridge is needed.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.Concept1Example"
  */
object Concept1Example extends ZIOAppDefault {

  // A simple Java ThreadLocal to store request context
  val requestIdThreadLocal: java.lang.ThreadLocal[String] =
    new java.lang.ThreadLocal[String] {
      override def initialValue(): String = "unset"
    }

  // Without ThreadLocalBridge, ThreadLocal values are lost across fiber boundaries
  val problemExample: ZIO[Any, Nothing, Unit] = for {
    _ <- ZIO.succeed {
      requestIdThreadLocal.set("request-001")
      println(s"Main fiber: request ID = ${requestIdThreadLocal.get()}")
    }

    // When we fork a new fiber, ThreadLocal context is NOT inherited
    _ <- ZIO.succeed {
      println(s"Forked fiber: request ID = ${requestIdThreadLocal.get()}")
      // This will print "unset" instead of "request-001"
    }.fork.flatMap(_.join)

    // This remains set in the original fiber
    _ <- ZIO.succeed {
      println(s"Back in main fiber: request ID = ${requestIdThreadLocal.get()}")
      // This prints "request-001"
    }
  } yield ()

  override def run: ZIO[Any, Any, Unit] = {
    println("=== ThreadLocal Problem in Async Code ===\n")
    problemExample
  }
}
