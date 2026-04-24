package zio.test.render

import zio.Trace
import zio.internal.stacktracer.SourceLocation
import zio.test.ExecutionEvent.{SectionEnd, SectionStart, Test, TestStarted, TopLevelFlush}
import zio.test.TestAnnotationRenderer.LeafRenderer
import zio.test.render.ExecutionResult.{ResultType, Status}
import zio.test.render.LogLine.Message
import zio.test._

trait IntelliJRenderer extends TestRenderer {
  import IntelliJRenderer._

  override def renderEvent(event: ExecutionEvent, includeCause: Boolean)(implicit
    trace: Trace
  ): Seq[ExecutionResult] =
    event match {
      case SectionStart(labelsReversed, _, _) =>
        val depth = labelsReversed.length - 1
        labelsReversed.reverse match {
          case Nil => Seq.empty
          case nonEmptyList =>
            Seq(
              ExecutionResult.withoutSummarySpecificOutput(
                ResultType.Suite,
                label = nonEmptyList.last,
                Status.Started,
                offset = depth,
                annotations = Nil,
                lines = Nil,
                duration = None
              )
            )
        }

      case _: TestStarted => Nil

      case Test(labelsReversed, results, annotations, _, duration, _, _) =>
        val labels       = labelsReversed.reverse
        val initialDepth = labels.length - 1
        val (streamingOutput, summaryOutput) =
          testCaseOutput(labels, results, includeCause, annotations)

        Seq(
          ExecutionResult(
            ResultType.Test,
            labels.lastOption.getOrElse(""),
            results match {
              case Left(_) => Status.Failed
              case Right(value: TestSuccess) =>
                value match {
                  case TestSuccess.Succeeded(_) => Status.Passed
                  case TestSuccess.Ignored(_)   => Status.Ignored
                }
            },
            initialDepth,
            List(annotations),
            streamingOutput,
            summaryOutput,
            Some(duration)
          )
        )
      case runtimeFailure @ ExecutionEvent.RuntimeFailure(_, _, failure, _) =>
        val depth = event.labels.length
        failure match {
          case TestFailure.Assertion(result, annotations) =>
            Seq(renderAssertFailure(result, runtimeFailure.labels, depth, annotations))
          case TestFailure.Runtime(cause, _) =>
            Seq(renderRuntimeCause(cause, runtimeFailure.labels, depth, includeCause))
        }
      case SectionEnd(labelsReversed, _, _) =>
        val depth = labelsReversed.length - 1
        labelsReversed.reverse match {
          case Nil => Seq.empty
          case nonEmptyList =>
            Seq(
              ExecutionResult.withoutSummarySpecificOutput(
                ResultType.Suite,
                label = nonEmptyList.last,
                Status.Passed,
                offset = depth,
                List(TestAnnotationMap.empty),
                lines = List(fr(nonEmptyList.last).toLine),
                duration = None
              )
            )
        }
      case TopLevelFlush(_) =>
        Nil
    }

  override protected def renderOutput(results: Seq[ExecutionResult])(implicit trace: Trace): Seq[String] =
    results.foldLeft(List.empty[String]) { (acc, result) =>
      result match {
        case r @ ExecutionResult(ResultType.Suite, _, Status.Started, _, _, _, _, _) => acc :+ onSuiteStarted(r)
        case r @ ExecutionResult(ResultType.Suite, _, _, _, _, _, _, _)              => acc :+ onSuiteFinished(r)
        case r @ ExecutionResult(ResultType.Test, _, Status.Passed, _, _, _, _, duration) =>
          acc :+ onTestStarted(r) :+ onTestFinished(r, duration)
        case r @ ExecutionResult(ResultType.Test, _, Status.Failed, _, _, _, _, _) =>
          acc :+ onTestStarted(r) :+ onTestFailed(r)
        case r @ ExecutionResult(ResultType.Test, _, Status.Ignored, _, _, _, _, _) => acc :+ onTestIgnored(r)
      }
    }

  private def onSuiteStarted(result: ExecutionResult) =
    tc(s"testSuiteStarted name='${escape(result.label)}'")

  private def onSuiteFinished(result: ExecutionResult) =
    tc(s"testSuiteFinished name='${escape(result.label)}'")

  private def onTestStarted(result: ExecutionResult) =
    tc(
      s"testStarted name='${escape(result.label)}' locationHint='${escape(location(result))}' captureStandardOutput='true'"
    )

  private def onTestFinished(result: ExecutionResult, duration: Option[Long]) =
    tc(s"testFinished name='${escape(result.label)}' duration='${duration.map(_.toString).getOrElse("")}'")

  private def onTestIgnored(result: ExecutionResult) =
    tc(s"testIgnored name='${escape(result.label)}'")

  private def onTestFailed(result: ExecutionResult) = {
    val message = Message(result.streamingLines.drop(1)).withOffset(-result.offset)
    val error   = ConsoleRenderer.renderToStringLines(message).mkString("\n")

    tc(s"testFailed name='${escape(result.label)}' message='Assertion failed:' details='${escape(error)}'")
  }

  private def tc(message: String): String =
    s"##teamcity[$message]"

  def escape(str: String): String =
    str
      .replaceAll("[|]", "||")
      .replaceAll("[']", "|'")
      .replaceAll("[\n]", "|n")
      .replaceAll("[\r]", "|r")
      .replaceAll("]", "|]")
      .replaceAll("\\[", "|[")

  private def location(result: ExecutionResult) =
    result.annotations match {
      case annotations :: _ => locationRenderer.run(Nil, annotations).mkString
      case Nil              => ""
    }

  def renderSummary(summary: Summary): String = ""
}
object IntelliJRenderer extends IntelliJRenderer {
  val locationRenderer: TestAnnotationRenderer =
    LeafRenderer(TestAnnotation.trace) { case child :: _ =>
      child.headOption.collect { case SourceLocation(path, line) =>
        s"file://$path:$line"
      }
    }
}
