package zio.test

sealed trait AssertionResult { self =>
  import AssertionResult._

  def label(label: String): AssertionResult = self match {
    case FailureDetailsResult(failureDetails) =>
      FailureDetailsResult(failureDetails.label(label))
    case result: AssertionResult.TraceResult =>
      result
  }

  def setGenFailureDetails(details: GenFailureDetails): AssertionResult =
    self match {
      case FailureDetailsResult(failureDetails) =>
        FailureDetailsResult(failureDetails.copy(gen = Some(details)))
      case result: AssertionResult.TraceResult =>
        // TODO: Add Gen Details?
        result
    }
}

object AssertionResult {
  case class FailureDetailsResult(failureDetails: FailureDetails) extends AssertionResult
  case class TraceResult(trace: Trace[Boolean])                   extends AssertionResult
}
