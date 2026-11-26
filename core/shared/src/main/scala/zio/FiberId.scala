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
import scala.util.control.TailCalls

/**
 * The identity of a Fiber. A [[FiberId.Runtime]] identifies one fiber by a
 * number, the time it began life and the location it was forked from. By
 * default the number is a random non-negative `Int`; in the ZIO Test
 * environment it is a monotonically increasing sequence number generated from
 * an atomic counter. [[FiberId.None]] is the identity of no fiber, and
 * [[FiberId.Composite]] combines several identities, for example the identity
 * of a fiber built with `Fiber#zip`.
 *
 * {{{
 * val first: FiberId    = FiberId(1, 1700000000, Trace.empty)
 * val second: FiberId   = FiberId(2, 1700000000, Trace.empty)
 * val combined: FiberId = first <> second
 *
 * val ids: Set[Int] = combined.ids
 * // Set(1, 2)
 * val name: String = combined.threadName
 * // zio-fiber-1,2
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/fiber/fiberid/ FiberId]]
 */
sealed trait FiberId extends Serializable { self =>
  import FiberId._

  /** A symbolic alias for `combine`. */
  final def <>(that: FiberId): FiberId = self.combine(that)

  /**
   * Combines this identity with the specified one: returns the other one when
   * either is `FiberId.None`, and a `FiberId.Composite` of both otherwise.
   *
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

  /**
   * Returns `that` when this identity is none (see `isNone`), else this one.
   */
  final def getOrElse(that: => FiberId): FiberId = if (isNone) that else self

  /**
   * Returns the numbers of the runtime fiber ids that make up this identity.
   */
  final def ids: Set[Int] = try {
    FiberId.ids(self)
  } catch {
    // Can stack overflow for deeply nested fiber ids
    case _: StackOverflowError => FiberId.toSetLazy(self).map(_.id)
  }

  /**
   * Returns `true` when this identity contains no runtime fiber id:
   * `FiberId.None`, or a `FiberId.Composite` made only of `FiberId.None`.
   */
  final def isNone: Boolean = try {
    FiberId.isNone(self)
  } catch {
    // Can stack overflow for deeply nested fiber ids
    case _: StackOverflowError => FiberId.toSetLazy(self).isEmpty
  }

  /**
   * Returns the name used for this identity in traces and fiber dumps:
   * `zio-fiber-` followed by the numbers of its runtime fiber ids, separated by
   * commas (`zio-fiber-` alone for `FiberId.None`).
   */
  final def threadName: String =
    threadNameInto(new StringBuilder(32))(Unsafe).result()

  private[zio] final def threadNameInto(sb: StringBuilder)(implicit unsafe: Unsafe): StringBuilder = {
    sb.append("zio-fiber-")
    self match {
      case rt: Runtime => sb.append(rt.id) // Avoid boxing of the Int
      case fid =>
        val it   = fid.ids.iterator
        var loop = it.hasNext
        while (loop) {
          sb.append(it.next())
          if (it.hasNext) sb.append(',')
          else loop = false
        }
    }
    sb
  }

  /**
   * Returns `None` when this identity is none (see `isNone`), else `Some` of
   * its runtime fiber ids combined.
   */
  final def toOption: Option[FiberId] = toSet.asInstanceOf[Set[FiberId]].reduceOption(_.combine(_))

  /** Returns the runtime fiber ids that make up this identity. */
  final def toSet: Set[FiberId.Runtime] = try {
    FiberId.toSet(self)
  } catch {
    // Can stack overflow for deeply nested fiber ids
    case _: StackOverflowError => FiberId.toSetLazy(self)
  }
}

/**
 * The cases of [[FiberId]].
 */
object FiberId {

  /**
   * Creates a [[FiberId.Runtime]] from a number, a start time in seconds
   * (stored in milliseconds) and a location.
   */
  def apply(id: Int, startTimeSeconds: Int, location: Trace): FiberId =
    Runtime(id, startTimeSeconds * 1000L, location)

  @deprecated("use `generate` instead", "1.0.0")
  private[zio] def make(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime =
    Gen.Live.make(location)

  private[zio] def generate(fiberRefs: FiberRefs)(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime =
    fiberRefs.getOrDefault(FiberRef.currentFiberIdGenerator).make(location)

  /** The identity of no fiber, as used by fibers made from a value. */
  case object None extends FiberId

  /**
   * The identity of one runtime fiber: its number, the time it started (in
   * milliseconds since the epoch) and the location it was forked from.
   */
  final case class Runtime(id: Int, startTimeMillis: Long, location: Trace) extends FiberId

  /** Two identities combined, as made by `<>` and `combine`. */
  final case class Composite(left: FiberId, right: FiberId) extends FiberId

  private def ids(id: FiberId): Set[Int] =
    id match {
      case None              => Set.empty[Int]
      case Runtime(id, _, _) => Set(id)
      case Composite(l, r)   => ids(l) ++ ids(r)
    }

  private def isNone(id: FiberId): Boolean =
    id match {
      case None             => true
      case Runtime(_, _, _) => false
      case Composite(l, r)  => isNone(l) && isNone(r)
    }

  private def toSet(id: FiberId): Set[FiberId.Runtime] =
    id match {
      case None                  => Set.empty[FiberId.Runtime]
      case id @ Runtime(_, _, _) => Set(id)
      case Composite(l, r)       => toSet(l) ++ toSet(r)
    }

  private def toSetLazy(id: FiberId): Set[FiberId.Runtime] = {
    def go(id: FiberId): TailCalls.TailRec[Set[FiberId.Runtime]] =
      id match {
        case None                  => TailCalls.done(Set.empty[FiberId.Runtime])
        case id @ Runtime(_, _, _) => TailCalls.done(Set(id))
        case Composite(l, r) =>
          for {
            lSet <- TailCalls.tailcall(go(l))
            rSet <- TailCalls.tailcall(go(r))
          } yield lSet ++ rSet
      }

    go(id).result
  }

  private[zio] trait Gen {

    /** Generates a new runtime fiber id for a fiber forked at `location`. */
    def make(location: Trace)(implicit unsafe: Unsafe): FiberId.Runtime
  }

  private[zio] object Gen {

    /**
     * Generates a fiber ID where the `id` is a random integer.
     *
     * This is more performant than using `FiberId.Gen.Monotonic`, but cannot be
     * used in cases that rely on strict ordering of fibers (e.g., in zio-test)
     */
    object Live extends Gen {
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
