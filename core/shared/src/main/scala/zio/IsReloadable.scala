/*
 * Copyright 2017-2023 John A. De Goes and the ZIO Contributors
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

import scala.annotation.implicitNotFound

/**
 * A `IsReloadable[A]` provides a utility to generate a proxy instance for a
 * given service.
 *
 * The `generate` function creates a proxy instance of the service that forwards
 * every ZIO method call to the underlying service, wrapped in a
 * [[zio.ScopedRef]]. The generated proxy enables the service to change its
 * behavior at runtime.
 *
 * @note
 *   In order to successfully generate a service proxy, the type `A` must meet
 *   the following requirements:
 *   - `A` should be either a trait or a class with a primary constructor
 *     without any term parameters.
 *   - `A` should contain only ZIO methods or vals.
 *   - `A` should not have any abstract type members.
 *
 * @example
 * {{{
 *   trait MyService { def foo: UIO[String] }
 *
 *   val service1: MyService = new MyService { def foo = ZIO.succeed("zio1") }
 *   val service2: MyService = new MyService { def foo = ZIO.succeed("zio2") }
 *   for {
 *     ref  <- ScopedRef.make(service1)
 *     proxy = IsReloadable[MyService].generate(ref)
 *     res1 <- proxy.foo
 *     _    <- ref.set(ZIO.succeed(service2))
 *     res2 <- proxy.foo
 *   } yield assertTrue(res1 == "zio1" && res2 == "zio2")
 * }}}
 */
@implicitNotFound(
  "Unable to generate a ZIO service proxy for ${A}." +
    "\n\nPlease ensure the following:" +
    "\n  1. The type is either a trait or a class with an empty primary constructor." +
    "\n  2. The type includes only ZIO methods or vals." +
    "\n  3. The type does not have any abstract type members."
)
trait IsReloadable[A] {
  def reloadable(service: ScopedRef[A]): A
}
object IsReloadable extends IsReloadableVersionSpecific {
  def apply[A](implicit isReloadable: IsReloadable[A]): IsReloadable[A] = isReloadable
}
