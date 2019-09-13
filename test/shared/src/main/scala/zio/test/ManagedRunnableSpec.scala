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
import zio.Managed

/**
 * A managed runnable spec that allows the user to provide a managed resource
 * to the entire suite. This can be useful when the resource is expensive to
 * create and should only be created once for the entire suite.
 */
abstract class ManagedRunnableSpec[R](managed: Managed[Nothing, R])(
  spec: => ZSpec[R, Any, String, Any],
  defaultTestAspects: List[TestAspect[Nothing, R, Nothing, Any, Nothing, Any]] = List(
    TestAspect.timeoutWarning(60.seconds)
  )
) extends RunnableSpec(TestRunner(TestExecutor.managedSuite[R, Any, String, Any](managed)))(
      defaultTestAspects.foldLeft(spec)(_ @@ _)
    )
