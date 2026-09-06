package threadlocalbridge

import zio._

/** Title: ThreadLocalBridge with Inheritance and Cleanup
  * Description: This example demonstrates how ThreadLocalBridge handles value
  * inheritance in nested fibers and resource cleanup.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.Concept3Example"
  */
object Concept3Example extends ZIOAppDefault {

  // A ThreadLocal for transaction IDs
  val transactionIdThreadLocal: java.lang.ThreadLocal[String] =
    new java.lang.ThreadLocal[String] {
      override def initialValue(): String = "tx-none"
    }

  val complexInheritanceExample: ZIO[Scope with ThreadLocalBridge, Nothing, Unit] = for {
    _ <- ZIO.succeed(println("=== Inheritance Chain Demo ===\n"))

    // Create a FiberRef linked to the ThreadLocal
    txRef <- ThreadLocalBridge.makeFiberRef("tx-parent-001")(
      value => transactionIdThreadLocal.set(value)
    )

    // Level 1: Main fiber has the transaction ID
    _ <- ZIO.succeed(println(s"Level 1 (main): transaction = ${transactionIdThreadLocal.get()}"))

    // Level 2: Fork a child fiber that inherits the value via FiberRef
    _ <- txRef.locally("tx-parent-001") {
      for {
        _ <- ZIO.succeed {
          println(s"Level 2 (child): transaction = ${transactionIdThreadLocal.get()} [inherited]")
        }

        // Level 3: Fork a grandchild fiber with a different value
        _ <- txRef.locally("tx-child-002") {
          ZIO.succeed {
            println(s"Level 3 (grandchild): transaction = ${transactionIdThreadLocal.get()} [scoped]")
          }
        }.fork.flatMap(_.join)

        // Back in level 2 - value is restored by locally block
        _ <- ZIO.succeed {
          println(s"Level 2 (back): transaction = ${transactionIdThreadLocal.get()} [restored]")
        }
      } yield ()
    }.fork.flatMap(_.join)

    // Back in level 1 - parent's value is unchanged
    _ <- ZIO.succeed {
      println(s"Level 1 (back): transaction = ${transactionIdThreadLocal.get()} [unchanged]")
    }
  } yield ()

  val multipleInheritanceExample: ZIO[Scope with ThreadLocalBridge, Nothing, Unit] = for {
    _ <- ZIO.succeed(println("\n=== Multiple Parallel Inherits Demo ===\n"))

    txRef <- ThreadLocalBridge.makeFiberRef("tx-parallel-root")(
      value => transactionIdThreadLocal.set(value)
    )

    _ <- ZIO.succeed(println(s"Root: transaction = ${transactionIdThreadLocal.get()}"))

    // Run multiple parallel fibers, each with their own scoped values
    _ <- ZIO.collectAllPar(
      List(
        txRef.locally("tx-fiber-1") {
          ZIO.succeed {
            Thread.sleep(100) // Simulate work
            println(s"Fiber 1: transaction = ${transactionIdThreadLocal.get()}")
          }
        },
        txRef.locally("tx-fiber-2") {
          ZIO.succeed {
            Thread.sleep(50)
            println(s"Fiber 2: transaction = ${transactionIdThreadLocal.get()}")
          }
        },
        txRef.locally("tx-fiber-3") {
          ZIO.succeed {
            Thread.sleep(150)
            println(s"Fiber 3: transaction = ${transactionIdThreadLocal.get()}")
          }
        }
      )
    ).unit

    // Root value is restored after all fibers
    _ <- ZIO.succeed {
      println(s"Root (after parallel): transaction = ${transactionIdThreadLocal.get()}")
    }
  } yield ()

  override def run: ZIO[Any, Any, Unit] = {
    val combined = for {
      _ <- complexInheritanceExample
      _ <- multipleInheritanceExample
    } yield ()

    ZIO.scoped {
      combined
    }.provideLayer(ThreadLocalBridge.live)
  }
}
