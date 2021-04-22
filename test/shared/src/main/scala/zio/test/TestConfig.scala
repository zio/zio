/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio.{Has, URIO, ZIO, ZLayer}

/**
 * The `TestConfig` service provides access to default configuration settings
 * used by ZIO Test, including the number of times to repeat tests to ensure
 * they are stable, the number of times to retry flaky tests, the sufficient
 * number of samples to check from a random variable, and the maximum number
 * of shrinkings to minimize large failures.
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

  /**
   * Constructs a new `TestConfig` service with the specified settings.
   */
  def live(repeats0: Int, retries0: Int, samples0: Int, shrinks0: Int): ZLayer[Any, Nothing, Has[TestConfig]] =
    ZLayer.succeed {
      new TestConfig {
        val repeats = repeats0
        val retries = retries0
        val samples = samples0
        val shrinks = shrinks0
      }
    }

  /**
   * The number of times to repeat tests to ensure they are stable.
   */
  val repeats: URIO[Has[TestConfig], Int] =
    ZIO.access(_.get.repeats)

  /**
   * The number of times to retry flaky tests.
   */
  val retries: URIO[Has[TestConfig], Int] =
    ZIO.access(_.get.retries)

  /**
   * The number of sufficient samples to check for a random variable.
   */
  val samples: URIO[Has[TestConfig], Int] =
    ZIO.access(_.get.samples)

  /**
   * The maximum number of shrinkings to minimize large failures
   */
  val shrinks: URIO[Has[TestConfig], Int] =
    ZIO.access(_.get.shrinks)
}
