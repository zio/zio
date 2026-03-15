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

object TestServices {

  /**
   * The default ZIO Test services.
   */
  val test: ZEnvironment[Annotations & Live & Sized & TestConfig] =
    ZEnvironment[Annotations, Live, Sized, TestConfig](
      Annotations.Test(Ref.unsafe.make(TestAnnotationMap.empty)(Unsafe)),
      Live.Test(DefaultServices.live),
      Sized.Test(FiberRef.unsafe.make(100)(Unsafe)),
      TestConfig.TestV2(100, 100, 200, 1000, ZIOAspect.identity)
    )

  private[zio] val currentServices: FiberRef.WithPatch[
    ZEnvironment[Annotations & Live & Sized & TestConfig],
    ZEnvironment.Patch[
      Annotations & Live & Sized & TestConfig,
      Annotations & Live & Sized & TestConfig
    ]
  ] =
    FiberRef.unsafe.makeEnvironment(test)(Unsafe)
}
