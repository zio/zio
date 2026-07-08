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

package zio.examples

import zio._

/** Tutorial: Hot-Swapping Services with Reloadable
  *
  * Concept: Automatic Scheduled Reloads — `Reloadable.auto`
  *
  * Shows how `Reloadable.auto` drives periodic hot-swaps with a Schedule,
  * managing the background daemon fiber's lifecycle automatically.
  *
  * Run:
  * {{{
  *   sbt "examplesJVM/runMain zio.examples.ReloadableAutoExample"
  * }}}
  */
object ReloadableAutoExample extends ZIOAppDefault {

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

  /** The service is rebuilt automatically every 2 seconds by a daemon fiber
    * managed inside the layer's scope.
    */
  val autoLayer: ZLayer[Any, Nothing, Reloadable[Counter]] =
    Reloadable.auto(counterLayer, Schedule.fixed(2.seconds))

  def run =
    (for {
      c1     <- Reloadable.get[Counter]
      _      <- c1.increment *> c1.increment *> c1.increment
      before <- c1.get
      _      <- ZIO.debug(s"Before auto-reload: $before")
      _      <- ZIO.sleep(3.seconds) // wait for at least one automatic reload
      c2     <- Reloadable.get[Counter]
      after  <- c2.get
      _      <- ZIO.debug(s"After auto-reload: $after")
    } yield ()).provide(autoLayer)
}
