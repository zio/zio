/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

import zio.duration._
import zio.test.environment.TestEnvironment

/**
 * A default runnable spec that provides testable versions of all of the
 * modules in ZIO (Clock, Random, etc).
 */
abstract class DefaultRunnableSpec(
  spec0: => ZSpec[TestEnvironment, Any, String, Any] = Spec.test("DefaultRunnableSpec", ignore),
  defaultTestAspects0: List[TestAspect[Nothing, TestEnvironment, Nothing, Any, Nothing, Any]] = List(
    TestAspect.timeoutWarning(60.seconds)
  )
) extends RunnableSpec[TestEnvironment, String, Either[TestFailure[Nothing], TestSuccess[Any]], Any, Any] {

  def defaultTestAspects = defaultTestAspects0
  override def runner    = DefaultTestRunner
  override def spec      = defaultTestAspects.foldLeft(spec0)(_ @@ _)
}
