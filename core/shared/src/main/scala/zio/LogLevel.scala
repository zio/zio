/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `LogLevel` is the severity of a logging operation. Levels describe how
 * important a log statement is, and they let you tune how much log output is
 * produced by comparing one level with another.
 *
 * `LogLevel` values are ZIO aspects, so they can be used with the `@@` syntax.
 * Applying a level to an effect sets the level that `ZIO.log` uses inside that
 * effect; the explicit variants such as `ZIO.logInfo` keep their own level.
 *
 * {{{
 * val effect: UIO[Unit] = ZIO.log("starting") *> ZIO.logInfo("started")
 *
 * // "starting" is logged at level DEBUG, "started" at level INFO
 * val debugged: UIO[Unit] = effect @@ LogLevel.Debug
 * }}}
 *
 * @param ordinal
 *   The numeric rank of the level: a larger value is a more severe level.
 *   `LogLevel.All` is `Int.MinValue` and `LogLevel.None` is `Int.MaxValue`.
 * @param label
 *   The text that loggers print for the level, such as `INFO`.
 * @param syslog
 *   The syslog severity number of the level. `Debug`, `Trace` and `None` all
 *   use 7.
 * @see
 *   [[https://zio.dev/reference/observability/logging/ Logging]]
 */
final case class LogLevel(ordinal: Int, label: String, syslog: Int)
    extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] { self =>

  /**
   * Returns `true` when the ordinal of this level is less than that of `that`.
   */
  def <(that: LogLevel): Boolean =
    self.ordinal < that.ordinal

  /**
   * Returns `true` when the ordinal of this level is less than or equal to that
   * of `that`.
   */
  def <=(that: LogLevel): Boolean =
    self.ordinal <= that.ordinal

  /**
   * Returns `true` when the ordinal of this level is greater than or equal to
   * that of `that`.
   */
  def >=(that: LogLevel): Boolean =
    self.ordinal >= that.ordinal

  /**
   * Returns `true` when the ordinal of this level is greater than that of
   * `that`.
   */
  def >(that: LogLevel): Boolean =
    self.ordinal > that.ordinal

  /**
   * Runs `zio` with this level as the level used by `ZIO.log`, by setting
   * `FiberRef.currentLogLevel` for the duration of `zio`.
   */
  def apply[R >: Nothing <: Any, E >: Nothing <: Any, A >: Nothing <: Any](zio: ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R, E, A] =
    FiberRef.currentLogLevel.locally(self)(zio)
}

/** The predefined log levels, from `All` to `None`. */
object LogLevel {

  /**
   * The `ALL` level, with ordinal `Int.MinValue`: lower than every other one.
   */
  val All: LogLevel = LogLevel(Int.MinValue, "ALL", 0)

  /** The `FATAL` level, with ordinal 50000 and syslog severity 2. */
  val Fatal: LogLevel = LogLevel(50000, "FATAL", 2)

  /** The `ERROR` level, with ordinal 40000 and syslog severity 3. */
  val Error: LogLevel = LogLevel(40000, "ERROR", 3)

  /** The `WARN` level, with ordinal 30000 and syslog severity 4. */
  val Warning: LogLevel = LogLevel(30000, "WARN", 4)

  /** The `INFO` level, with ordinal 20000 and syslog severity 6. */
  val Info: LogLevel = LogLevel(20000, "INFO", 6)

  /** The `DEBUG` level, with ordinal 10000 and syslog severity 7. */
  val Debug: LogLevel = LogLevel(10000, "DEBUG", 7)

  /** The `TRACE` level, with ordinal 0 and syslog severity 7. */
  val Trace: LogLevel = LogLevel(0, "TRACE", 7)

  /**
   * The `OFF` level, with ordinal `Int.MaxValue`: higher than every other
   * level, so `level >= LogLevel.None` holds only for `None` itself.
   */
  val None: LogLevel = LogLevel(Int.MaxValue, "OFF", 7)

  /** The set of the eight predefined levels, from `All` to `None`. */
  val levels: Set[LogLevel] = Set(
    LogLevel.All,
    LogLevel.Trace,
    LogLevel.Debug,
    LogLevel.Info,
    LogLevel.Warning,
    LogLevel.Error,
    LogLevel.Fatal,
    LogLevel.None
  )

  /** An `Ordering` that sorts levels by ordinal, lowest first. */
  implicit val orderingLogLevel: Ordering[LogLevel] = Ordering.by(_.ordinal)
}
