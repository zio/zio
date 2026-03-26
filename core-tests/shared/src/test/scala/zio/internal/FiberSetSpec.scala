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

package zio.internal

import zio._
import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly}
import zio.ZIOBaseSpec

object FiberSetSpec extends ZIOBaseSpec {

  // A simple wrapper whose liveness can be toggled for GC-predicate tests.
  final class Entry(@volatile var alive: Boolean = true)

  def mkSet(nursery: Int = 64, concurrency: Int = 1): FiberSet[Entry] =
    FiberSet[Entry](nursery, concurrency, _.alive)

  def spec =
    suite("FiberSetSpec") {
      test("isEmpty on a freshly created set") {
        val set = mkSet()
        assertTrue(set.isEmpty)
      } +
        test("add makes isEmpty return false") {
          val set = mkSet()
          set.add(new Entry())
          assertTrue(!set.isEmpty)
        } +
        test("added entry appears in iteration") {
          val set = mkSet()
          val e   = new Entry()
          set.add(e)
          assertTrue(set.iterator.exists(_ eq e))
        } +
        test("removed entry does not appear in iteration") {
          val set = mkSet()
          val e   = new Entry()
          set.add(e)
          set.remove(e)
          assertTrue(!set.iterator.exists(_ eq e))
        } +
        test("removing an entry not in the set is a no-op") {
          val set = mkSet()
          val e   = new Entry()
          set.remove(e) // must not throw
          assertTrue(set.isEmpty)
        } +
        test("dead entries are excluded from iteration") {
          val set = mkSet()
          val e   = new Entry()
          set.add(e)
          e.alive = false
          assertTrue(!set.iterator.exists(_ eq e))
        } +
        test("dead entries cause isEmpty to return true") {
          val set = mkSet()
          val e   = new Entry()
          set.add(e)
          e.alive = false
          assertTrue(set.isEmpty)
        } +
        test("entries evicted from a full nursery remain reachable") {
          // nursery = 4 slots; adding 20 entries forces most into long-term storage
          val set     = mkSet(nursery = 4)
          val entries = List.fill(20)(new Entry())
          entries.foreach(set.add)
          val found = set.iterator.toSet
          assertTrue(entries.forall(e => found.contains(e)))
        } +
        test("concurrent adds are all represented") {
          val set     = FiberSet[Entry](1024, concurrencyLevel = 4, isAlive = _.alive)
          val entries = List.fill(200)(new Entry())
          ZIO
            .foreachPar(entries)(e => ZIO.succeed(set.add(e)))
            .map(_ => assertTrue(entries.forall(e => set.iterator.exists(_ eq e))))
        } +
        test("gc reclaims cleared weak references from long-term storage") {
          val set               = mkSet(nursery = 4)
          var hard: List[Entry] = (1 to 16).map(_ => new Entry()).toList
          hard.foreach(set.add)
          set.iterator.foreach(_ => ()) // flush nursery into long-term storage

          hard = Nil // drop all hard references
          java.lang.System.gc()
          set.add(new Entry()) // triggers drainQueue / sweepLongTerm

          assertTrue(set.size < 17)
        } @@ flaky
    } @@ jvmOnly
}
