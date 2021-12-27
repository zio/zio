/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import zio._
import zio.test._
import zio.test.render._
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class MockSpec[R: Tag] extends ZIOSpec { self =>

  override final def testReporter(testRenderer: TestRenderer, testAnnotationRenderer: TestAnnotationRenderer)(implicit trace: ZTraceElement): TestReporter[Any] =
    MockTestReporter(testRenderer, testAnnotationRenderer)
}
