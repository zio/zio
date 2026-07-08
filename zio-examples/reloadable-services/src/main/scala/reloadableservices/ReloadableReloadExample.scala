/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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

package reloadableservices

import zio._

/** Tutorial: Hot-Swapping Services with Reloadable
  *
  * Concept: Explicit Hot-Swap — `Reloadable.reload`
  *
  * Demonstrates an atomic hot-swap of a running service: the old resource
  * scope is closed (finalizers run), then a brand-new instance is acquired,
  * all in one uninterruptible step.
  *
  * Run:
  * {{{
  *   sbt "examplesJVM/runMain zio.examples.ReloadableReloadExample"
  * }}}
  */
object ReloadableReloadExample extends ZIOAppDefault {

  trait Counter {
    def increment: UIO[Unit]
    def get: UIO[Int]
  }

  val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
    for {
      ref <- Ref.make(0)
      counter = new Counter {
                  def increment = ref.update(_ + 1)
                  def get       = ref.get
                }
      _ <- ZIO.debug(">>> Counter acquired")
      _ <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
    } yield counter
  }

  def run =
    (for {
      c1     <- Reloadable.get[Counter]
      _      <- c1.increment *> c1.increment
      before <- c1.get
      _      <- ZIO.debug(s"Before reload: $before")
      _      <- Reloadable.reload[Counter] // atomic teardown + rebuild
      c2     <- Reloadable.get[Counter]
      _      <- c2.increment
      after  <- c2.get
      _      <- ZIO.debug(s"After reload: $after")
    } yield ()).provide(Reloadable.manual(counterLayer))
}
