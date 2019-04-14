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

package scalaz.zio.stm

class TSemaphore private (permits: TRef[Long]) {
  final def acquire: STM[Nothing, Unit] = acquireN(1L)

  final def acquireN(n: Long): STM[Nothing, Unit] =
    (for {
      _     <- assertNonNegative(n)
      value <- permits.get
      _     <- STM.check(value >= n)
      _     <- permits.set(value - n)
    } yield ())

  final def available: STM[Nothing, Long] = permits.get

  final def release: STM[Nothing, Unit] = releaseN(1L)

  final def releaseN(n: Long): STM[Nothing, Unit] =
    assertNonNegative(n) *> permits.update(_ + n).unit

  final def withPermit[E, B](stm: STM[E, B]): STM[E, B] =
    acquire *> stm <* release

  private def assertNonNegative(n: Long): STM[Nothing, Unit] =
    if (n < 0)
      STM.die(new RuntimeException(s"Unexpected negative value `$n` passed to acquireN or releaseN."))
    else STM.unit
}

object TSemaphore {
  final def make(n: Long): STM[Nothing, TSemaphore] =
    TRef.make(n).map(v => new TSemaphore(v))
}
