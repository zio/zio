/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.{Duration, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.Summary.{Failure, Success}

final case class Summary(
  success: Int,
  fail: Int,
  ignore: Int,
  summary: String,
  duration: Duration = Duration.Zero
) {
  val status: Summary.Status =
    if (summary.trim.isEmpty)
      Summary.Success
    else
      Summary.Failure
  def total: Int = success + fail + ignore

  def add(executionEvent: ExecutionEvent)(implicit trace: ZTraceElement): Summary =
    SummaryBuilder.buildSummary(executionEvent, this)

  def add(other: Summary)(implicit trace: ZTraceElement): Summary =
    Summary(
      success + other.success,
      fail + other.fail,
      ignore + other.ignore,
      summary +
        (if (other.summary.trim.isEmpty)
           ""
         else
           "\n" + other.summary)
    )
}

object Summary {
  sealed trait Status
  object Success extends Status
  object Failure extends Status
}
