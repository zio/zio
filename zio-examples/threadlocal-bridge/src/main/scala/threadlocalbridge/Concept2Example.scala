package threadlocalbridge

import zio._

/** Title: Creating and Using ThreadLocalBridge
  * Description: This example shows how to use ThreadLocalBridge to safely manage
  * ThreadLocal values across ZIO fiber boundaries.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.Concept2Example"
  */
object Concept2Example extends ZIOAppDefault {

  // A Java ThreadLocal for storing user context
  val userContextThreadLocal: java.lang.ThreadLocal[String] =
    new java.lang.ThreadLocal[String] {
      override def initialValue(): String = "anonymous"
    }

  val exampleWithBridge: ZIO[Scope with ThreadLocalBridge, Nothing, Unit] = for {
    // Create a FiberRef linked to the ThreadLocal
    // The link function keeps ThreadLocal in sync with FiberRef changes
    userContextRef <- ThreadLocalBridge.makeFiberRef("user-alice")(
      value => userContextThreadLocal.set(value)
    )

    // Initial value is set
    _ <- ZIO.succeed(println(s"Main fiber: user = ${userContextThreadLocal.get()}"))

    // Use FiberRef.locally to temporarily change the value in a forked fiber
    // This maintains proper context inheritance
    _ <- userContextRef.locally("user-bob") {
      ZIO.succeed {
        println(s"Forked fiber: user = ${userContextThreadLocal.get()}")
        // This correctly shows "user-bob" thanks to FiberRef.locally
      }
    }.fork.flatMap(_.join)

    // Value remains unchanged in main fiber
    _ <- ZIO.succeed {
      println(s"Back in main fiber: user = ${userContextThreadLocal.get()}")
      // Still "user-alice"
    }

    // You can also use FiberRef.set for explicit changes
    _ <- userContextRef.set("user-charlie")
    _ <- ZIO.succeed(println(s"After set: user = ${userContextThreadLocal.get()}"))

    // Use FiberRef.locally to scope changes to a specific effect
    _ <- userContextRef.locally("user-diana") {
      for {
        _ <- ZIO.succeed(println(s"In locally block: user = ${userContextThreadLocal.get()}"))
        // Nested forked fiber inherits the locally-scoped value
        _ <- ZIO.succeed {
          println(s"Nested fiber (inherited): user = ${userContextThreadLocal.get()}")
        }.fork.flatMap(_.join)
      } yield ()
    }

    // After locally block, value reverts
    _ <- ZIO.succeed {
      println(s"After locally block: user = ${userContextThreadLocal.get()}")
      // Back to "user-charlie"
    }
  } yield ()

  override def run: ZIO[Any, Any, Unit] = {
    println("=== ThreadLocalBridge: Safe ThreadLocal Inheritance ===\n")
    ZIO.scoped {
      exampleWithBridge
    }.provideLayer(ThreadLocalBridge.live)
  }
}
