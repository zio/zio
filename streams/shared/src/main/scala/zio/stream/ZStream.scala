/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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


package zio.stream


import zio._
import zio.internal.{SingleThreadedRingBuffer, UniqueKey}
import zio.metrics.MetricLabel
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm._
import zio.stream.ZStream.{DebounceState, HandoffSignal, zipChunks}
import zio.stream.internal.{ZInputStream, ZReader}


import java.io.{IOException, InputStream}
import scala.collection.mutable
import scala.reflect.ClassTag


final class ZStream[-R, +E, +A] private (val channel: ZChannel[R, Any, Any, Any, E, Chunk[A], Any]) { self =>


  import ZStream.HaltStrategy


  /**
   * Returns a new ZStream that applies the specified aspect to this ZStream.
   * Aspects are "transformers" that modify the behavior of their input in some
   * well-defined way (for example, adding a timeout).
   */
  def @@[LowerR <: UpperR, UpperR <: R, LowerE >: E, UpperE >: LowerE, LowerA >: A, UpperA >: LowerA](
    aspect: => ZStreamAspect[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA]
  )(implicit trace: Trace): ZStream[UpperR, LowerE, LowerA] =
    ZStream.suspend(aspect(self))


  /**
   * Symbolic alias for [[ZStream#cross]].
   */
  def <*>[R1 <: R, E1 >: E, A2](that: => ZStream[R1, E1, A2])(implicit
    zippable: Zippable[A, A2],
    trace: Trace
  ): ZStream[R1, E1, zippable.Out] =
    self cross that


  /**
   * Symbolic alias for [[ZStream#crossLeft]].
   */
  def <*[R1 <: R, E1 >: E, A2](that: => ZStream[R1, E1, A2])(implicit trace: Trace): ZStream[R1, E1, A] =
    self crossLeft that


  /**
   * Symbolic alias for [[ZStream#crossRight]].
   */
  def *>[R1 <: R, E1 >: E, A2](that: => ZStream[R1, E1, A2])(implicit trace: Trace): ZStream[R1, E1, A2] =
    self crossRight that


  /**
   * Symbolic alias for [[ZStream#zip]].
   */
  def <&>[R1 <: R, E1 >: E, A2](that: => ZStream[R1, E1, A2])(implicit
    zippable: Zippable[A, A2],
    trace: Trace
  ): ZStream[R1, E1, zippable.Out] =
    self zip that


  /**
   * Symbolic alias for [[ZStream#zipLeft]].
   */
  def <&[R1 <: R, E1 >: E, A2](that: => ZStream[R1, E1, A2])(implicit trace: Trace): ZStream[R1, E1, A] =
