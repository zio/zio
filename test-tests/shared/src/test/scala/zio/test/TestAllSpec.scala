package zio.test

import zio.test.Assertion._
import zio.{ZIO, ZManaged}

object TestAllSpec extends ZIOBaseSpec {
  val gen: Gen[Any, Int] = Gen.fromIterable(List(1, 2, 3))
  val spec: ZSpec[Environment, Failure] = suite("Parameterized tests")(
    testAllM("Runs a test for generated value")(gen) { value =>
      val _ = value // do something with value
      ZIO.succeedNow(assertCompletes)
    },
    suite("testAllM")(
      testM("testAllM produces a given number of tests") {
        val spec = testAllM("tests")(gen)(_ => ZIO.succeedNow(assertCompletes))
        assertM(spec.countTests(_ => true).useNow)(equalTo(3))
      },
      testM("testAllM creates a label containing the parameter") {
        val spec = testAllM("tests")(gen)(_ => ZIO.succeedNow(assertCompletes))
        assertM(testLabels(spec).useNow)(
          hasSameElements(
            List("tests 1", "tests 2", "tests 3")
          )
        )
      }
    )
  )

  private def testLabels[R, E](spec: ZSpec[R, E]) =
    spec.fold[ZManaged[R, Any, Vector[String]]] {
      case Spec.SuiteCase(_, specs, _) => specs.flatMap(ZManaged.collectAll(_).map(_.flatten))
      case Spec.TestCase(label, _, _)  => ZManaged.succeedNow(Vector(label))
    }

}
