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

package zio.test

import zio.{Tag, URIO, ZIO, ZLayer}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.Trace
import zio.ZIOAspect

/**
 * The `TestConfig` service provides access to default configuration settings
 * used by ZIO Test, including the number of times to repeat tests to ensure
 * they are stable, the number of times to retry flaky tests, the sufficient
 * number of samples to check from a random variable, and the maximum number of
 * shrinkings to minimize large failures.
 */
trait TestConfig extends Serializable {

  /**
   * The number of times to repeat tests to ensure they are stable.
   */
  def repeats: Int

  /**
   * The number of times to retry flaky tests.
   */
  def retries: Int

  /**
   * The number of sufficient samples to check for a random variable.
   */
  def samples: Int

  /**
   * The maximum number of shrinkings to minimize large failures
   */
  def shrinks: Int

  /**
   * Aspect that should be applied to each check sample.
   *
   * NOTE: default implementation for backward compatibility. Remove in next
   * major version.
   */
  def checkSampleAspect: TestAspect.CheckSampleAspect = ZIOAspect.identity
}

object TestConfig {

  val tag: Tag[TestConfig] = Tag[TestConfig]

  final case class Test(repeats: Int, retries: Int, samples: Int, shrinks: Int) extends TestConfig

  // NOTE: Additonal class for backward compatibility. Rename to `Test` and remove the old one in next major version.
  final case class Test1(
    repeats: Int,
    retries: Int,
    samples: Int,
    shrinks: Int,
    override val checkSampleAspect: TestAspect.CheckSampleAspect
  ) extends TestConfig

  /**
   * Constructs a new `TestConfig` with the default settings.
   */
  val default: ZLayer[Any, Nothing, TestConfig] =
    live(100, 100, 200, 1000)(Trace.empty)

  /**
   * Constructs a new `TestConfig` service with the specified settings.
   */
  def live(
    repeats: Int,
    retries: Int,
    samples: Int,
    shrinks: Int
  )(implicit
    trace: Trace
  ): ZLayer[Any, Nothing, TestConfig] =
    ZLayer.scoped {
      val testConfig = Test(repeats, retries, samples, shrinks)
      withTestConfigScoped(testConfig).as(testConfig)
    }

  /**
   * Constructs a new `TestConfig` service with the specified settings.
   *
   * Note: manual overload instead of default argument for binary compatibility.
   * Remove in next major version.
   */
  def live(
    repeats: Int,
    retries: Int,
    samples: Int,
    shrinks: Int,
    checkSampleAspect: TestAspect.CheckSampleAspect
  )(implicit
    trace: Trace
  ): ZLayer[Any, Nothing, TestConfig] =
    ZLayer.scoped {
      val testConfig = Test1(repeats, retries, samples, shrinks, checkSampleAspect)
      withTestConfigScoped(testConfig).as(testConfig)
    }

  /**
   * The number of times to repeat tests to ensure they are stable.
   */
  def repeats(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeed(testConfig.repeats))

  /**
   * The number of times to retry flaky tests.
   */
  def retries(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeed(testConfig.retries))

  /**
   * The number of sufficient samples to check for a random variable.
   */
  def samples(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeed(testConfig.samples))

  /**
   * The maximum number of shrinkings to minimize large failures
   */
  def shrinks(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeed(testConfig.shrinks))

  /**
   * Action that should be performed on each check sample.
   */
  def checkSampleAspect(implicit trace: Trace): URIO[Any, TestAspect.CheckSampleAspect] =
    testConfigWith(testConfig => ZIO.succeed(testConfig.checkSampleAspect))
}
