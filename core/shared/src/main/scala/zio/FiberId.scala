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

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The identity of a Fiber, described by the time it began life, and a
 * monotonically increasing sequence number generated from an atomic counter.
 */
sealed trait FiberId extends Serializable { self =>
  import FiberId._

  final def <>(that: FiberId): FiberId = self.combine(that)

  /**
   * Implemented this way and not just pattern matching on (self, that) because
   * of: https://github.com/zio/zio/pull/8746#discussion_r1567448064
   */
  final def combine(that: FiberId): FiberId =
    self match {
      case FiberId.None =>
        that match {
          case FiberId.None => None // (None, None)
          case _            => that // (None, that)
        }
      case _ =>
        that match {
          case FiberId.None => self                          // (self, None)
          case _            => FiberId.Composite(self, that) // (self, that)
        }
    }

  final def getOrElse(that: => FiberId): FiberId = if (isNone) that else self

  final def ids: Set[Int] =
    self match {
      case None              => Set.empty
      case Runtime(id, _, _) => Set(id)
      case Composite(l, r)   => l.ids ++ r.ids
    }

  final def isNone: Boolean =
    self match {
      case None                     => true
      case FiberId.Runtime(_, _, _) => false
      case Composite(l, r) =>
        try l.isNone && r.isNone
        catch {
          // Can stack overflow for deeply nested fiber ids
          case _: StackOverflowError => toSet.forall(_.isNone)
        }
    }

  final def threadName: String = s"zio-fiber-${self.ids.mkString(",")}"

  final def toOption: Option[FiberId] = toSet.asInstanceOf[Set[FiberId]].reduceOption(_.combine(_))

  final def toSet: Set[FiberId.Runtime] =
    self match {
      case None                          => Set.empty[FiberId.Runtime]
      case id @ FiberId.Runtime(_, _, _) => Set(id)
      case Composite(l, r)               => l.toSet ++ r.toSet
    }
}

object FiberId {

  def apply(id: Int, startTimeSeconds: Int, location: Trace): FiberId =
    Runtime(id, startTimeSeconds * 1000L, location)

  private[zio] def make(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime =
    FiberId.Runtime(_fiberCounter.getAndIncrement(), java.lang.System.currentTimeMillis(), location)

  private[zio] val _fiberCounter = new java.util.concurrent.atomic.AtomicInteger(0)

  case object None                                                          extends FiberId
  final case class Runtime(id: Int, startTimeMillis: Long, location: Trace) extends FiberId
  final case class Composite(left: FiberId, right: FiberId)                 extends FiberId
}
