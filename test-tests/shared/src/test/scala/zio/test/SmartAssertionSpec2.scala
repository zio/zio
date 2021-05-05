package zio.test

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
 *   - Don't delete hasFields chained onto a literal constructor. Basically, fix IsConstructor
 * √ Improve rendering for all the existing assertions
 * √ conjunction/disjunction/negation, break apart at top level in macro.
 * - Add all the methods we want
 * - Add a prose, human-readable error message for assertions (instead of 'does not satisfy hasAt(0)')
 * - Diff Stuff. Strings, case classes, maps, anything. User customizable.
 * - exposing bugs. try to break in as many ways as possible, and add helpful error messages
 *   √ how to handle multi-statement blocks
 */

object SmartAssertionSpec2 extends ZIOBaseSpec {
  def spec = suite("SMART ASSERTION")(
    test("contains array") {
//      val _ = (
//        zio.test.Assertion
//          .hasField(
//            "toList",
//            ((x: scala.collection.mutable.ArraySeq.ofInt) => x.toList),
//            zio.test.Assertion.contains(10).withCode(".contains(10)")
//          )
//          .withCode(".toList")
//      )
      assert(Array(1, 2, 8, 4, 8, 9, 1).toList.contains(10))
    }
  )
}
