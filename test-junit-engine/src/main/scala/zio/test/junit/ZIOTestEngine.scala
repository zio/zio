/*
 * Copyright 2024-2024 Vincent Raman and the ZIO Contributors
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

package zio.test.junit

import org.junit.platform.engine.{
  EngineDiscoveryRequest,
  ExecutionRequest,
  TestDescriptor,
  TestEngine,
  TestExecutionResult,
  UniqueId
}
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver
import zio._

import scala.jdk.CollectionConverters._

/**
 * A JUnit Platform test engine that discovers and runs ZIO Test specs; its id is `zio`. It is registered as a `TestEngine` service, so build tools that run tests on the JUnit Platform, such as Maven Surefire, find it when `zio-test-junit-engine` is on the test class path. Specs need no annotation.
 *
 * Discovery finds the spec classes (see [[ZIOTestClassSelectorResolver]]) in the selected classes, packages, modules and class path roots, such as `object MySpec extends ZIOSpecDefault`. Each spec becomes a container, each suite a nested container and each test a test, and the ZIO Test tags of a test become its JUnit tags (a tag must be a valid JUnit tag name, for example without spaces), so tag filters (for example Surefire's `groups`) select tests by tag.
 */
class ZIOTestEngine extends TestEngine {

  /** Returns `zio`, the id of this engine. */
  override def getId: String = "zio"

  private lazy val discoverer = EngineDiscoveryRequestResolver
    .builder[EngineDescriptor]()
    .addSelectorResolver(new ZIOTestClassSelectorResolver)
    .build()

  /**
   * Returns an engine descriptor named `ZIO EngineDescriptor` with a [[ZIOTestClassDescriptor]] for each spec class that `discoveryRequest` selects. Creating a class descriptor runs the effects that build the suites of its spec. When the `bootstrap` layer of the spec fails, when one of these effects dies, or when one that isn't inside another suite fails, the selector is reported as failed; a suite whose effect fails inside another suite is left out.
   */
  override def discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor = {
    val engineDesc = new EngineDescriptor(uniqueId, "ZIO EngineDescriptor")
    discoverer.resolve(discoveryRequest, engineDesc)
    engineDesc
  }

  /**
   * Runs the spec classes of `request` one after another with
   * [[ZIOTestClassRunner]] and reports them to the listener of `request`. Each
   * class is reported as successful when its run ends, whatever the results of
   * its tests, which are reported one by one. Descriptors that aren't spec
   * classes are skipped with a warning log. When the run of a class fails, this
   * method throws and the remaining classes don't run.
   */
  override def execute(request: ExecutionRequest): Unit =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        val engineDesc = request.getRootTestDescriptor
        val listener   = request.getEngineExecutionListener
        ZIO.logInfo("Start tests execution...") *> ZIO.foreachDiscard(engineDesc.getChildren.asScala) {
          case clzDesc: ZIOTestClassDescriptor =>
            ZIO.logInfo(s"Start execution of test class ${clzDesc.className}...") *> ZIO.succeed(
              listener.executionStarted(clzDesc)
            ) *> new ZIOTestClassRunner(clzDesc).run(listener) *> ZIO.succeed(
              listener.executionFinished(clzDesc, TestExecutionResult.successful())
            )
          case otherDesc =>
            // Do nothing for other descriptor, just log it.
            ZIO.logWarning(s"Found test descriptor $otherDesc that is not supported, skipping.")

        } *> ZIO.succeed(listener.executionFinished(engineDesc, TestExecutionResult.successful())) *> ZIO.logInfo(
          "Completed tests execution."
        )
      }
        .getOrThrowFiberFailure()
    }
}
