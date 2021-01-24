package zio.test

object SummaryBuilder {
  def buildSummary[E](executedSpec: ExecutedSpec[E]): Summary = {
    val success = countTestResults(executedSpec) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
    val fail = countTestResults(executedSpec)(_.isLeft)
    val ignore = countTestResults(executedSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
    val failures = extractFailures(executedSpec)
    val rendered = failures
      .flatMap(DefaultTestReporter.render(_, TestAnnotationRenderer.silent, false))
      .flatMap(_.rendered)
      .mkString("\n")
    Summary(success, fail, ignore, rendered)
  }

  private def countTestResults[E](
    executedSpec: ExecutedSpec[E]
  )(pred: Either[TestFailure[E], TestSuccess] => Boolean): Int =
    executedSpec.fold[Int] {
      case ExecutedSpec.SuiteCase(_, counts) => counts.sum
      case ExecutedSpec.TestCase(_, test, _) => if (pred(test)) 1 else 0
    }

  private def extractFailures[E](executedSpec: ExecutedSpec[E]): Seq[ExecutedSpec[E]] =
    executedSpec.fold[Seq[ExecutedSpec[E]]] {
      case ExecutedSpec.SuiteCase(label, specs) =>
        val newSpecs = specs.flatten
        if (newSpecs.nonEmpty) Seq(ExecutedSpec(ExecutedSpec.SuiteCase(label, newSpecs))) else Seq.empty
      case c @ ExecutedSpec.TestCase(_, test, _) => if (test.isLeft) Seq(ExecutedSpec(c)) else Seq.empty
    }
}
