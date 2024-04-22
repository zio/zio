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

import java.util.concurrent.ThreadLocalRandom

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
      case None =>
        that match {
          case None => None // (None, None)
          case _    => that // (None, that)
        }
      case _ =>
        that match {
          case None => self                  // (self, None)
          case _    => Composite(self, that) // (self, that)
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
      case None             => true
      case Runtime(_, _, _) => false
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
      case None                  => Set.empty[FiberId.Runtime]
      case id @ Runtime(_, _, _) => Set(id)
      case Composite(l, r)       => l.toSet ++ r.toSet
    }
}

object FiberId {

  def apply(id: Int, startTimeSeconds: Int, location: Trace): FiberId =
    Runtime(id, startTimeSeconds * 1000L, location)

  @deprecated("use `generate` instead", "1.0.0")
  private[zio] def make(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime =
    Gen.Random.make(location)

  private[zio] def generate(fiberRefs: FiberRefs)(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime =
    fiberRefs.getOrDefault(FiberRef.currentFiberIdGenerator).make(location)

  case object None                                                          extends FiberId
  final case class Runtime(id: Int, startTimeMillis: Long, location: Trace) extends FiberId
  final case class Composite(left: FiberId, right: FiberId)                 extends FiberId

  private[zio] trait Gen {
    def make(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime
  }

  private[zio] object Gen {

    /**
     * Generates a fiber ID where the `id` is a random integer.
     *
     * This is more performant than using `FiberId.Gen.Ordered`, but cannot be
     * used in cases that rely on strict ordering of fibers (e.g., in zio-test)
     */
    object Random extends Gen {
      def make(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime = {
        val id = ThreadLocalRandom.current().nextInt(Int.MaxValue)
        FiberId.Runtime(id, java.lang.System.currentTimeMillis(), location)
      }
    }

    /**
     * Generates a fiber ID where the `id` is a monotonically increasing
     * integer.
     *
     * This is less performant than generating IDs randomly, but is required for
     * cases that rely on strict ordering of fibers (e.g., in zio-test)
     */
    object Monotonic extends Gen {
      private[this] val counter = new java.util.concurrent.atomic.AtomicInteger(0)
      def make(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime =
        FiberId.Runtime(counter.getAndIncrement(), java.lang.System.currentTimeMillis(), location)
    }
  }

}
