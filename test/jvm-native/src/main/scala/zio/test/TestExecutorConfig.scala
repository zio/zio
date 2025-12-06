package zio.test

import zio._
import zio.internal.NIOExecutor

/**
 * Configuration utility for overriding the test executor via system property.
 *
 * Supports the following values for `zio.test.executor`:
 *  - `default` - Standard ZIO default executor
 *  - `zscheduler` - ZScheduler executor (current default)
 *  - `nio` - NIOExecutor for testing NIO functionality
 *
 * @example
 * {{{
 * sbt "testOnly * -Dzio.test.executor=nio"
 * }}}
 */
object TestExecutorConfig {

  sealed trait ExecutorType
  object ExecutorType {
    case object Default extends ExecutorType
    case object ZScheduler extends ExecutorType
    case object NIO extends ExecutorType
  }

  /**
   * Reads the `zio.test.executor` system property and returns the corresponding executor type.
   *
   * @return Some(ExecutorType) if property is set, None otherwise
   * @throws IllegalArgumentException if the property value is not recognized
   */
  def fromSystemProperty: Option[ExecutorType] =
    sys.props.get("zio.test.executor").map {
      case "default" => ExecutorType.Default
      case "zscheduler" => ExecutorType.ZScheduler
      case "nio" => ExecutorType.NIO
      case unknown =>
        throw new IllegalArgumentException(
          s"Unknown executor type: '$unknown'. Supported values: default, zscheduler, nio"
        )
    }
}
