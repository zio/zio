/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The default values of the ZIO Test services that are not among the default
 * ZIO services: `Annotations`, `Live`, `Sized` and `TestConfig`. Each fiber
 * reads these services from a fiber-local environment that starts as `test`.
 * The test environment replaces them for the duration of a test, and the
 * accessors in the `zio.test` package object (`annotations`, `live`, `sized`,
 * `testConfig` and their `...With` forms) read them from there.
 *
 * @see
 *   [[https://zio.dev/reference/test/services/ Test Services]]
 */
object TestServices {

  /**
   * The default ZIO Test services: an empty `Annotations`, a `Live` backed by
   * the live default ZIO services, a `Sized` of size 100, and a `TestConfig`
   * with 100 repeats, 100 retries, 200 samples and 1000 shrinks. Code that runs
   * outside a test environment, such as a `ZIOAppDefault`, sees these values.
   *
   * {{{
   * val samples: Int = TestServices.test.get[TestConfig].samples // 200
   * }}}
   */
  val test: ZEnvironment[Annotations & Live & Sized & TestConfig] =
    ZEnvironment[Annotations, Live, Sized, TestConfig](
      Annotations.Test(Ref.unsafe.make(TestAnnotationMap.empty)(Unsafe)),
      Live.Test(DefaultServices.live),
      Sized.Test(FiberRef.unsafe.make(100)(Unsafe)),
      TestConfig.TestV2(100, 100, 200, 1000, ZIOAspect.identity)
    )

  /** The fiber-local ZIO Test services, starting as `test`. */
  private[zio] val currentServices: FiberRef.WithPatch[
    ZEnvironment[Annotations & Live & Sized & TestConfig],
    ZEnvironment.Patch[
      Annotations & Live & Sized & TestConfig,
      Annotations & Live & Sized & TestConfig
    ]
  ] =
    FiberRef.unsafe.makeEnvironment(test)(Unsafe)
}
