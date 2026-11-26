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

package zio

import zio.internal.macros.IsReloadableMacros

/**
 * Provides the derivation of `IsReloadable` instances: a macro on Scala 2 and
 * an inline given on Scala 3.
 */
private[zio] trait IsReloadableVersionSpecific {

  /**
   * Derives an `IsReloadable[A]` at compile time. The `reloadable` method of
   * the instance returns a proxy of `A`: each ZIO method or val of the proxy,
   * when its effect runs, reads the current service from the `ScopedRef` and
   * forwards the call to it. The proxy therefore follows the changes made with
   * `ScopedRef#set`. On Scala 3 the instance is marked `@experimental`.
   *
   * @tparam A
   *   The type of the service.
   */
  implicit def derived[A]: IsReloadable[A] = macro IsReloadableMacros.makeImpl[A]
}
