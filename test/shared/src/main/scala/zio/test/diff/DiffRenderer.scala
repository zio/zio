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

package zio.test.diff

import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.{ConsoleUtils, ErrorMessage => M, PrettyPrint}

trait DiffRenderer {
  def render[A, B](expected: A, actual: B, diffResult: DiffResult): M
}

object DiffRenderer {
  implicit val default: DiffRenderer = new DiffRenderer {
    def render[A, B](expected: A, actual: B, diffResult: DiffResult): M =
      diffResult match {
        case DiffResult.Different(_, _, None) =>
          M.pretty(actual) + M.equals + M.pretty(expected)
        case diffResult =>
          M.choice("There was no difference", "There was a difference") ++
            M.custom(ConsoleUtils.underlined("Expected")) ++ M.custom(PrettyPrint(expected)) ++
            M.custom(
              ConsoleUtils.underlined(
                "Diff"
              ) + s" ${scala.Console.RED}-expected ${scala.Console.GREEN}+obtained".faint
            ) ++
            M.custom(scala.Console.RESET + diffResult.render)
      }
  }

  def render[A, B](expected: A, actual: B, diffResult: DiffResult)(implicit
    renderer: DiffRenderer
  ): M =
    renderer.render(expected, actual, diffResult)
}
