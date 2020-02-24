/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio

import zio.internal.Platform

@deprecated(
  "Use `Runtime.default` and provide the required environment directly using" +
    "`ZIO#provideLayer`. In general, layers are managed resources that " +
    "require allocation and deallocation and therefore scoping resources to" +
    "an effect is highly recommended as a best practice. " +
    "`Runtime.unsafefromLayer` can be used when this best practice is is too " +
    "inconvenient.",
  "1.0.0"
)
trait DefaultRuntime extends Runtime[ZEnv] {
  override val platform: Platform = Platform.default
  override val environment: ZEnv  = Runtime.unsafeFromLayer(ZEnv.live, platform).environment
}
