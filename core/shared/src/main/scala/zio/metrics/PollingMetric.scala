/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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
package zio.metrics

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio._
import zio.metrics._

/**
 * A `PollingMetric[Type, Out]` is a combination of a metric and an effect that
 * polls for updates to the metric.
 */
trait PollingMetric[-R, +E, +Out] { self =>
  type Type
  type In

  /**
   * Create a new `PollingMetric` that will poll on the blocking thread pool.
   */
  final def blocking: PollingMetric.Full[Type, In, R, E, Out] =
    new PollingMetric[R, E, Out] {
      type Type = self.Type
      type In   = self.In

      def metric = self.metric

      def poll(implicit trace: ZTraceElement) = ZIO.blocking(self.poll)
    }

  /**
   * The metric that this `PollingMetric` polls to update.
   */
  def metric: Metric[Type, In, Out]

  /**
   * Returns an effect that will launch the polling metric in a background
   * fiber, using the specified schedule.
   */
  final def launch[R1 <: R, B](schedule: Schedule[R1, Out, B])(implicit
    trace: ZTraceElement
  ): ZIO[R1 with Scope, Nothing, Fiber[E, B]] =
    ZIO.acquireRelease((pollAndUpdate *> metric.value).repeat(schedule).forkDaemon)(_.interrupt)

  /**
   * An effect that polls a value that may be fed to the metric.
   */
  def poll(implicit trace: ZTraceElement): ZIO[R, E, In]

  /**
   * An effect that polls for a value and uses the value to update the metric.
   */
  final def pollAndUpdate(implicit trace: ZTraceElement): ZIO[R, E, Unit] =
    poll.flatMap(metric.update(_))

  /**
   * Returns a new polling metric whose poll function will be retried with the
   * specified retry policy.
   */
  final def retry[R1 <: R, E1 >: E](
    policy: Schedule[R1, E1, Any]
  ): PollingMetric.Full[Type, In, R1, E1, Out] =
    new PollingMetric[R1, E1, Out] {
      type Type = self.Type
      type In   = self.In

      def metric = self.metric

      def poll(implicit trace: ZTraceElement) = self.poll.retry(policy)
    }

  /**
   * Zips this polling metric with the specified polling metric.
   */
  final def zip[R1 <: R, E1 >: E, Out2](that: PollingMetric[R1, E1, Out2])(implicit
    z1: Zippable[self.Type, that.Type],
    z2: Zippable[Out, Out2]
  ): PollingMetric.Full[z1.Out, (self.In, that.In), R1, E1, z2.Out] =
    new PollingMetric[R1, E1, z2.Out] {
      type Type = z1.Out
      type In   = (self.In, that.In)

      def metric: Metric[z1.Out, In, z2.Out] =
        self.metric.zip(that.metric)

      def poll(implicit trace: ZTraceElement): ZIO[R1, E1, In] =
        self.poll.zip(that.poll)
    }
}
object PollingMetric {
  type Full[Type0, In0, -R, +E, +Out] =
    PollingMetric[R, E, Out] { type Type = Type0; type In = In0 }

  /**
   * Constructs a new polling metric from a metric and poll effect.
   */
  def apply[Type0, In0, R, E, Out](
    metric0: Metric[Type0, In0, Out],
    poll0: ZIO[R, E, In0]
  ): PollingMetric[R, E, Out] { type Type = Type0; type In = In0 } =
    new PollingMetric[R, E, Out] {
      type Type = Type0
      type In   = In0

      def metric: Metric[Type, In, Out] = metric0

      def poll(implicit trace: ZTraceElement): ZIO[R, E, In] = poll0
    }

  /**
   * Collects all of the polling metrics into a single polling metric, which
   * polls for, updates, and produces the outputs of all individual metrics.
   */
  def collectAll[R, E, Out](
    in0: Iterable[PollingMetric[R, E, Out]]
  ): PollingMetric[R, E, Chunk[Out]] =
    new PollingMetric[R, E, Chunk[Out]] {
      val in = Chunk.fromIterable(in0)
      type Type = Chunk[Any]
      type In   = Chunk[Any]

      def metric: Metric[Type, In, Chunk[Out]] = {
        val start =
          Metric.succeed(Chunk.empty).mapType(_ => Chunk.empty)

        in.zipWithIndex.foldLeft[Metric[Type, In, Chunk[Out]]](start) { case (acc, (metric, index)) =>
          metric.metric.mapType(Chunk(_)).map(Chunk(_)).contramap[In](chunk => chunk(index).asInstanceOf[metric.In])
        }
      }

      def poll(implicit trace: ZTraceElement): ZIO[R, E, In] = ZIO.foreach(in)(_.poll)
    }
}
