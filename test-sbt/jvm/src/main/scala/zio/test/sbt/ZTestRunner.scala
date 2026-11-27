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

package zio.test.sbt

import zio.test.Summary

/**
 * The sbt runner of ZIO Test, created by [[ZTestFramework]].
 *
 * On the JVM, the specs of each `tasks` call run on one runtime built from
 * their `bootstrap` layers. The runner collects the summary of each spec, and
 * `done()` prints the combined summary and returns `"Completed tests"`.
 *
 * On Scala.js and Scala Native, a runner is a `ZTestRunner.Master` or a
 * `ZTestRunner.Slave`. There is no shared runtime: each spec runs as its own
 * application with its own `bootstrap` layer. `done()` returns the combined
 * summary instead of printing it.
 */
final class ZTestRunner(
  args: Array[String],
  remoteArgs: Array[String],
  testClassLoader: ClassLoader
) extends TestRunner(
      args,
      remoteArgs,
      testClassLoader,
      runnerType = "JVM"
    ) {
  override protected def sharedRuntimeSupported: Boolean = true

  override protected def sendSummary(summary: Summary): Unit = addSummary(summary)

  override protected def returnSummary: Boolean = false
}
