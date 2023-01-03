/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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
}

object TestConfig {

  val tag: Tag[TestConfig] = Tag[TestConfig]

  final case class Test(repeats: Int, retries: Int, samples: Int, shrinks: Int) extends TestConfig

  /**
   * Constructs a new `TestConfig` with the default settings.
   */
  val default: ZLayer[Any, Nothing, TestConfig] =
    live(100, 100, 200, 1000)(Trace.empty)

  /**
   * Constructs a new `TestConfig` service with the specified settings.
   */
  def live(repeats: Int, retries: Int, samples: Int, shrinks: Int)(implicit
    trace: Trace
  ): ZLayer[Any, Nothing, TestConfig] =
    ZLayer.scoped {
      val testConfig = Test(repeats, retries, samples, shrinks)
      withTestConfigScoped(testConfig).as(testConfig)
    }

  /**
   * The number of times to repeat tests to ensure they are stable.
   */
  def repeats(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeedNow(testConfig.repeats))

  /**
   * The number of times to retry flaky tests.
   */
  def retries(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeedNow(testConfig.retries))

  /**
   * The number of sufficient samples to check for a random variable.
   */
  def samples(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeedNow(testConfig.samples))

  /**
   * The maximum number of shrinkings to minimize large failures
   */
  def shrinks(implicit trace: Trace): URIO[Any, Int] =
    testConfigWith(testConfig => ZIO.succeedNow(testConfig.shrinks))
}
