/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

package zio.stream.experimental

import zio._
import zio.internal.stacktracer.Tracer

/**
 * A `ZPipeline` is a polymorphic stream transformer. Pipelines
 * accept a stream as input, and return the transformed stream as output.
 *
 * Pipelines can be thought of as a recipe for calling a bunch of methods on a
 * source stream, to yield a new (transformed) stream. A nice mental model is
 * the following type alias:
 *
 * {{{
 * type ZPipeline[Env, Err, In, Out] =
 *   ZStream[Env, Err, In] => ZStream[Env, Err, Out]
 * }}}
 *
 * This encoding of a pipeline with a type alias is not used because it does
 * not infer well. In its place, this trait captures the polymorphism inherent
 * to many pipelines, which can therefore be more flexible about the
 * environment and error types of the streams they transform.
 *
 * There is no fundamental requirement for pipelines to exist, because
 * everything pipelines do can be done directly on a stream. However, because
 * pipelines separate the stream transformation from the source stream itself,
 * it becomes possible to abstract over stream transformations at the level of
 * values, creating, storing, and passing around reusable transformation
 * pipelines that can be applied to many different streams.
 *
 * The most common way to create a pipeline is to convert a sink into a
 * pipeline (in general, transforming elements of a stream requires the power
 * of a sink). However, the companion object has lots of other pipeline
 * constructors based on the methods of stream.
 */
trait ZPipeline[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +LowerElem, -UpperElem]
    extends ZPipelineVersionSpecific[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem] { self =>
  type OutEnv[Env]
  type OutErr[Err]
  type OutElem[Elem]

  def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr, Elem >: LowerElem <: UpperElem](
    stream: ZStream[Env, Err, Elem]
  )(implicit
    trace: ZTraceElement
  ): ZStream[OutEnv[Env], OutErr[Err], OutElem[Elem]]
}

