/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

package zio.stm

import zio.stm.ZSTM.internal.TExit

final class TSemaphore private (val permits: TRef[Long]) extends AnyVal {
  def acquire: USTM[Unit] = acquireN(1L)

  def acquireN(n: Long): USTM[Unit] =
    new ZSTM((journal, _, _, _) => {
      assertNonNegative(n)

      val value = permits.unsafeGet(journal)

      if (value < n) TExit.Retry
      else {
        permits.unsafeSet(journal, value - n)
        TExit.unit
      }
    })

  def available: USTM[Long] = permits.get

  def release: USTM[Unit] = releaseN(1L)

  def releaseN(n: Long): USTM[Unit] =
    new ZSTM((journal, _, _, _) => {
      assertNonNegative(n)

      val current = permits.unsafeGet(journal)
      permits.unsafeSet(journal, current + n)

      TExit.unit
    })

  def withPermit[E, B](stm: STM[E, B]): STM[E, B] =
    acquire *> stm <* release

  private def assertNonNegative(n: Long): Unit =
    require(n >= 0, s"Unexpected negative value `$n` passed to acquireN or releaseN.")
}

object TSemaphore {
  def make(n: Long): USTM[TSemaphore] =
    TRef.make(n).map(v => new TSemaphore(v))
}
