/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class ZIOSpec[R: CompositeTag] extends ZIOSpecAbstract { self =>
  type Environment = R

  final val tag: CompositeTag[R] = CompositeTag[R]

  /**
   * Builds a spec with a single test.
   */
  def test[In](label: String)(
    assertion: => In
  )(implicit
    testConstructor: TestConstructor[Nothing, In],
    trace: ZTraceElement
  ): testConstructor.Out =
    zio.test.test(label)(assertion)

  def suite[In](label: String)(specs: In*)(implicit
    suiteConstructor: SuiteConstructor[In],
    trace: ZTraceElement
  ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError, suiteConstructor.OutSuccess] =
    zio.test.suite(label)(specs: _*)
}
