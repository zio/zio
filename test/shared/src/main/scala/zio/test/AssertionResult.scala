package zio.test

sealed trait AssertionResult { self =>
  import AssertionResult._

  def genFailureDetails: Option[GenFailureDetails]

  def label(label: String): AssertionResult =
    self match {
      case result: FailureDetailsResult =>
        result.copy(failureDetails = result.failureDetails.label(label))
    }

  def setGenFailureDetails(details: GenFailureDetails): AssertionResult =
    self match {
      case result: FailureDetailsResult =>
        result.copy(genFailureDetails = Some(details))
    }
}

object AssertionResult {
  case class FailureDetailsResult(failureDetails: FailureDetails, genFailureDetails: Option[GenFailureDetails] = None)
      extends AssertionResult
}
