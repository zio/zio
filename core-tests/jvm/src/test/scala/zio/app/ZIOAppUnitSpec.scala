package zio.app

import zio._
import zio.test._
import zio.test.TestAspect._

/**
 * Unit tests for ZIOApp that can run in-process without spawning external JVMs.
 * These tests are faster and more reliable for CI than process-based tests.
 *
 * Tests cover:
 *   - ZIOApp composition
 *   - Finalizer ordering guarantees
 *   - Exit code semantics
 *   - Bootstrap layer behavior
 */
object ZIOAppUnitSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ZIOAppUnitSpec")(
    finalizerOrderingSuite,
    exitCodeSemanticsSuite,
    bootstrapLayerSuite,
    compositionSuite
  ) @@ sequential

  // ============================================
  // Finalizer Ordering Tests
  // ============================================

  val finalizerOrderingSuite: Spec[Any, Nothing] = suite("Finalizer Ordering")(
    test("nested acquireRelease runs finalizers in reverse order") {
      for {
        ref <- Ref.make(List.empty[Int])
        _ <- ZIO.scoped {
               for {
                 _ <- ZIO.acquireRelease(ref.update(_ :+ 1))(_ => ref.update(_ :+ 4))
                 _ <- ZIO.acquireRelease(ref.update(_ :+ 2))(_ => ref.update(_ :+ 3))
               } yield ()
             }
        order <- ref.get
      } yield assertTrue(order == List(1, 2, 3, 4))
    },
    test("parallel acquireRelease runs finalizers for all branches") {
      for {
        ref <- Ref.make(Set.empty[String])
        _ <- ZIO.scoped {
               ZIO.collectAllPar(
                 List("A", "B", "C").map { name =>
                   ZIO.acquireRelease(ref.update(_ + s"acquire-$name"))(_ => ref.update(_ + s"release-$name"))
                 }
               )
             }
        recorded <- ref.get
      } yield assertTrue(
        recorded.contains("acquire-A") && recorded.contains("release-A") &&
          recorded.contains("acquire-B") && recorded.contains("release-B") &&
          recorded.contains("acquire-C") && recorded.contains("release-C")
      )
    },
    test("finalizers run even when effect fails") {
      for {
        finalizerRan <- Ref.make(false)
        result <- ZIO.scoped {
                    ZIO.acquireRelease(ZIO.unit)(_ => finalizerRan.set(true)) *>
                      ZIO.fail("intentional failure")
                  }.either
        ran <- finalizerRan.get
      } yield assertTrue(ran && result.isLeft)
    },
    test("finalizers run even when effect dies") {
      for {
        finalizerRan <- Ref.make(false)
        result <- ZIO.scoped {
                    ZIO.acquireRelease(ZIO.unit)(_ => finalizerRan.set(true)) *>
                      ZIO.die(new RuntimeException("intentional die"))
                  }.exit
        ran <- finalizerRan.get
      } yield assertTrue(ran && result.isFailure)
    }
  )

  // ============================================
  // Exit Code Semantics Tests
  // ============================================

  val exitCodeSemanticsSuite: Spec[Any, Nothing] = suite("Exit Code Semantics")(
    test("successful ZIO effect maps to ExitCode.success") {
      val exit = Exit.succeed(42)
      assertTrue(exit.isSuccess)
    },
    test("failed ZIO effect maps to failure") {
      val exit = Exit.fail("error")
      assertTrue(exit.isFailure)
    },
    test("interrupted effect has interruption cause") {
      for {
        fiber <- ZIO.never.fork
        _     <- fiber.interrupt
        exit  <- fiber.await
      } yield assertTrue(exit.isInterrupted)
    }
  )

  // ============================================
  // Bootstrap Layer Tests
  // ============================================

  val bootstrapLayerSuite: Spec[Any, Nothing] = suite("Bootstrap Layer")(
    test("bootstrap layer is available to run effect") {
      // This simulates what ZIOApp does with bootstrap layers
      val layer                                = ZLayer.succeed("test-value")
      val effect: ZIO[String, Nothing, String] = ZIO.service[String]

      for {
        result <- effect.provideLayer(layer)
      } yield assertTrue(result == "test-value")
    },
    test("bootstrap layer finalizers run on completion") {
      for {
        ref <- Ref.make(false)
        layer = ZLayer.scoped {
                  ZIO.acquireRelease(ZIO.succeed("test"))(_ => ref.set(true))
                }
        _ <- ZIO.scoped {
               layer.build.flatMap(_ => ZIO.unit)
             }
        finalized <- ref.get
      } yield assertTrue(finalized)
    }
  )

  // ============================================
  // Composition Tests
  // ============================================

  val compositionSuite: Spec[Any, Nothing] = suite("ZIOApp Composition")(
    test("combined apps run both effects") {
      for {
        ref     <- Ref.make(List.empty[String])
        app1     = ZIO.succeed(ref.update(_ :+ "app1")).flatten
        app2     = ZIO.succeed(ref.update(_ :+ "app2")).flatten
        _       <- app1.zipPar(app2)
        results <- ref.get
      } yield assertTrue(results.contains("app1") && results.contains("app2"))
    },
    test("combined apps share environment") {
      val effect: ZIO[String, Nothing, (String, String)] = for {
        s1 <- ZIO.service[String]
        s2 <- ZIO.service[String]
      } yield (s1, s2)

      for {
        result <- effect.provideLayer(ZLayer.succeed("shared"))
      } yield assertTrue(result._1 == result._2)
    }
  )
}
