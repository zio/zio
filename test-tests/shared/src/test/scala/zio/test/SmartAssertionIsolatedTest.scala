package zio.test

import zio.Chunk
import zio.test.AssertionSyntax.{AssertionOps, EitherAssertionOps}
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

  sealed trait NiceError extends Throwable

  case class CoolError() extends NiceError
  case class BadError()  extends NiceError

  case class Company(people: List[Person])

  case class Person(age: Int, name: String, nickname: Option[String], children: List[Int] = List()) {
    def boom: Int = throw new Error("BOOM")
  }

  val someColor: Color = Red("hello")

  def spec: ZSpec[Annotations, Any] = suite("SmartAssertionSpec")(
    test("asInstanceOf") {
      val list = List(8, 9, 8, 10, 13)
      assert(list.exists(number => number * 2 == 16))
    }
  ) @@ TestAspect.identity

}

//object AssertExamples {
//  //  def optionExample = {
//  //    val option = Assert.succeed(Option.empty[Int]).label("maybeInt") >>>
//  //      Assert.get[Int] >>> Assert.fromFunction((_: Int) > 10).label(" > 10")
//  //    Assert.run(option, Right(()))
//  //  }
//  //
//  //  lazy val throwingExample = {
//  //    val a = Assert.succeed[Int](10).label("10") >>>
//  //      Assert.fromFunction((_: Int) + 10).label(" + 10") >>>
//  //      Assert.fromFunction((_: Any) => throw new Error("BANG")).label("BOOM") >>>
//  //      Assert.fromFunction((_: Int) % 2 == 0).label(" % 2 == 0") >>>
//  //      Assert.fromFunction((_: Boolean) == true).label(" == true") >>>
//  //      Assert.throws
//  //
//  //    Assert.run(a, Right(10))
//  //  }
//
//  lazy val booleanLogic = {
//    val ten   = 10
//    val hello = "hello"
//
//
////    val b0 = assertZ(company.people.head.nickname.$.getRight)
//
////    val b         = assertZ(ten >= 13 && hello.length == 8 && hello.length <= -130)
//    val assertion = a // && b
//
//    var result = Arrow.run(assertion.arrow, Right(()))
//    result = Trace.prune(result, false).getOrElse(Trace.Node(Result.Succeed(true)))
//    result
//  }
//
//  def main(args: Array[String]): Unit = {
//    val result  = booleanLogic
//    val failure = FailureCase.fromTrace(result)
//    println("")
//    println(
//      failure.map { f =>
//        FailureCase.renderFailureCase(f).mkString("\n")
//      }.mkString("\n\n")
//    )
//  }
//}
