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

package zio.stm

import com.github.ghik.silencer.silent

import scala.collection.immutable.{ Queue => ScalaQueue }

final class TQueue[A] private (val capacity: Int, ref: TRef[ScalaQueue[A]]) {
  def offer(a: A): STM[Nothing, Unit] =
    for {
      q <- ref.get
      _ <- STM.check(q.length < capacity)
      _ <- ref.update(_.enqueue(a))
    } yield ()

  // TODO: Scala doesn't allow Iterable???
  @silent("enqueueAll")
  def offerAll(as: List[A]): STM[Nothing, Unit] =
    ref.update(_.enqueue(as)).unit

  def poll: STM[Nothing, Option[A]] = takeUpTo(1).map(_.headOption)

  def size: STM[Nothing, Int] = ref.get.map(_.length)

  def take: STM[Nothing, A] =
    ref.get.flatMap { q =>
      q.dequeueOption match {
        case Some((a, as)) =>
          ref.set(as) *> STM.succeed(a)
        case _ => STM.retry
      }
    }

  def takeAll: STM[Nothing, List[A]] =
    ref.modify(q => (q.toList, ScalaQueue.empty[A]))

  def takeUpTo(max: Int): STM[Nothing, List[A]] =
    ref.get
      .map(_.splitAt(max))
      .flatMap(split => ref.set(split._2) *> STM.succeed(split._1))
      .map(_.toList)
}

object TQueue {
  def make[A](capacity: Int): STM[Nothing, TQueue[A]] =
    TRef.make(ScalaQueue.empty[A]).map(ref => new TQueue(capacity, ref))
}
