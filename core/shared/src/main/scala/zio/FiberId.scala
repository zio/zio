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

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

/**
 * The identity of a Fiber, described by the time it began life, and a
 * monotonically increasing sequence number generated from an atomic counter.
 */
sealed trait FiberId extends Serializable { self =>

  def ids: List[Int] = {

    @tailrec
    def loop(stack: List[FiberId], result: List[Int]): List[Int] =
      stack match {
        case FiberId.None :: next                   => loop(next, result)
        case FiberId.Runtime(id, _) :: next         => loop(next, id :: result)
        case FiberId.Composite(left, right) :: next => loop(left :: right :: next, result)
        case Nil                                    => result.reverse
      }

    loop(List(self), List.empty)
  }
}

object FiberId {
  case object None                                          extends FiberId
  final case class Runtime(id: Int, startTime: Int)         extends FiberId
  final case class Composite(left: FiberId, right: FiberId) extends FiberId
}
