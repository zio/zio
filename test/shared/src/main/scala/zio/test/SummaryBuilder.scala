package zio.test

object SummaryBuilder {
  def buildSummary[E](executedSpec: ExecutedSpec[E]): Summary = {
    val success  = executedSpec.countSuccesses
    val fail     = executedSpec.countFailures
    val ignore   = executedSpec.countIgnored
    val failures = executedSpec.failures
    val rendered = failures
      .flatMap(DefaultTestReporter.render(_, TestAnnotationRenderer.silent))
      .flatMap(_.rendered)
      .mkString("\n")
    Summary(success, fail, ignore, rendered)
  }
}
