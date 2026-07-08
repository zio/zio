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
  * Concept: Putting It All Together — all reload strategies in one program
  *
  * A complete example combining `Reloadable.manual`, `Reloadable.reload`
  * (blocking hot-swap), and `Reloadable.reloadFork` (background hot-swap),
  * making three full acquire/release cycles visible in the console.
  *
  * Run:
  * {{{
  *   sbt "examplesJVM/runMain zio.examples.ReloadableFullExample"
  * }}}
  */
object ReloadableFullExample extends ZIOAppDefault {

  // ── Service definition ────────────────────────────────────────────────────

  trait Counter {
    def increment: UIO[Unit]
    def get: UIO[Int]
  }

  // ── Implementation with visible lifecycle ─────────────────────────────────

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

  // ── Main program ──────────────────────────────────────────────────────────

  def run =
    (for {
      // 1. Get the initial instance and increment twice.
      c1 <- Reloadable.get[Counter]
      _  <- c1.increment *> c1.increment
      v1 <- c1.get
      _  <- ZIO.debug(s"Initial count: $v1") // 2

      // 2. Blocking atomic hot-swap — old finalizers run, new instance built.
      _  <- Reloadable.reload[Counter]

      // 3. Fresh instance starts at 0; increment once.
      c2 <- Reloadable.get[Counter]
      _  <- c2.increment
      v2 <- c2.get
      _  <- ZIO.debug(s"After manual reload: $v2") // 1

      // 4. Fork a non-blocking background reload; main fiber continues.
      _  <- Reloadable.reloadFork[Counter]
      _  <- ZIO.sleep(50.millis) // give the daemon fiber time to finish

      // 5. Another fresh instance — count is back to 0.
      c3 <- Reloadable.get[Counter]
      v3 <- c3.get
      _  <- ZIO.debug(s"After fork reload: $v3") // 0
    } yield ()).provide(Reloadable.manual(counterLayer))
}
