package zio.test

/**
 * The kinds of [[ExecutionEvent]].
 */
object ExecutionEvent {

  /**
   * Emitted when a test starts, before its effect runs.
   */
  final case class TestStarted(
    labelsReversed: List[String],
    annotations: TestAnnotationMap,
    ancestors: List[SuiteId],
    id: SuiteId,
    fullyQualifiedName: String
  ) extends ExecutionEvent {

    /**
     * The labels from the outermost suite down to this event, the reverse of
     * `labelsReversed`.
     */
    val labels: List[String] = labelsReversed.reverse
  }

  /**
   * Emitted when a test finishes, with its result, its annotations and its
   * duration in milliseconds.
   */
  final case class Test[+E](
    labelsReversed: List[String],
    test: Either[TestFailure[E], TestSuccess],
    annotations: TestAnnotationMap,
    ancestors: List[SuiteId],
    duration: Long,
    id: SuiteId,
    fullyQualifiedName: String
  ) extends ExecutionEvent {

    /**
     * The labels from the outermost suite down to this event, the reverse of
     * `labelsReversed`.
     */
    val labels: List[String] = labelsReversed.reverse
  }

  /**
   * Emitted when a suite starts, before its children run.
   */
  final case class SectionStart(
    labelsReversed: List[String],
    id: SuiteId,
    ancestors: List[SuiteId]
  ) extends ExecutionEvent {

    /**
     * The labels from the outermost suite down to this event, the reverse of
     * `labelsReversed`.
     */
    val labels: List[String] = labelsReversed.reverse
  }

  /**
   * Emitted when a suite ends, after its children have finished.
   */
  final case class SectionEnd(
    labelsReversed: List[String],
    id: SuiteId,
    ancestors: List[SuiteId]
  ) extends ExecutionEvent {

    /**
     * The labels from the outermost suite down to this event, the reverse of
     * `labelsReversed`.
     */
    val labels: List[String] = labelsReversed.reverse
  }

  /**
   * Emitted once at the end of a run, after the whole spec has finished.
   */
  final case class TopLevelFlush(id: SuiteId) extends ExecutionEvent {

    /**
     * Always empty.
     */
    val labels: List[String] = List.empty

    /**
     * Always empty.
     */
    val ancestors: List[SuiteId] = List.empty
  }

  /**
   * Emitted when a failure happens outside a test, for example when the layer
   * shared by the whole spec fails to build.
   */
  final case class RuntimeFailure[+E](
    id: SuiteId,
    labelsReversed: List[String],
    failure: TestFailure[E],
    ancestors: List[SuiteId]
  ) extends ExecutionEvent {

    /**
     * The labels from the outermost suite down to this event, the reverse of
     * `labelsReversed`.
     */
    val labels: List[String] = labelsReversed.reverse
  }

}

/**
 * An event that the test executor emits while it runs a spec: a suite starting
 * or ending, a test starting or finishing, a failure outside a test, or the end
 * of the run. The test runner builds its output and the [[Summary]] from these
 * events.
 */
sealed trait ExecutionEvent {

  /**
   * The id of the suite the event belongs to: the suite itself for
   * `SectionStart` and `SectionEnd`, and the enclosing suite for test events
   * and `RuntimeFailure`. The `RuntimeFailure` for a failed top-level layer
   * carries `SuiteId(-1)`, and `TopLevelFlush` carries `SuiteId.global`.
   */
  val id: SuiteId

  /**
   * The ids of the suites that enclose the suite `id`, the innermost first.
   */
  val ancestors: List[SuiteId]

  /**
   * The labels from the outermost suite down to this event.
   */
  val labels: List[String]
}
