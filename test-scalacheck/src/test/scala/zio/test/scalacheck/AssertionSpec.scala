package zio.test.scalacheck

import org.scalacheck.{Gen, Prop, Properties}
import zio.Scope
import zio.test._

object AssertionSpec extends ZIOSpecDefault {
  object FailingProperties extends Properties("MyProperties") {
    property("PassingProp") = Prop.propBoolean(false)
  }

  object PassingProperties extends Properties("MyProperties") {
    property("FailingProp") = Prop.propBoolean(true)
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZIO assertions for ScalaCheck")(
      test("Prop passing")(Prop.propBoolean(true).assertZIO()),
      test("Prop failing")(Prop.propBoolean(false).assertZIO()) @@ TestAspect.failing,
      test("Prop failing includes shrunk arguments") {
        val result = Prop.forAll(Gen.const(0))((n: Int) => n > 0).assertZIO()
        result.failures match {
          case Some(trace) => assert(trace.getGenFailureDetails.fold(false)(_.shrunkenInput == 0))(isTrue)
          case None        => assert(false)(isTrue)
        }
      },
      test("Properties passing")(PassingProperties.assertZIO()),
      test("Properties failing")(FailingProperties.assertZIO()) @@ TestAspect.failing
    )
}
