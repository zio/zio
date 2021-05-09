package zio.test

import zio.Chunk
import zio.duration.Duration
import zio.test.SmartTestTypes._
import zio.test.examples.Node

import java.time.LocalDateTime

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

  def debugNode(node: Node, indent: Int): Unit = {
    println(" " * indent + node.copy(fullCode = "", children = Nil))
    node.children.foreach(debugNode(_, indent + 2))
  }

  def spec: ZSpec[Annotations, Any] = suite("SmartAssertionSpec")(
    test("filterConstFalseResultsInEmptyChunk") {
      val listOfWords = List(None, Some("Howdy"), Some("Cool"))

      val zoom: Zoom[Any, Boolean] = assertZoom(listOfWords.head.get == "123")

      val (node, _) = zoom.run
      println(examples.render(node, List.empty, 0, true).mkString("\n"))

      assertCompletes
    }
  ) @@ TestAspect.identity

}
