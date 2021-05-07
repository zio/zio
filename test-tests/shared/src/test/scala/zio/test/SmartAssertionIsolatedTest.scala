package zio.test

import zio.test.SmartTestTypes._

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

  def spec = suite("SmartAssertionSpec")(
    test("nested access") {
      assert(company.users.forall(user => user.name == "COOOOOL"))
    },
    test("nested access") {
      val list = List(Some(10))
      assert(list.head.contains(30))
//      assert(company.users.forall(user => user.name == "COOOOOL"))
    }
//    test("nested accesss II") {
//      assert(company.users.map(_.name).withAssertion(Assertion.forall(Assertion.equalTo("BILLL"))))
//    }
  ) @@ TestAspect.ignore

}
