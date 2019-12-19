package zio.test

import zio.{ Ref, ZIO, ZManaged }
import zio.test.Assertion.{ equalTo, isFalse, isTrue }
import zio.test.TestAspect.ifEnvSet
import zio.test.TestUtils._

object SpecSpec extends ZIOBaseSpec {

  def spec = suite("SpecSpec")(
    suite("provideManagedShared")(
      testM("gracefully handles fiber death") {
        import zio.NeedsEnv.needsEnv
        val spec = suite("Suite1")(
          test("Test1") {
            assert(true, isTrue)
          }
        ).provideManagedShared(ZManaged.dieMessage("everybody dies"))
        for {
          _ <- execute(spec)
        } yield assertCompletes
      },
      testM("does not acquire the environment if the suite is ignored") {
        val spec = suite("Suite1")(
          testM("Test1") {
            assertM(ZIO.accessM[Ref[Boolean]](_.get), isTrue)
          },
          testM("another test") {
            assertM(ZIO.accessM[Ref[Boolean]](_.get), isTrue)
          }
        )
        for {
          ref <- Ref.make(true)
          _ <- execute {
                spec.provideManagedShared {
                  ZManaged.make(ref.set(false).as(ref))(_ => ZIO.unit)
                } @@ ifEnvSet("foo")
              }
          result <- ref.get
        } yield assert(result, isTrue)
      }
    ),
    suite("only")(
      testM("ignores all tests except one matching the given label") {
        checkM(genSuite) { spec =>
          for {
            passed1 <- isSuccess(spec.only(passingTest))
            passed2 <- isSuccess(spec.only(failingTest))
          } yield assert(passed1, isTrue) && assert(passed2, isFalse)
        }
      },
      testM("ignores all tests except ones in the suite matching the given label") {
        checkM(genSuite) { spec =>
          for {
            passed1 <- isSuccess(spec.only(passingSuite))
            passed2 <- isSuccess(spec.only(failingSuite))
          } yield assert(passed1, isTrue) && assert(passed2, isFalse)
        }
      },
      testM("runs everything if root suite label given") {
        checkM(genSuite) { spec =>
          for {
            passed <- isSuccess(spec.only(rootSuite))
          } yield assert(passed, isFalse)
        }
      }
    )
  )

  val failingTest  = "failing-test"
  val failingSuite = "failing-suite"
  val passingTest  = "passing-test"
  val passingSuite = "passing-suite"
  val rootSuite    = "root-suite"
  val genSuite = Gen.anyString zip Gen.anyString map {
    case (prefix, suffix) => mixedSpec(prefix, suffix)
  }
  def mixedSpec(prefix: String, suffix: String) = suite(prefix + rootSuite + suffix)(
    suite(prefix + failingSuite + suffix)(test(prefix + failingTest + suffix) {
      assert(1, equalTo(2))
    }),
    suite(prefix + passingSuite + suffix)(test(prefix + passingTest + suffix) {
      assert(1, equalTo(1))
    })
  )
}
