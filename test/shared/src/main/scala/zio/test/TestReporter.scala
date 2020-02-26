/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import zio.duration.Duration
import zio.test.reflect.Reflect.EnableReflectiveInstantiation
import zio.{ UIO, URIO, ZIO }

/**
 * A `TestReporter[E]` is capable of reporting test results with error type
 * `E`.
 */
@EnableReflectiveInstantiation
trait TestReporter[-E] {
  def report(duration: Duration, executedSpec: ExecutedSpec[E]): URIO[TestLogger, Unit]
  def render(executedSpec: ExecutedSpec[E], renderer: TestAnnotationRenderer): UIO[Seq[RenderedResult[String]]]
}

object TestReporter {

  /**
   * TestReporter that does nothing
   */
  val silent: TestReporter[Any] = new TestReporter[Any] {
    override def report(duration: Duration, executedSpec: ExecutedSpec[Any]): URIO[TestLogger, Unit] = ZIO.unit

    override def render(
      executedSpec: ExecutedSpec[Any],
      renderer: TestAnnotationRenderer
    ): UIO[Seq[RenderedResult[String]]] =
      ZIO.succeed(Seq.empty)
  }
}
