/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test

import zio.internal.stacktracer.SourceLocation
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Trace, UIO, ZIO}

/**
 * The assertion functions that need a macro: `assertTrue`, `assert`,
 * `assertZIO` and `typeCheck`, in a source file per Scala version. The
 * `zio.test` package object extends this trait, so `import zio.test._` brings
 * them into scope.
 */
trait CompileVariants {

  /**
   * Returns either `Right` if the specified string type checks as valid Scala
   * code or `Left` with an error message otherwise. The code is checked at
   * compile time, in the scope of the call.
   *
   * On Scala 2, the returned effect dies with a `RuntimeException` when the
   * string cannot be parsed or is not known at compile time. On Scala 3, a
   * parse error is returned as a `Left`, and a string that is not known at
   * compile time does not compile.
   *
   * {{{
   * import zio.test.Assertion._
   *
   * test("Int has no ++ method") {
   *   assertZIO(typeCheck("1 ++ 1"))(isLeft(anything))
   * }
   * }}}
   */
  final def typeCheck(code: String): UIO[Either[String, Unit]] =
    macro Macros.typeCheck_impl

  /**
   * Checks that every given Boolean expression is `true`, and returns a
   * [[TestResult]] that fails when one of them is `false`. This is a smart
   * assertion: a macro records the source code of each expression, so a failure
   * report can name the part that failed and the values it had, without a
   * separate [[Assertion]].
   *
   * Each expression is checked, also those after a `false` one. An expression
   * that throws an exception makes the result fail instead of throwing. At
   * least one expression is required. Inside `assertTrue`, the `is` extension
   * method opens a [[TestLens]] that looks into an `Option`, `Either`, `Exit`,
   * `Try` or `Cause`, as in `opt.is(_.some) == 42`.
   *
   * {{{
   * import zio.Ref
   *
   * test("updating a ref") {
   *   for {
   *     ref   <- Ref.make(0)
   *     _     <- ref.update(_ + 1)
   *     value <- ref.get
   *   } yield assertTrue(value == 1, value > 0)
   * }
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/assertions/smart-assertions/ Smart Assertions]]
   */
  def assertTrue(expr: Boolean, exprs: Boolean*): TestResult = macro SmartAssertMacros.assert_impl
  /**
   * Checks that `expr` is `true`. The one-expression form of `assertTrue`, with
   * the same behaviour as the form that takes several expressions.
   */
  def assertTrue(expr: Boolean): TestResult = macro SmartAssertMacros.assertOne_impl

  /**
   * Checks the assertion holds for the given value. The value is evaluated
   * inside the check, so an exception it throws becomes part of the result (see
   * `Assertion.throws`) instead of escaping. A macro records the source code of
   * the value and of the assertion for the failure report.
   *
   * {{{
   * import zio.test.Assertion._
   *
   * val result: TestResult = assert(List(1, 2, 3))(contains(2) && hasSize[Int](equalTo(3)))
   * // result.isSuccess == true
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/assertions/classic-assertions/ Classic Assertions]]
   */
  def assert[A](expr: => A)(assertion: Assertion[A]): TestResult =
    macro Macros.new_assert_impl

  /**
   * Checks the assertion holds for the given effectfully-computed value. The
   * returned effect fails when `effect` fails; to check a failure, assert on
   * `effect.exit` with `Assertion.fails`, `Assertion.dies` or
   * `Assertion.isInterrupted`.
   *
   * {{{
   * import zio.ZIO
   * import zio.test.Assertion._
   *
   * test("sum of a list") {
   *   assertZIO(ZIO.succeed(List(1, 2, 3)).map(_.sum))(equalTo(6))
   * }
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/assertions/classic-assertions/ Classic Assertions]]
   */
  def assertZIO[R, E, A](effect: ZIO[R, E, A])(assertion: Assertion[A])(implicit
    trace: Trace,
    sourceLocation: SourceLocation
  ): ZIO[R, E, TestResult] =
    Assertion.smartAssertZIO(assertion)(effect)

  private[zio] def showExpression[A](expr: => A): String = macro Macros.showExpression_impl
}

/**
 * Proxy methods to call package private methods from the macro
 */
object CompileVariants {

  /**
   * Checks `assertion` against `value`. A public entry point for
   * macro-generated code; use `assert` instead.
   */
  def newAssertProxy[A](value: => A, codeString: String, assertionString: String)(
    assertion: Assertion[A]
  )(implicit trace: Trace, sourceLocation: SourceLocation): TestResult =
    zio.test.assertImpl(value, Some(codeString), Some(assertionString))(assertion)

  /**
   * Checks `assertion` against the value of `effect`. A public entry point for
   * macro-generated code; use `assertZIO` instead.
   */
  def assertZIOProxy[R, E, A](effect: ZIO[R, E, A])(
    assertion: Assertion[A]
  )(implicit trace: Trace, sourceLocation: SourceLocation): ZIO[R, E, TestResult] =
    zio.test.assertZIOImpl(effect)(assertion)
}
