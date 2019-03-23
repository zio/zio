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

import scala.collection.immutable.{ Queue => ScalaQueue }

// TODO: Add poll, takeUpTo, etc.
class TQueue[A] private (capacity: Int, ref: TRef[ScalaQueue[A]]) {
  final def offer(a: A): STM[Nothing, Unit] =
    for {
      q <- ref.get
      _ <- STM.check(q.length < capacity)
      _ <- ref.update(_ enqueue a)
    } yield ()

  final def size: STM[Nothing, Int] = ref.get.map(_.length)

  final def take: STM[Nothing, A] =
    for {
      q <- ref.get
      a <- q.dequeueOption match {
            case Some((a, as)) =>
              ref.set(as) *> STM.succeed(a)
            case _ => STM.retry
          }
    } yield a
}
object TQueue {
  final def make[A](capacity: Int): STM[Nothing, TQueue[A]] =
    TRef.make(ScalaQueue.empty[A]).map(ref => new TQueue(capacity, ref))
}
