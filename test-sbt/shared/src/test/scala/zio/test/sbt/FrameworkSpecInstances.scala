package zio.test.sbt

import zio.test._
import zio.{Scope, ZIO, ZLayer, durationInt}

import java.util.concurrent.atomic.AtomicInteger

object FrameworkSpecInstances {

  val counter = new AtomicInteger(0)

  lazy val sharedLayer: ZLayer[Any, Nothing, Int] =
    ZLayer.fromZIO(ZIO.succeed(counter.getAndUpdate(value => value + 1)))

  private def numberedTest(specIdx: Int, suiteIdx: Int, testIdx: Int) =
    test(s"spec $specIdx suite $suiteIdx test $testIdx") {
      assertCompletes
    }

  object SimpleSpec extends ZIOSpec[Int] {
    override def bootstrap = ZLayer.succeed(1)

    def spec =
      suite("simple suite")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1)
      ) @@ TestAspect.parallel
  }

  object CombinedWithPlusSpec extends ZIOSpecDefault {
    override def spec = suite("spec A") {
      test("successful test") {
        assertTrue(true)
      } +
        test("failing test") {
          assertTrue(false)
        } @@ TestAspect.ignore
    }
  }

  object CombinedWithCommasSpec extends ZIOSpecDefault {
    override def spec = suite("spec A")(
      test("successful test") {
        assertTrue(true)
      },
      test("failing test") {
        assertTrue(false)
      } @@ TestAspect.ignore
    )
  }

  object TimeOutSpec extends ZIOSpecDefault {
    def spec =
      suite("simple suite")(
        test("slow test")(
          for {
            _ <- ZIO.sleep(3.seconds)
          } yield assertCompletes
        )
      ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(1.second)
  }

  object RuntimeExceptionSpec extends ZIOSpecDefault {
    def spec =
      suite("exploding suite")(
        test("boom") {
          if (true) throw new RuntimeException("Good luck ;)") else ()
          assertCompletes
        }
      )
  }

  object RuntimeExceptionDuringLayerConstructionSpec extends ZIOSpec[Int] {
    // Note: BindException does not exist on Scala.js; using IllegalArgumentException instead.
    override val bootstrap = ZLayer.fromZIO(
      ZIO.attempt(throw new IllegalArgumentException("Other Kafka container already grabbed your port"))
    )

    def spec =
      suite("kafka suite")(
        test("does stuff with a live kafka cluster") {
          assertCompletes
        }
      )
  }

  object Spec1UsingSharedLayer extends ZIOSpec[Int] {
    override def bootstrap = sharedLayer

    def spec =
      suite("suite with shared layer")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1),
        numberedTest(specIdx = 1, suiteIdx = 1, 2),
        numberedTest(specIdx = 1, suiteIdx = 1, 3),
        numberedTest(specIdx = 1, suiteIdx = 1, 4)
      ) @@ TestAspect.parallel
  }

  object Spec2UsingSharedLayer extends ZIOSpec[Int] {
    override def bootstrap = sharedLayer

    def spec =
      test("test completes with shared layer 2") {
        assertCompletes
      }
  }

  object MultiLineSharedSpec extends ZIOSpecDefault {
    def spec = test("multi-line test") {
      assert("Hello,\nWorld!")(Assertion.equalTo("Hello, World!"))
    }
  }

  object SimpleFailingSharedSpec extends ZIOSpecDefault {
    def spec: Spec[Any, TestFailure[Any]] = suite("some suite")(
      test("failing test") {
        assert(1)(Assertion.equalTo(2))
      },
      test("passing test") {
        assert(1)(Assertion.equalTo(1))
      },
      test("ignored test") {
        assert(1)(Assertion.equalTo(2))
      } @@ TestAspect.ignore
    )
  }

  object TagsSpec extends ZIOSpecDefault {
    def spec: Spec[Any, TestFailure[Any]] = suite("tag suite")(
      test("integration test") {
        assertCompletes
      }.annotate(TestAnnotation.tagged, Set("IntegrationTest")),
      test("unit test") {
        assert(1)(Assertion.equalTo(1))
      }.annotate(TestAnnotation.tagged, Set("UnitTest"))
    )
  }

  object TagsSpecWithFailingEnv extends ZIOSpecDefault {
    // failing test with `provide`
    private val suiteWithFailingProvideEnv =
      (
        suite("suite with tag and failing `provide` env")(
          test("should not be executed - 0")(assertTrue(false))
        ).provide(ZLayer(ZIO.fail(new RuntimeException("should not be called - 0"))))
      ) @@ TestAspect.tag("NotExecuted")

    // succeeding test with `provide`
    private val suiteWithValidProvideEnv =
      (
        suite("suite with tag and valid `provide` env")(
          test("should be executed - 1")(ZIO.serviceWith[Int](i => assertTrue(i == 1)))
        ).provide(ZLayer.succeed(1))
      ) @@ TestAspect.tag("Executed")

    // failing test with `provideShared`
    private val suiteWithFailingProvideSharedEnv =
      (
        suite("suite with tag and failing `provideShared` env")(
          test("should not be executed - 2")(assertTrue(false))
        ).provideShared(ZLayer(ZIO.fail(new RuntimeException("should not be called - 2"))))
      ) @@ TestAspect.tag("NotExecuted")

    // succeeding test with `provideShared`
    private val suiteWithValidProvideSharedEnv =
      (
        suite("suite with tag and valid `provideShared` env")(
          test("should be executed - 3")(ZIO.serviceWith[Int](i => assertTrue(i == 1)))
        ).provideShared(ZLayer.succeed(1))
      ) @@ TestAspect.tag("Executed")

    // failing test with `provideLayerShared`
    private val suiteWithFailingProvideLayerSharedEnv =
      (
        suite("suite with tag and failing `provideShared` env")(
          test("should not be executed - 4")(assertTrue(false))
        ).provideLayerShared(ZLayer(ZIO.fail(new RuntimeException("should not be called - 4"))))
      ) @@ TestAspect.tag("NotExecuted")

    // succeeding test with `provideLayerShared`
    private val suiteWithValidProvideLayerSharedEnv =
      (
        suite("suite with tag and valid `provideShared` env")(
          test("should be executed - 5")(ZIO.serviceWith[Int](i => assertTrue(i == 1)))
        ).provideLayerShared(ZLayer.succeed(1))
      ) @@ TestAspect.tag("Executed")

    // failing test with `provideSomeLayerShared`
    private val suiteWithFailingProvideSomeLayerSharedEnv =
      (
        suite("suite with tag and failing `provideShared` env")(
          test("should not be executed - 6")(assertTrue(false))
        ).provideSomeLayerShared[Int](
          ZLayer.fail(new RuntimeException("should not be called - 6")): ZLayer[Int, Throwable, Any]
        )
      ).provide(ZLayer.succeed(1)) @@ TestAspect.tag("NotExecuted")

    // succeeding test with `provideSomeLayerShared`
    private val suiteWithValidProvideSomeLayerSharedEnv =
      (
        suite("suite with tag and valid `provideShared` env")(
          test("should be executed - 7")(ZIO.serviceWith[Int](i => assertTrue(i == 1)))
        ).provideSomeLayerShared[Int](ZLayer.service[Int])
      ).provide(ZLayer.succeed(1)) @@ TestAspect.tag("Executed")

    private val suiteWithTestWithFailingProvideEnv =
      suite("suite with tags and with tests having invalid env")(
        (
          test("should not be executed - 8") {
            assertTrue(false)
          }.provide(ZLayer(ZIO.fail(new RuntimeException("should not be called - 8"))))
        ) @@ TestAspect.tag("NotExecuted"),
        test("should be executed - 9") {
          assertTrue(true)
        } @@ TestAspect.tag("Executed")
      )

    override def spec: Spec[TestEnvironment with Scope, Any] =
      suite("tag suite with failing env")(
        suiteWithFailingProvideEnv,
        suiteWithValidProvideEnv,
        suiteWithFailingProvideSharedEnv,
        suiteWithValidProvideSharedEnv,
        suiteWithFailingProvideLayerSharedEnv,
        suiteWithValidProvideLayerSharedEnv,
        suiteWithValidProvideSomeLayerSharedEnv,
        suiteWithValidProvideSomeLayerSharedEnv,
        suiteWithFailingProvideSomeLayerSharedEnv,
        suiteWithTestWithFailingProvideEnv
      )
  }

  object NestedSpec extends ZIOSpecDefault {
    def spec: Spec[Any, TestFailure[Any]] = suite("outer")(
      suite("inner")(
        test("test") {
          assert(1)(Assertion.equalTo(1))
        }
      )
    )
  }
}