object ZPipeline extends ZPipelineCompanionVersionSpecific {

  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +LowerElem, -UpperElem, OutEnv0[Env], OutErr0[Err], Out0[
    Elem
  ]] =
    ZPipeline[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem] {
      type OutEnv[Env]   = OutEnv0[Env]
      type OutErr[Err]   = OutErr0[Err]
      type OutElem[Elem] = Out0[Elem]
    }

  type Identity[A] = A

  def branchAfter[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem, OutElem0[Elem]](n: Int)(
    f: Chunk[UpperElem] => ZPipeline.WithOut[
      LowerEnv,
      UpperEnv,
      LowerErr,
      UpperErr,
      LowerElem,
      UpperElem,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr,
      OutElem0
    ]
  ): ZPipeline.WithOut[
    LowerEnv,
    UpperEnv,
    LowerErr,
    UpperErr,
    LowerElem,
    UpperElem,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    OutElem0
  ] =
    new ZPipeline[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = OutElem0[Elem]
      def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr, Elem >: LowerElem <: UpperElem](
        stream: ZStream[Env, Err, Elem]
      )(implicit trace: ZTraceElement): ZStream[Env, Err, OutElem[Elem]] =
        stream.branchAfter(n)(f)
    }

  /**
   * Creates a pipeline that collects elements with the specified partial function.
   *
   * {{{
   * ZPipeline.collect[Option[Int], Int] { case Some(v) => v }
   * }}}
   */
  def collect[In, Out](
    f: PartialFunction[In, Out]
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Out })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Out
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Out] =
        stream.collect(f)
    }

  /**
   * Creates a pipeline that drops elements until the specified predicate evaluates to true.
   *
   * {{{
   * ZPipeline.dropUntil[Int](_ > 100)
   * }}}
   */
  def dropUntil[In](
    f: In => Boolean
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        stream.dropUntil(f)
    }

  /**
   * Creates a pipeline that drops elements while the specified predicate evaluates to true.
   *
   * {{{
   * ZPipeline.dropWhile[Int](_ <= 100)
   * }}}
   */
  def dropWhile[In](
    f: In => Boolean
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        stream.dropWhile(f)
    }

  /**
   * Creates a pipeline that filters elements according to the specified predicate.
   */
  def filter[In](
    f: In => Boolean
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        stream.filter(f)
    }

  /**
   * Creates a pipeline that groups on adjacent keys, calculated by the
   * specified keying function.
   */
  def groupAdjacentBy[In, Key](f: In => Key): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = (Key, NonEmptyChunk[Elem]) })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = (Key, NonEmptyChunk[Elem])
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, (Key, NonEmptyChunk[Elem])] =
        stream.groupAdjacentBy(f)
    }

  /**
   * The identity pipeline, which does not modify streams in any way.
   */
  val identity: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, Any] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        stream
    }

  /**
   * Creates a pipeline that maps elements with the specified function.
   */
  def map[In, Out](
    f: In => Out
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Out })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Out
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Out] =
        stream.map(f)
    }

  /**
   * Emits the provided chunk before emitting any other value.
   */
  def prepend[In](values: Chunk[In]): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    In,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, In, Any] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem >: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        ZStream.fromChunk(values) ++ stream
    }

  /**
   * Creates a pipeline that provides the specified environment.
   */
  def provide[Env](
    env: Env
  ): ZPipeline.WithOut[
    Env,
    Any,
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Any })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Env, Any, Nothing, Any, Nothing, Any] {
      type OutEnv[Env]   = Any
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env1 >: Env, Err, In](stream: ZStream[Env1, Err, In])(implicit
        trace: ZTraceElement
      ): ZStream[Any, Err, In] =
        stream.provide(env)
    }

  /**
   * A pipeline that rechunks the stream into chunks of the specified size.
   */
  def rechunk(n: Int): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, Any] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        stream.rechunk(n)
    }

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scan[In, Out](
    s: Out
  )(
    f: (Out, In) => Out
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Out })#OutElem
  ] =
    scanZIO(s)((out, in) => ZIO.succeedNow(f(out, in)))

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scanZIO[Env, Err, In, Out](
    s: Out
  )(
    f: (Out, In) => ZIO[Env, Err, Out]
  ): ZPipeline.WithOut[
    Nothing,
    Env,
    Err,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Out })#OutElem
  ] =
    new ZPipeline[Nothing, Env, Err, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Out
      def apply[Env1 <: Env, Err1 >: Err, Elem <: In](stream: ZStream[Env1, Err1, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env1, Err1, Out] =
        stream.scanZIO(s)(f)
    }

  /**
   * Creates a pipeline that takes elements until the specified predicate evaluates to true.
   */
  def takeUntil[In](
    f: In => Boolean
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        stream.takeUntil(f)
    }

  /**
   * Creates a pipeline that takes elements while the specified predicate evaluates to true.
   */
  def takeWhile[In](
    f: In => Boolean
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    In,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, In] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env, Err, Elem <: In](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Elem] =
        stream.takeWhile(f)
    }

  trait Compose[+LeftLower, -LeftUpper, LeftOut[In], +RightLower, -RightUpper, RightOut[In]] {
    type Lower
    type Upper
    type Out[In]
  }

  object Compose extends ComposeLowPriorityImplicits {
    type WithOut[LeftLower, LeftUpper, LeftOut[In], RightLower, RightUpper, RightOut[In], Lower0, Upper0, Out0[In]] =
      Compose[LeftLower, LeftUpper, LeftOut, RightLower, RightUpper, RightOut] {
        type Lower   = Lower0
        type Upper   = Upper0
        type Out[In] = Out0[In]
      }

    implicit def compose[
      LeftLower,
      LeftUpper,
      LeftOut >: RightLower <: RightUpper,
      RightLower,
      RightUpper,
      RightOut
    ]: Compose.WithOut[
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out,
      LeftLower,
      LeftUpper,
      ({ type Out[In] = RightOut })#Out
    ] =
      new Compose[
        LeftLower,
        LeftUpper,
        ({ type Out[In] = LeftOut })#Out,
        RightLower,
        RightUpper,
        ({ type Out[In] = RightOut })#Out
      ] {
        type Lower   = LeftLower
        type Upper   = LeftUpper
        type Out[In] = RightOut
      }

    implicit def identity[LeftLower <: RightLower, LeftUpper, RightLower, RightUpper]: Compose.WithOut[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      Identity,
      RightLower,
      LeftUpper with RightUpper,
      Identity
    ] =
      new Compose[
        LeftLower,
        LeftUpper,
        Identity,
        RightLower,
        RightUpper,
        Identity
      ] {
        type Lower   = RightLower
        type Upper   = LeftUpper with RightUpper
        type Out[In] = In
      }

    implicit def leftIdentity[LeftLower <: RightLower, LeftUpper, RightLower, RightUpper, RightOut]: Compose.WithOut[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out,
      RightLower,
      LeftUpper with RightUpper,
      ({ type Out[In] = RightOut })#Out
    ] =
      new Compose[
        LeftLower,
        LeftUpper,
        Identity,
        RightLower,
        RightUpper,
        ({ type Out[In] = RightOut })#Out
      ] {
        type Lower   = RightLower
        type Upper   = LeftUpper with RightUpper
        type Out[In] = RightOut
      }

    implicit def rightIdentity[LeftLower, LeftUpper, LeftOut >: RightLower <: RightUpper, RightLower, RightUpper]
      : Compose.WithOut[
        LeftLower,
        LeftUpper,
        ({ type Out[In] = LeftOut })#Out,
        RightLower,
        RightUpper,
        Identity,
        LeftLower,
        LeftUpper,
        ({ type Out[In] = LeftOut })#Out
      ] =
      new Compose[
        LeftLower,
        LeftUpper,
        ({ type Out[In] = LeftOut })#Out,
        RightLower,
        RightUpper,
        Identity
      ] {
        type Lower   = LeftLower
        type Upper   = LeftUpper
        type Out[In] = LeftOut
      }
  }

  trait ComposeLowPriorityImplicits {

    implicit def identityLowPriority[LeftLowerElem, LeftUpperElem, RightLowerElem <: LeftLowerElem, RightUpperElem]
      : Compose.WithOut[
        LeftLowerElem,
        LeftUpperElem,
        Identity,
        RightLowerElem,
        RightUpperElem,
        Identity,
        LeftLowerElem,
        LeftUpperElem with RightUpperElem,
        Identity
      ] =
      new Compose[
        LeftLowerElem,
        LeftUpperElem,
        Identity,
        RightLowerElem,
        RightUpperElem,
        Identity
      ] {
        type Lower   = LeftLowerElem
        type Upper   = LeftUpperElem with RightUpperElem
        type Out[In] = In
      }

    implicit def leftIdentityLowPriority[LeftLower, LeftUpper, RightLower <: LeftLower, RightUpper, RightOut]
      : Compose.WithOut[
        LeftLower,
        LeftUpper,
        Identity,
        RightLower,
        RightUpper,
        ({ type Out[In] = RightOut })#Out,
        LeftLower,
        LeftUpper with RightUpper,
        ({ type Out[In] = RightOut })#Out
      ] =
      new Compose[
        LeftLower,
        LeftUpper,
        Identity,
        RightLower,
        RightUpper,
        ({ type Out[In] = RightOut })#Out
      ] {
        type Lower   = LeftLower
        type Upper   = LeftUpper with RightUpper
        type Out[In] = RightOut
      }
  }
}
