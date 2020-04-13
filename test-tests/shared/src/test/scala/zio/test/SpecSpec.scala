package zio.test

import zio.test.Assertion.{ equalTo, isFalse, isTrue }
import zio.test.TestAspect.{ ifEnvSet, only }
import zio.test.TestUtils._
import zio.test.environment.TestEnvironment
import zio.{ Has, NeedsEnv, Ref, ZIO, ZLayer }

object SpecSpec extends ZIOBaseSpec {

  type Module = Has[Module.Service]

  object Module {
    trait Service
  }

  val layer = ZLayer.succeed(new Module.Service {})

  def spec = suite("SpecSpec")(
    suite("provideLayerShared")(
      testM("gracefully handles fiber death") {
        implicit val needsEnv = NeedsEnv
        val spec = suite("Suite1")(
          test("Test1") {
            assert(true)(isTrue)
          }
        ).provideLayerShared(ZLayer.fromEffectMany(ZIO.dieMessage("everybody dies")))
        for {
          _ <- execute(spec)
        } yield assertCompletes
      },
      testM("does not acquire the environment if the suite is ignored") {
        val spec = suite("Suite1")(
          testM("Test1") {
            assertM(ZIO.accessM[Has[Ref[Boolean]]](_.get[Ref[Boolean]].get))(isTrue)
          },
          testM("another test") {
            assertM(ZIO.accessM[Has[Ref[Boolean]]](_.get[Ref[Boolean]].get))(isTrue)
          }
        )
        for {
          ref    <- Ref.make(true)
          layer  = ZLayer.fromEffect(ref.set(false).as(ref))
          _      <- execute(spec.provideCustomLayerShared(layer) @@ ifEnvSet("foo"))
          result <- ref.get
        } yield assert(result)(isTrue)
      },
      testM("is not interfered with by test level failures") {
        val spec = suite("some suite")(
          test("failing test") {
            assert(1)(Assertion.equalTo(2))
          },
          test("passing test") {
            assert(1)(Assertion.equalTo(1))
          },
          testM("test requires env") {
            assertM(ZIO.access[Has[Int]](_.get[Int]))(Assertion.equalTo(42))
          }
        ).provideLayerShared(ZLayer.succeed(43))
        for {
          executedSpec <- execute(spec)
          successes    <- executedSpec.countTests(_.isRight)
          failures     <- executedSpec.countTests(_.isLeft)
        } yield assert(successes)(equalTo(1)) && assert(failures)(equalTo(2))
      }
    ),
    suite("only")(
      testM("ignores all tests except one tagged") {
        val spec = suite("root suite")(
          suite("failing suite")(
            test("failing test") {
              assert(1)(equalTo(2))
            }
          ),
          suite("passing suite")(
            test("passing test") {
              assert(1)(equalTo(1))
            } @@ only
          )
        )
        assertM(succeeded(spec))(isTrue)
      },
      testM("ignores all tests except ones in the tagged suite") {
        val spec = suite("root suite")(
          suite("failing suite")(
            test("failing test") {
              assert(1)(equalTo(2))
            }
          ),
          suite("passing suite")(
            test("passing test") {
              assert(1)(equalTo(1))
            }
          ) @@ only
        )
        assertM(succeeded(spec))(isTrue)
      },
      testM("runs everything if no tests are tagged") {
        val spec = suite("root suite")(
          suite("failing suite")(
            test("failing test") {
              assert(1)(equalTo(2))
            }
          ),
          suite("passing suite")(
            test("passing test") {
              assert(1)(equalTo(1))
            }
          )
        )
        assertM(succeeded(spec))(isFalse)
      }
    ),
    suite("provideCustomLayer")(
      testM("provides the part of the environment that is not part of the `TestEnvironment`") {
        for {
          _ <- ZIO.environment[TestEnvironment]
          _ <- ZIO.environment[Module]
        } yield assertCompletes
      }.provideCustomLayer(layer)
    ),
    suite("provideLayer")(
      testM("does not have early initialization issues") {
        for {
          _ <- ZIO.environment[Module]
        } yield assertCompletes
      }.provideLayer(layer)
    )
  )
}
