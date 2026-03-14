package zio.test

object AssertionSpec extends ZIOSpecDefault {

  private def renderFailure(result: TestResult): String = {
    def renderMessage(message: ErrorMessage): String =
      message.render(isSuccess = false).lines.map(_.fragments.map(_.text).mkString).mkString("\n")

    def loop(trace: TestTrace[Boolean]): String =
      trace match {
        case node: TestTrace.Node[Boolean] => renderMessage(node.message)
        case TestTrace.AndThen(_, right)   => loop(right)
        case TestTrace.And(left, right)    => s"${loop(left)}\n${loop(right)}"
        case TestTrace.Or(left, right)     => s"${loop(left)}\n${loop(right)}"
        case TestTrace.Not(inner)          => loop(inner)
      }

    loop(result.failures.get)
  }

  def spec = suite("AssertionSpec")(
    test("classic equalTo uses Diff rendering for strings") {
      val expected = "hello brave new world from the zio assertion diff renderer"
      val actual   = "hello bold new world from the zio assertion diff renderer"
      val failure  = renderFailure(Assertion.equalTo(expected).run(actual))

      assertTrue(
        failure.contains("There was a difference"),
        failure.contains("Expected"),
        failure.contains("Diff"),
        failure.contains("-expected"),
        failure.contains("+obtained")
      )
    },
    test("classic equalTo falls back to product diff when no Diff instance resolves") {
      final case class Example(left: Int, right: Int)

      val failure = renderFailure(Assertion.equalTo(Example(1, 2)).run(Example(1, 3)))

      assertTrue(failure.contains(".right : expected '2' got '3'"))
    }
  )
}
