package zio.test

import zio.test.SmartAssertionIsolatedTest.CoolError
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
      assertCompletes
    }
  ) @@ TestAspect.identity

}

object ZoomExexExample {
  def main(args: Array[String]): Unit = {
    val listOfWords = List(None, Some("Vanilla"), Some("Strawberry"), Some("Chocolate"))

    case class Thing() {
      def blowUp: Int = 18 //throw CoolError()
    }

    def blowUp: Int = throw CoolError()

    val thing = Thing()

    val a = zio.test.Zoom
      .succeed(listOfWords)
      .pos(0, 11)
      .>>>(
        zio.test.Zoom
          .forall(
            zio.test.Zoom
              .suspend((x$1: Option[String]) =>
                zio.test.Zoom
                  .succeed(x$1)
                  .pos(19, 20)
                  .>>>(zio.test.Zoom.isSome.pos(20, 24))
                  .>>>(zio.test.Zoom.zoom((a: String) => a.length()).pos(24, 31))
                  .>>>(zio.test.Zoom.equalTo(9).pos(31, 36))
              )
              .pos(19, 36)
          )
          .pos(11, 37)
      )
      .withCode("listOfWords.forall(_.get.length == 9)")

    type Assert[In, Out] = Zoom[In, Out]
    val Assert = Zoom

    val assertion = assert(listOfWords.forall(_.get.length == 9))

    val generated = (Assert.succeed(listOfWords).pos(0, 11) >>>
      Assert
        .forall(
          Assert
            .suspend((x$1: Option[String]) =>
              Assert.succeed(x$1).pos(19, 20) >>> Assert.isSome.pos(20, 24) >>>
                Assert.zoom((a: String) => a.length()).pos(24, 31) >>> Assert.equalTo(9).pos(31, 36)
            )
        )
        .pos(11, 37))
      .withCode("listOfWords.forall(_.get.length == 9)")

//    val zoom: Zoom[Any, Boolean] = assertZoom(thing.blowUp.throwsA[BadError])
//    val zoom: Zoom[Any, Boolean] = assertZoom(!(thing.blowUp > 10))
//    val zoom: Zoom[Any, Boolean] = assertZoom(thing.blowUp < 10)

    val (node, result) = zoom.run

    if (result.contains(true)) println("SUCCEooEoSS")
    else println(Node.render(node, List.empty, 0, true).mkString("\n"))
  }
}
