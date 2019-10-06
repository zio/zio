package zio.test

object SummaryBuilder {
  def buildSummary[L, E, S](executedSpec: ExecutedSpec[L, E, S]): String =
    extractFailures(executedSpec)
      .map(_.mapLabel(_.toString))
      .flatMap(DefaultTestReporter.render(_))
      .flatMap(_.rendered)
      .mkString("\n")

  private def extractFailures[L, E, S](executedSpec: ExecutedSpec[L, E, S]): Seq[ExecutedSpec[L, E, S]] = {
    def isFailure(testCase: Spec.TestCase[L, Either[TestFailure[E], TestSuccess[S]]]): Boolean =
      testCase.test.isLeft

    def hasFailures(suite: Spec.SuiteCase[L, ExecutedSpec[L, E, S]]): Boolean =
      suite.specs.exists(_.caseValue match {
        case s @ Spec.SuiteCase(_, _, _) => hasFailures(s)
        case t @ Spec.TestCase(_, _)     => isFailure(t)
      })

    def loop(current: ExecutedSpec[L, E, S], acc: Seq[ExecutedSpec[L, E, S]]): Seq[ExecutedSpec[L, E, S]] =
      current.caseValue match {
        case suite @ Spec.SuiteCase(_, specs, _) if hasFailures(suite) =>
          acc :+ Spec(suite.copy(specs = specs.flatMap(loop(_, Vector.empty))))
        case t @ Spec.TestCase(_, _) if isFailure(t) => acc :+ current
        case _                                       => acc
      }

    loop(executedSpec, Vector.empty)
  }
}
