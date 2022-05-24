/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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
  import FiberId._

  final def <>(that: FiberId): FiberId = self.combine(that)

  final def combine(that: FiberId): FiberId =
    (self, that) match {
      case (None, that)                                       => that
      case (that, None)                                       => that
      case (Composite(self), Composite(that))                 => Composite(self | that)
      case (Composite(self), that @ Runtime(_, _, _))         => Composite(self + that)
      case (self @ Runtime(_, _, _), Composite(that))         => Composite(that + self)
      case (self @ Runtime(_, _, _), that @ Runtime(_, _, _)) => Composite(Set(self, that))
    }

  final def getOrElse(that: => FiberId): FiberId = if (isNone) that else self

  final def ids: Set[Int] =
    self match {
      case None                => Set.empty
      case Runtime(id, _, _)   => Set(id)
      case Composite(fiberIds) => fiberIds.map(_.id)
    }

  final def isNone: Boolean =
    self match {
      case None           => true
      case Composite(set) => set.forall(_.isNone)
      case _              => false
    }

  final def threadName: String = s"zio-fiber-${self.ids.mkString(",")}"

  final def toOption: Option[FiberId] =
    self match {
      case None           => Option.empty[FiberId]
      case Composite(set) => set.map(_.toOption).collect { case Some(fiberId) => fiberId }.reduceOption(_.combine(_))
      case other          => Some(other)
    }

  final def toSet: Set[FiberId.Runtime] = self match {
    case None                          => Set.empty[FiberId.Runtime]
    case Composite(set)                => set
    case id @ FiberId.Runtime(_, _, _) => Set(id)
  }
}

object FiberId {

  def apply(id: Int, startTimeSeconds: Int, location: ZTraceElement): FiberId =
    Runtime(id, startTimeSeconds, location)

  def combineAll(fiberIds: Set[FiberId]): FiberId =
    fiberIds.foldLeft[FiberId](FiberId.None)(_ combine _)

  private[zio] def unsafeMake(location: ZTraceElement): FiberId.Runtime =
    FiberId.Runtime(_fiberCounter.getAndIncrement(), (java.lang.System.currentTimeMillis / 1000).toInt, location)

  private[zio] val _fiberCounter = new java.util.concurrent.atomic.AtomicInteger(0)

  case object None                                                                  extends FiberId
  final case class Runtime(id: Int, startTimeSeconds: Int, location: ZTraceElement) extends FiberId
  final case class Composite(fiberIds: Set[FiberId.Runtime])                        extends FiberId
}
