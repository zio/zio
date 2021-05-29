package zio.test

sealed trait AssertionResult { self =>
  import AssertionResult._

  def genFailureDetails: Option[GenFailureDetails]

  def label(label: String): AssertionResult = {
    println(s"label ${label}")
    self match {
      case result: FailureDetailsResult =>
        result.copy(failureDetails = result.failureDetails.label(label))
      case result: AssertionResult.TraceResult =>
        result
    }
  }

  def setGenFailureDetails(details: GenFailureDetails): AssertionResult =
    self match {
      case result: FailureDetailsResult =>
        result.copy(genFailureDetails = Some(details))
      case result: TraceResult =>
        result.copy(genFailureDetails = Some(details))
    }
}

object AssertionResult {
  case class FailureDetailsResult(failureDetails: FailureDetails, genFailureDetails: Option[GenFailureDetails] = None)
      extends AssertionResult
  case class TraceResult(trace: Trace[Boolean], genFailureDetails: Option[GenFailureDetails] = None)
      extends AssertionResult
}
