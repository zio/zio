package zio.internal

import zio.ZIOBaseSpec
import zio.internal.CleanCodePrinterSpec.A.B
import zio.internal.CleanCodePrinterSpec.A.B.C
import zio.internal.macros.MacroUnitTestUtils.showTree
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._
import zio.test.AssertionM.Render.param
import zio.test._

object CleanCodePrinterSpec extends ZIOBaseSpec {
  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(param(element))(_.removingAnsiCodes.contains(element))

  def spec: ZSpec[Environment, Failure] =
    suite("AutoLayerSpec")(
      suite(".showTree") {
        test("prints trees for expressions") {
          import nested.{Service => Nested}

          Seq(
            showTree(unaryFunction("hello"))  -> """unaryFunction("hello")""",
            showTree(regularVal)              -> "regularVal",
            showTree(`backtick enclosed`)     -> "`backtick enclosed`",
            showTree(Service.live)            -> "Service.live",
            showTree(Nested.live)             -> "Nested.live",
            showTree(AppliedObject.apply(10)) -> "AppliedObject(10)",
            showTree(AppliedObject.apply)     -> "AppliedObject",
            showTree(C.live)                  -> "C.live",
            showTree(B.C.live)                -> "C.live"
          ).map { case (a, b) => assert(a)(equalTo(b)) }
            .reduce(_ && _)
        }
      }
    ) @@ TestAspect.exceptDotty

  object A {
    object B {
      object C {
        def live = 0
      }
    }
  }
  object Service { def live = 0 }

  object AppliedObject {
    def apply(int: Int): Int = int
    def apply                = 0
  }

  val regularVal                            = 0
  val `backtick enclosed`                   = 0
  def unaryFunction(string: String): String = string
}

package nested {
  object Service {
    val live: Int = 3
  }
}
