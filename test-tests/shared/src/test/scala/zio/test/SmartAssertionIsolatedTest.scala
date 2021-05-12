package zio.test

import zio.Chunk
import zio.test.AssertionSyntax.AssertionOps
import zio.test.SmartAssertionIsolatedTest.{BadError, CoolError}
import zio.test.SmartTestTypes._

/**
 * - Scala 3
 * - Refactor to make Scala 3 easy.
 * - Create some data structure that allows us to list out
 *   - Method Name, Method
 * - Fix up rendering issues.
 *   √ Do not list value for literals
 *   √ Add spaces around infix with code show.
 *   √ Special case apply.
 *   √ Use the actual written showCode(expr) for the withField for the macro code
 *   - Fix IsConstructor
 * √ Improve rendering for all the existing assertions
 * √ conjunction/disjunction/negation, break apart at top level in macro.
 * √ handle exceptions thrown by methods in `assert`
 * √ Add a prose, human-readable error message for assertions (instead of 'does not satisfy hasAt(0)')
 * - Add all the methods we want
 *   - right.get (on an Either)
 *   - toOption.get (on an Either)
 *   - forall
 * - Diff Stuff. Strings, case classes, maps, anything. User customizable.
 * - Exposing bugs. try to break in as many ways as possible, and add helpful error messages
 *   √ how to handle multi-statement blocks
 */

object SmartAssertionIsolatedTest extends ZIOBaseSpec {

  val company: Company = Company("Ziverge", List(User("Bobo", List.tabulate(2)(n => Post(s"Post #$n")))))

  sealed trait NiceError extends Throwable

  case class CoolError() extends NiceError
  case class BadError()  extends NiceError

  def spec: ZSpec[Annotations, Any] = suite("SmartAssertionSpec")(
    test("filterConstFalseResultsInEmptyChunk") {
      AssertExamples.main(Array())
      assertCompletes
    }
  ) @@ TestAspect.identity

}

object ExampleZoo {
  def main(args: Array[String]): Unit = {
    val listOfWords = List(None, Some("Vanilla"), Some("Strawberry"), Some("Chocolate"))

    case class Thing() {
      def blowUp: Int = 11 // throw CoolError()
    }

    def blowUp: Int = throw CoolError()

    val thing = Thing()
//    val zoom = assertZoom(listOfWords.forall(word => word.isEmpty && word.get.length == 9))

//    val zoom: Zoom[Any, Boolean] = assertZoom(thing.blowUp.throws.getMessage.length < 5)

    val num = 10
//    val zoom: Zoom[Any, Boolean] =
//      assertZoom((thing.blowUp > 10 && listOfWords(2).get == "Nice") || !(num == 10)) // && listOfWords.length == 3)
//    val zoom: Zoom[Any, Boolean] =
//      val assertZoom(thing.blowUp < 10 || (listOfWords.isEmpty && thing.blowUp < 10) && false)
    val age = 30

    val zoom = assertZoom(age > 40)

    val result = Assert.run(zoom, Right(()))
//    Node.debug(node)
    println(result)
//    println("")

//    if (result.contains(true)) println("SUCCEooEoSS")
//    else println(Node.render(node, Chunk.empty, 0, true).mkString("\n"))
  }
}
