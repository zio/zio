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

/**
 * The sbt test framework of ZIO Test. sbt uses it to find the spec objects
 * (objects that extend `zio.test.ZIOSpecAbstract`, such as `ZIOSpecDefault`)
 * and to run them with `sbt test` and `sbt testOnly`. sbt 1.8.0 and later
 * register it when `zio-test-sbt` is on the test classpath; older versions need
 * `zio.test.sbt.ZTestFramework` in the `testFrameworks` setting.
 *
 * On the JVM, `runner` returns a [[ZTestRunner]]. The runner builds one runtime
 * from the `bootstrap` layers of the specs in each `tasks` call, so a layer
 * that several of those specs share is built once. It prints the summary when
 * sbt calls `done()`. The framework's own class loader is used when no test
 * class loader is given.
 *
 * On Scala.js and Scala Native, `runner` returns a `ZTestRunner.Master` and
 * `slaveRunner` returns a `ZTestRunner.Slave`. Slave runners send the summaries
 * of their specs to the master runner. There is no shared runtime: each spec
 * runs as its own application with its own `bootstrap` layer. The master
 * runner's `done()` returns the summary instead of printing it. The class
 * loader used when none is given is `null`.
 *
 * @see
 *   [[https://zio.dev/reference/test/installation/ Installation]]
 * @see
 *   [[https://zio.dev/reference/test/running-tests/ Running Tests]]
 */
final class ZTestFramework extends TestFramework {
  override protected def getClassLoader: ClassLoader = getClass.getClassLoader

  /**
   * Returns a new runner for one sbt test run: a [[ZTestRunner]] on the JVM,
   * and a `ZTestRunner.Master` on Scala.js and Scala Native.
   */
  override def runner(
    args: Array[String],
    remoteArgs: Array[String],
    testClassLoader: ClassLoader
  ): ZTestRunner = new ZTestRunner(
    args,
    remoteArgs,
    testClassLoader
  )
}
