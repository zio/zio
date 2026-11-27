package zio.test

import zio.{Console, Ref, UIO, ULayer, Unsafe, ZIO, ZLayer}

/**
 * Receives the [[ExecutionEvent]] values of a test run and keeps the run's
 * [[Summary]]. The test executor sends each event to the sink before it sends
 * it to the `ZTestEventHandler`.
 */
trait ExecutionEventSink {

  /**
   * Returns the summary of the events processed so far.
   */
  def getSummary: UIO[Summary]

  /**
   * Handles one event of the run.
   */
  def process(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

/**
 * Constructors and accessors for [[ExecutionEventSink]].
 */
object ExecutionEventSink {

  /**
   * Returns the summary of the `ExecutionEventSink` in the environment.
   */
  def getSummary: ZIO[ExecutionEventSink, Nothing, Summary] =
    ZIO.serviceWithZIO[ExecutionEventSink](_.getSummary)

  /**
   * Sends `event` to the `ExecutionEventSink` in the environment.
   */
  def process(event: ExecutionEvent): ZIO[ExecutionEventSink, Nothing, Unit] =
    ZIO.serviceWithZIO[ExecutionEventSink](_.process(event))

  /**
   * Creates a sink that adds each event to its summary and then passes it to
   * `testOutput` for printing.
   */
  def ExecutionEventSinkLive(testOutput: TestOutput): ZIO[Any, Nothing, ExecutionEventSink] =
    ZIO.succeed {
      new ExecutionEventSink {
        private val summary = Ref.unsafe.make(Summary.empty)(Unsafe)

        override def process(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
          summary.update(_.add(event)) *> testOutput.print(event)

        override def getSummary: UIO[Summary] =
          summary.get
      }
    }

  /**
   * A sink that prints the events to `console` with `eventRenderer`. On the JVM
   * and Scala Native it also writes the test results as JSON to
   * `target/test-reports-zio/output.json`.
   */
  def live(console: Console, eventRenderer: ReporterEventRenderer): ZLayer[Any, Nothing, ExecutionEventSink] =
    live(console, eventRenderer, reportsParent = TestArgs.reportsParentDefault)

  /**
   * A sink that prints the events to `console` with `eventRenderer`. On the JVM
   * and Scala Native it also writes the test results as JSON to
   * `test-reports-zio/output.json` under the `reportsParent` directory.
   */
  def live(
    console: Console,
    eventRenderer: ReporterEventRenderer,
    reportsParent: String
  ): ZLayer[Any, Nothing, ExecutionEventSink] =
    ZLayer.make[ExecutionEventSink](
      ExecutionEventPrinter.live(console, eventRenderer, reportsParent),
      TestOutput.live,
      ZLayer(ZIO.serviceWithZIO[TestOutput](ExecutionEventSinkLive(_)))
    )

  /**
   * A sink that prints the events through the `TestOutput` in the environment.
   * The sbt test runner uses it with a `TestOutput` shared by the specs of a
   * run.
   */
  val live: ZLayer[TestOutput, Nothing, ExecutionEventSink] =
    ZLayer(ZIO.serviceWithZIO[TestOutput](ExecutionEventSinkLive(_)))

  /**
   * A sink that ignores every event. Its summary is always `Summary.empty`.
   */
  val silent: ULayer[ExecutionEventSink] =
    ZLayer.succeed(
      new ExecutionEventSink {
        override def getSummary: UIO[Summary]                                = ZIO.succeed(Summary.empty)
        override def process(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = ZIO.unit
      }
    )
}
