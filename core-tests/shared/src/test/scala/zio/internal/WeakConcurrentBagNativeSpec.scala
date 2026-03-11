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

package zio.internal

import zio.test._
import zio.test.TestAspect.nativeOnly
import zio.{ZIO, ZIOBaseSpec}

/**
 * Regression test for https://github.com/zio/zio/issues/9681
 *
 * `WeakConcurrentBag.addToLongTermStorage` triggered an NPE in Scala Native
 * when forking many fibers because `ConcurrentHashMap.newKeySet()` has a race
 * condition in `treeifyBin()` under high concurrency.  The fix switches the
 * Scala Native `Platform.newConcurrentSet` implementation to
 * `Collections.newSetFromMap(new ConcurrentHashMap[A, java.lang.Boolean]())`
 * which avoids the buggy `KeySetView` / `treeifyBin` code path.
 */
object WeakConcurrentBagNativeSpec extends ZIOBaseSpec {

  def spec =
    suite("WeakConcurrentBagNativeSpec")(
      test("does not NPE when forking 10K fibers (regression: #9681)") {
        // Each fork exercises WeakConcurrentBag.addToLongTermStorage via the
        // scheduler's internal bookkeeping.  Prior to the fix this would
        // throw a NullPointerException on Scala Native.
        ZIO.foreachPar(1 to 10000)(_ => ZIO.unit).as(assertCompletes)
      }
    ) @@ nativeOnly
}
