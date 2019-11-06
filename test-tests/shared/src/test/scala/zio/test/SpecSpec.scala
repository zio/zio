package zio.test

import zio.ZManaged
import zio.test.Assertion.{equalTo, isFalse, isTrue}
import zio.test.TestOnlySpecData._
import zio.test.TestUtils.{execute, succeeded}

object SpecSpec
    extends ZIOBaseSpec(
      suite("SpecSpec")(
        suite("SpecSpec")(
          testM("provideManagedShared gracefully handles fiber death") {
            import zio.NeedsEnv.needsEnv
            val spec = suite("Suite1")(
              test("Test1") {
                assert(true, isTrue)
              }
            ).provideManagedShared(ZManaged.dieMessage("everybody dies"))
            for {
              _ <- execute(spec)
            } yield assertCompletes
          }
        ),
        suite(".only(pattern)")(
          testM("ignores all tests except one matching the given label") {
            zio.test.checkM(genSuite) { spec =>
              for {
                passed1 <- succeeded(spec.only(passingTest))
                passed2 <- succeeded(spec.only(failingTest))
              } yield assert(passed1, isTrue) && assert(passed2, isFalse)
            }
          },
          testM("ignores all tests except ones in the suite matching the given label") {
            zio.test.checkM(genSuite) { spec =>
              for {
                passed1 <- succeeded(spec.only(passingSuite))
                passed2 <- succeeded(spec.only(failingSuite))
              } yield assert(passed1, isTrue) && assert(passed2, isFalse)
            }
          },
          testM("runs everything if root suite label given") {
            zio.test.checkM(genSuite) { spec =>
              for {
                passed <- succeeded(spec.only(rootSuite))
              } yield assert(passed, isFalse)
            }
          }
        )
      )
    )

object TestOnlySpecData {
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
