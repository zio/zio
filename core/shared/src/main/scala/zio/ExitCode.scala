/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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
 * The exit code of a program: the integer `code` that the process reports to
 * its caller when it ends. `ZIO#exitCode` and the `ZIOApp` exit code use
 * `ExitCode.success` and `ExitCode.failure`, and `ZIOApp#exit` ends the program
 * with the given code. On Scala.js, `ZIOApp#exit` sets the exit code without
 * stopping the program.
 *
 * {{{
 * val code: ExitCode = ExitCode(2)
 *
 * val fromEffect: UIO[ExitCode] = ZIO.fail("boom").exitCode
 * // ExitCode(1)
 * }}}
 */
final case class ExitCode(code: Int)

/** The predefined exit codes. */
object ExitCode {

  /** The exit code 0, which stands for success. */
  val success: ExitCode = ExitCode(0)

  /** The exit code 1, which stands for failure. */
  val failure: ExitCode = ExitCode(1)
}
