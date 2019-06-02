/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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
package interop

import com.github.ghik.silencer.silent
import org.specs2.Specification
import zio.interop.bio.{Async2, Concurrent2, Errorful2, Guaranteed2, RunAsync2, RunSync2, Sync2, Temporal2}
import zio.interop.runtime.TestRuntime

final class ZioTestDefaultsSpec extends Specification with TestRuntime {

  def is = "ZioTestDefaultsSpec".title ^ s2"""
    The default test type-class instances for Zio:
      don't give conflict when unified all together. $unifyAll
  """

  private[this] def unifyAll = {

    import default.testZioInstances._

    @silent def f[F[+ _, + _]](
      implicit
      A: Guaranteed2[F],
      B: Errorful2[F],
      C: Sync2[F],
      D: Temporal2[F],
      E: Concurrent2[F],
      F: RunSync2[F],
      G: Async2[F],
      H: RunAsync2[F]
    ): Unit = ()

    val _ = f[IO]

    success
  }

}
