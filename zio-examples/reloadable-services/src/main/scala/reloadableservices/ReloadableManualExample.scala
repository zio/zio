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
  * Concept: Wrapping a Layer — `Reloadable.manual` and `Reloadable.get`
  *
  * Shows how to lift any resource-managed ZLayer into a Reloadable wrapper,
  * then access the live service instance via `Reloadable.get[Counter]`.
  *
  * Run:
  * {{{
  *   sbt "examplesJVM/runMain zio.examples.ReloadableManualExample"
  * }}}
  */
object ReloadableManualExample extends ZIOAppDefault {

  /** The service interface used throughout this tutorial. */
  trait Counter {
    def increment: UIO[Unit]
    def get: UIO[Int]
  }

  /** A scoped layer that logs its own acquire and release events so the
    * lifecycle is immediately visible in the console.
    */
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
      c     <- Reloadable.get[Counter]
      _     <- c.increment
      _     <- c.increment
      count <- c.get
      _     <- ZIO.debug(s"Count: $count")
    } yield ()).provide(Reloadable.manual(counterLayer))
}
