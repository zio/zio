package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace

sealed trait AssertionResult { self =>
  import AssertionResult._

  def genFailureDetails: Option[GenFailureDetails]

  def label(label: String): AssertionResult =
    self match {
      case result: FailureDetailsResult =>
        result.copy(failureDetails = result.failureDetails.label(label))
      case result: AssertionResult.TraceResult =>
        result.copy(label = Some(label))
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
  case class TraceResult(
    trace: TestTrace[Boolean],
    genFailureDetails: Option[GenFailureDetails] = None,
    label: Option[String] = None
  ) extends AssertionResult
}
