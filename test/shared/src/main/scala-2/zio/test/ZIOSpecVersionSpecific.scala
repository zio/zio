package zio.test

private[test] trait ZIOSpecVersionSpecific[R] {
  // SCALA 2

  /**
   * Builds a suite labeled `name` from a block of code. Each expression in the
   * block whose type is a `Spec` (a `test`, or a nested `suiteAll` or `suite`)
   * becomes a member of the suite, in the order written. The other statements,
   * such as `val`, `def` and `case class` definitions, stay in the block and
   * are visible to the tests after them.
   *
   * {{{
   * object GreetingSpec extends ZIOSpecDefault {
   *   def spec =
   *     suiteAll("greetings") {
   *       val greeting = "hello"
   *
   *       test("starts with h") {
   *         assertTrue(greeting.startsWith("h"))
   *       }
   *
   *       suiteAll("nested") {
   *         test("ends with o") {
   *           assertTrue(greeting.endsWith("o"))
   *         }
   *       }
   *     }
   * }
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/test-hierarchies-and-organization/ Test Hierarchies and Organization]]
   */
  def suiteAll(name: String)(spec: Any): Spec[Nothing, Any] =
    macro SmartSpecMacros.suiteImpl
}
