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

import cats.Monad
import cats.syntax.either._
import org.specs2.Specification
import org.specs2.execute.Result
import zio.interop.bio.Errorful2
import zio.interop.runtime.TestRuntime

final class ErrorfulMonadTailRecConstructionSpec extends Specification with TestRuntime {

  def is = "ErrorfulMonadTailRecConstructionSpec".title ^ s2"""
    Errorful2's Monad `tailRecM`:
      can be constructed in a tail recursive manner. $tailRecMSafe
  """

  def tailRecMSafe: Result = {

    val n = 100000

    val ion = testRuntime.unsafeRun {
      import default.testZioInstances._

      def M[E]: Monad[IO[E, ?]] =
        Errorful2[IO[+?, +?]].monad

      def f[E](i: Int): IO[E, Either[Int, Int]] =
        if (i < n) M.tailRecM(i + 1)(f).map(Either.left)
        else M.pure(Either.right(i))

      M.tailRecM(0)(f)
    }

    ion should_=== n
  }
}
