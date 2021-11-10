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

package zio.stream

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.internal.CharacterSet.{BOM, CharsetUtf32BE, CharsetUtf32LE}

import java.nio.charset.{Charset, StandardCharsets}

/**
 * A `ZPipeline` is a polymorphic stream transformer. Pipelines accept a stream
 * as input, and return the transformed stream as output.
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
 * This encoding of a pipeline with a type alias is not used because it does not
 * infer well. In its place, this trait captures the polymorphism inherent to
 * many pipelines, which can therefore be more flexible about the environment
 * and error types of the streams they transform.
 *
 * There is no fundamental requirement for pipelines to exist, because
 * everything pipelines do can be done directly on a stream. However, because
 * pipelines separate the stream transformation from the source stream itself,
 * it becomes possible to abstract over stream transformations at the level of
 * values, creating, storing, and passing around reusable transformation
 * pipelines that can be applied to many different streams.
 *
 * The most common way to create a pipeline is to convert a sink into a pipeline
 * (in general, transforming elements of a stream requires the power of a sink).
 * However, the companion object has lots of other pipeline constructors based
 * on the methods of stream.
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

object ZPipeline extends ZPipelineCompanionVersionSpecific with ZPipelinePlatformSpecificConstructors {

  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +LowerElem, -UpperElem, OutEnv0[Env], OutErr0[Err], Out0[
    Elem
  ]] =
    ZPipeline[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem] {
      type OutEnv[Env]   = OutEnv0[Env]
      type OutErr[Err]   = OutErr0[Err]
      type OutElem[Elem] = Out0[Elem]
    }

  type Identity[A] = A

  def branchAfter[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem, OutElem[Elem]](n: Int)(
    f: Chunk[UpperElem] => ZPipeline.WithOut[
      LowerEnv,
      UpperEnv,
      LowerErr,
      UpperErr,
      LowerElem,
      UpperElem,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr,
      ({ type OutElem[Elem] = Elem })#OutElem
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
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = Err
      type OutElem[Elem] = Elem
      def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr, Elem >: LowerElem <: UpperElem](
        stream: ZStream[Env, Err, Elem]
      )(implicit trace: ZTraceElement): ZStream[Env, Err, Elem] =
        stream.branchAfter(n)(f)
    }

  /**
   * Creates a pipeline that collects elements with the specified partial
   * function.
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
   * Creates a pipeline that drops elements until the specified predicate
   * evaluates to true.
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
   * Creates a pipeline that drops elements while the specified predicate
   * evaluates to true.
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
   * Creates a pipeline that filters elements according to the specified
   * predicate.
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
   * Creates a pipeline that sends all the elements through the given channel
   */
  def fromChannel[InEnv, OutEnv0, InErr, OutErr0, In, Out](
    channel: ZChannel[OutEnv0, InErr, Chunk[In], Any, OutErr0, Chunk[Out], Any]
  ): ZPipeline.WithOut[
    OutEnv0,
    InEnv,
    InErr,
    InErr,
    In,
    In,
    ({ type OutEnv[Env] = OutEnv0 })#OutEnv,
    ({ type OutErr[Err] = OutErr0 })#OutErr,
    ({ type OutElem[Elem] = Out })#OutElem
  ] = new ZPipeline[OutEnv0, InEnv, InErr, InErr, In, In] {
    override type OutEnv[Env]   = OutEnv0
    override type OutErr[Err]   = OutErr0
    override type OutElem[Elem] = Out

    override def apply[Env >: OutEnv0 <: InEnv, Err >: InErr <: InErr, Elem >: In <: In](
      stream: ZStream[Env, Err, Elem]
    )(implicit trace: ZTraceElement): ZStream[OutEnv0, OutErr0, Out] =
      stream.pipeThroughChannel(channel)
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

  def iso_8859_1Decode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    textDecodeUsing(StandardCharsets.ISO_8859_1)

  def iso_8859_1Encode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.ISO_8859_1)

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
   * Creates a pipeline that maps chunks of elements with the specified
   * function.
   */
  def mapChunks[In, Out](
    f: Chunk[In] => Chunk[Out]
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
        stream.mapChunks(f)
    }

  /**
   * Creates a pipeline that maps elements with the specified function.
   */
  def mapError[InError, OutError](
    f: InError => OutError
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    InError,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = OutError })#OutErr,
    ({ type OutElem[Elem] = Elem })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, InError, Nothing, Any] {
      type OutEnv[Env]   = Env
      type OutErr[Err]   = OutError
      type OutElem[Elem] = Elem
      def apply[Env, Err <: InError, Elem](stream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, OutError, Elem] =
        stream.mapError(f)
    }

  /**
   * Creates a pipeline that maps elements with the specified effect.
   */
  def mapZIO[R1, E1, In, Out](
    f: In => ZIO[R1, E1, Out]
  ): ZPipeline.WithOut[
    R1,
    Any,
    Nothing,
    E1,
    Nothing,
    In,
    ({ type OutEnv[Env] = R1 })#OutEnv,
    ({ type OutErr[Err] = E1 })#OutErr,
    ({ type OutElem[Elem] = Out })#OutElem
  ] =
    new ZPipeline[R1, Any, Nothing, E1, Nothing, In] {
      type OutEnv[Env]   = R1
      type OutErr[Err]   = E1
      type OutElem[Elem] = Out
      def apply[R >: R1, E <: E1, A <: In](stream: ZStream[R, E, A])(implicit
        trace: ZTraceElement
      ): ZStream[R1, E1, Out] =
        stream.mapZIO(f)
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
   * Splits strings on a delimiter.
   */
  def splitOn(delimiter: String): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, String] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = String
      override def apply[Env, Err, Elem <: String](
        stream: ZStream[Env, Err, Elem]
      )(implicit trace: ZTraceElement): ZStream[Env, Err, String] =
        stream
          .map(str => Chunk.fromArray(str.toArray))
          .mapChunks(_.flatten)
          .splitOnChunk(Chunk.fromArray(delimiter.toArray))
          .map(_.mkString(""))
    }

  /**
   * Splits strings on a delimiter.
   */
  def splitOnChunk[A](delimiter: Chunk[A]): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    A,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = A })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, A] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = A
      override def apply[Env, Err, Elem <: A](
        stream: ZStream[Env, Err, Elem]
      )(implicit trace: ZTraceElement): ZStream[Env, Err, A] =
        stream
          .splitOnChunk(delimiter)
          .flattenChunks
    }

  /**
   * Splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX
   * newlines (`\n`).
   */
  def splitLines: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, String] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = String
      override def apply[Env, Err, Elem <: String](
        stream: ZStream[Env, Err, Elem]
      )(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, String] = {
        def next(
          leftover: Option[String],
          wasSplitCRLF: Boolean
        ): ZChannel[Env, Err, Chunk[String], Any, Err, Chunk[String], Any] =
          ZChannel.readWithCause[Env, Err, Chunk[String], Any, Err, Chunk[String], Any](
            incomingChunk => {
              val buffer = collection.mutable.ArrayBuffer.empty[String]
              var inCRLF = wasSplitCRLF
              var carry  = leftover getOrElse ""

              incomingChunk foreach { string =>
                val concatenated = carry concat string

                if (concatenated.nonEmpty) {

                  // If we had a split CRLF, start reading from the last character of the leftover, which was the '\r'
                  // Otherwise we just skip over the entire previous leftover, as it doesn't contain a newline.
                  val continueFrom =
                    if (inCRLF && carry.nonEmpty) carry.length - 1
                    else carry.length

                  concatenated.zipWithIndex
                    .drop(continueFrom)
                    .foldLeft((0, false, inCRLF)) { case ((sliceStart, skipNext, midCRLF), (char, index)) =>
                      if (skipNext) (sliceStart, false, false)
                      else
                        char match {
                          case '\n' =>
                            buffer += concatenated.substring(sliceStart, index)
                            (index + 1, false, midCRLF)
                          case '\r' =>
                            if (index + 1 < concatenated.length && concatenated(index + 1) == '\n') {
                              buffer += concatenated.substring(sliceStart, index)
                              (index + 2, true, false)
                            } else if (index == concatenated.length - 1)
                              (sliceStart, false, true)
                            else (index, false, false)
                          case _ => (sliceStart, false, midCRLF)
                        }
                    } match {
                    case (sliceStart, _, midCRLF) =>
                      carry = concatenated.drop(sliceStart)
                      inCRLF = midCRLF
                  }
                }
              }

              ZChannel.write(Chunk.fromArray(buffer.toArray)) *>
                next(if (carry.nonEmpty) Some(carry) else None, inCRLF)
            },
            halt =>
              leftover match {
                case Some(value) => ZChannel.write(Chunk.single(value)) *> ZChannel.failCause(halt)
                case None        => ZChannel.failCause(halt)
              },
            done =>
              leftover match {
                case Some(value) => ZChannel.write(Chunk.single(value)) *> ZChannel.succeed(done)
                case None        => ZChannel.succeed(done)
              }
          )

        new ZStream[Env, Err, String](stream.channel >>> next(None, wasSplitCRLF = false))
      }
    }

  /**
   * Creates a pipeline that takes elements until the specified predicate
   * evaluates to true.
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
   * Creates a pipeline that takes elements while the specified predicate
   * evaluates to true.
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

  def usASCIIDecode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    textDecodeUsing(StandardCharsets.US_ASCII)

  /**
   * utfDecode determines the right encoder to use based on the Byte Order Mark
   * (BOM). If it doesn't detect one, it defaults to utf8Decode. In the case of
   * utf16 and utf32 without BOM, `utf16Decode` and `utf32Decode` should be used
   * instead as both default to their own default decoder respectively.
   */
  def utfDecode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeDetectingBom(
      bomSize = 4,
      {
        case bytes @ BOM.Utf32BE if Charset.isSupported(CharsetUtf32BE.name) =>
          bytes -> utf32BEDecode
        case bytes @ BOM.Utf32LE if Charset.isSupported(CharsetUtf32LE.name) =>
          bytes -> utf32LEDecode
        case bytes if bytes.take(3) == BOM.Utf8 =>
          bytes.drop(3) -> utf8DecodeNoBom
        case bytes if bytes.take(2) == BOM.Utf16BE =>
          bytes.drop(2) -> utf16BEDecode
        case bytes if bytes.take(2) == BOM.Utf16LE =>
          bytes.drop(2) -> utf16LEDecode
        case bytes =>
          bytes -> utf8DecodeNoBom
      }
    )

  def utf8Decode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeDetectingBom(
      bomSize = 3,
      {
        case BOM.Utf8 =>
          Chunk.empty -> utf8DecodeNoBom
        case bytes =>
          bytes -> utf8DecodeNoBom
      }
    )

  def utf16Decode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeDetectingBom(
      bomSize = 2,
      {
        case BOM.Utf16BE =>
          Chunk.empty -> utf16BEDecode
        case BOM.Utf16LE =>
          Chunk.empty -> utf16LEDecode
        case bytes =>
          bytes -> utf16BEDecode
      }
    )

  def utf16BEDecode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeFixedLength(StandardCharsets.UTF_16BE, fixedLength = 2)

  def utf16LEDecode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeFixedLength(StandardCharsets.UTF_16LE, fixedLength = 2)

  def utf32Decode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeDetectingBom(
      bomSize = 4,
      {
        case bytes @ BOM.Utf32LE =>
          bytes -> utf32LEDecode
        case bytes =>
          bytes -> utf32BEDecode
      }
    )

  def utf32BEDecode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeFixedLength(CharsetUtf32BE, fixedLength = 4)

  def utf32LEDecode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    utfDecodeFixedLength(CharsetUtf32LE, fixedLength = 4)

  def usASCIIEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.US_ASCII)

  /**
   * `utf*Encode` pipelines adhere to the same behavior of Java's
   * String#getBytes(charset)`, that is:
   *   - utf8: No BOM
   *   - utf16: Has BOM (the outlier)
   *   - utf16BE & utf16LE: No BOM
   *   - All utf32 variants: No BOM
   *
   * If BOM is required, users can use the `*WithBomEncode` variants. (As
   * alluded above, `utf16Encode` always prepends BOM, just like
   * `getBytes("UTF-16")` in Java. In fact, it is an alias to both
   * `utf16BEWithBomEncode` and `utf16WithBomEncode`.
   */
  def utf8Encode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.UTF_8)

  def utf8WithBomEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.UTF_8, bom = BOM.Utf8)

  def utf16BEEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.UTF_16BE)

  def utf16BEWithBomEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.UTF_16BE, bom = BOM.Utf16BE)

  def utf16LEEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.UTF_16LE)

  def utf16LEWithBomEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(StandardCharsets.UTF_16LE, bom = BOM.Utf16LE)

  def utf16Encode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utf16BEWithBomEncode

  def utf16WithBomEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utf16BEWithBomEncode

  def utf32BEEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(CharsetUtf32BE)

  def utf32BEWithBomEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(CharsetUtf32BE, bom = BOM.Utf32BE)

  def utf32LEEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(CharsetUtf32LE)

  def utf32LEWithBomEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utfEncodeFor(CharsetUtf32LE, bom = BOM.Utf32LE)

  def utf32Encode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utf32BEEncode

  def utf32WithBomEncode: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    utf32BEWithBomEncode

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

  private def textDecodeUsing(charset: Charset): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, Byte] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = String

      override def apply[Env, Err, Elem <: Byte](sourceStream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, String] = {

        def stringChunkFrom(bytes: Chunk[Byte]) =
          Chunk.single(
            new String(bytes.toArray, charset)
          )

        def transform: ZChannel[Env, Err, Chunk[Byte], Any, Err, Chunk[String], Any] =
          ZChannel.readWith(
            received => {
              if (received.isEmpty)
                transform
              else
                ZChannel.write(stringChunkFrom(received))
            },
            error = ZChannel.fail(_),
            done = _ => ZChannel.unit
          )

        new ZStream(
          sourceStream.channel >>> transform
        )
      }
    }

  private def utfDecodeDetectingBom(
    bomSize: Int,
    processBom: Chunk[Byte] => (
      Chunk[Byte],
      ZPipeline.WithOut[
        Nothing,
        Any,
        Nothing,
        Any,
        Nothing,
        Byte,
        ({ type OutEnv[Env] = Env })#OutEnv,
        ({ type OutErr[Err] = Err })#OutErr,
        ({ type OutElem[Elem] = String })#OutElem
      ]
    )
  ): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, Byte] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = String

      override def apply[Env, Err, Elem <: Byte](sourceStream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, String] = {

        type DecodingChannel = ZChannel[Env, Err, Chunk[Byte], Any, Err, Chunk[String], Any]

        def passThrough(
          decodingPipeline: ZPipeline.WithOut[
            Nothing,
            Any,
            Nothing,
            Any,
            Nothing,
            Byte,
            ({ type OutEnv[Env] = Env })#OutEnv,
            ({ type OutErr[Err] = Err })#OutErr,
            ({ type OutElem[Elem] = String })#OutElem
          ]
        ): DecodingChannel =
          ZChannel.readWith(
            received =>
              decodingPipeline(
                ZStream.fromChunk(received)
              ).channel *>
                passThrough(decodingPipeline),
            error = ZChannel.fail(_),
            done = _ => ZChannel.unit
          )

        def lookingForBom(buffer: Chunk[Byte]): DecodingChannel =
          ZChannel.readWith(
            received => {
              val data = buffer ++ received

              if (data.length >= bomSize) {
                val (bom, rest)                        = data.splitAt(bomSize)
                val (dataWithoutBom, decodingPipeline) = processBom(bom)

                decodingPipeline(
                  ZStream.fromChunk(dataWithoutBom ++ rest)
                ).channel *>
                  passThrough(decodingPipeline)
              } else {
                lookingForBom(data)
              }
            },
            error = ZChannel.fail(_),
            done = _ =>
              if (buffer.isEmpty) ZChannel.unit
              else {
                val (dataWithoutBom, decodingPipeline) = processBom(buffer)
                decodingPipeline(
                  ZStream.fromChunk(dataWithoutBom)
                ).channel *>
                  passThrough(decodingPipeline)
              }
          )

        new ZStream(
          sourceStream.channel >>> lookingForBom(Chunk.empty)
        )
      }
    }

  private def utf8DecodeNoBom: ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, Byte] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = String

      override def apply[Env, Err, Elem <: Byte](sourceStream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, String] = {

        val emptyByteChunk: Chunk[Byte] =
          Chunk.empty
        val emptyStringChunk =
          Chunk.single("")

        val is2ByteStart =
          (b: Byte) => (b & 0xe0) == 0xc0
        val is3ByteStart =
          (b: Byte) => (b & 0xf0) == 0xe0
        val is4ByteStart =
          (b: Byte) => (b & 0xf8) == 0xf0

        def computeSplitIndex(chunk: Chunk[Byte]) = {
          // There are 3 bad patterns we need to check to detect an incomplete chunk:
          // - 2/3/4 byte sequences that start on the last byte
          // - 3/4 byte sequences that start on the second-to-last byte
          // - 4 byte sequences that start on the third-to-last byte
          //
          // Otherwise, we can convert the entire concatenated chunk to a string.
          val size = chunk.length

          if (
            size >= 1 &&
            List(is2ByteStart, is3ByteStart, is4ByteStart).exists(_(chunk(size - 1)))
          ) {
            size - 1
          } else if (
            size >= 2 &&
            List(is3ByteStart, is4ByteStart).exists(_(chunk(size - 2)))
          ) {
            size - 2
          } else if (size >= 3 && is4ByteStart(chunk(size - 3))) {
            size - 3
          } else {
            size
          }
        }

        def stringChunkFrom(bytes: Chunk[Byte]) =
          Chunk.single(
            new String(bytes.toArray, StandardCharsets.UTF_8)
          )

        def process(buffered: Chunk[Byte], received: Chunk[Byte]): (Chunk[String], Chunk[Byte]) = {
          val bytes         = buffered ++ received
          val (chunk, rest) = bytes.splitAt(computeSplitIndex(bytes))

          if (chunk.isEmpty) {
            emptyStringChunk -> rest.materialize
          } else if (rest.isEmpty) {
            stringChunkFrom(chunk) -> emptyByteChunk
          } else {
            stringChunkFrom(chunk) -> rest
          }
        }

        def readThenTransduce(buffer: Chunk[Byte]): ZChannel[Env, Err, Chunk[Byte], Any, Err, Chunk[String], Any] =
          ZChannel.readWith(
            received => {
              val (string, buffered) = process(buffer, received)

              ZChannel.write(string) *> readThenTransduce(buffered)
            },
            error = ZChannel.fail(_),
            done = _ =>
              if (buffer.isEmpty)
                ZChannel.unit
              else
                ZChannel.write(stringChunkFrom(buffer))
          )

        new ZStream(
          sourceStream.channel >>> readThenTransduce(emptyByteChunk)
        )
      }
    }

  private def utfDecodeFixedLength(charset: Charset, fixedLength: Int): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    Byte,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = String })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, Byte] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = String

      override def apply[Env, Err, Elem <: Byte](sourceStream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, String] = {

        val emptyByteChunk: Chunk[Byte] =
          Chunk.empty
        val emptyStringChunk =
          Chunk.single("")

        def stringChunkFrom(bytes: Chunk[Byte]) =
          Chunk.single(
            new String(bytes.toArray, charset)
          )

        def process(buffered: Chunk[Byte], received: Chunk[Byte]): (Chunk[String], Chunk[Byte]) = {
          val bytes     = buffered ++ received
          val remainder = bytes.length % fixedLength

          if (remainder == 0) {
            stringChunkFrom(bytes) -> emptyByteChunk
          } else if (bytes.length > fixedLength) {
            val (fullChunk, rest) = bytes.splitAt(bytes.length - remainder)

            stringChunkFrom(fullChunk) -> rest
          } else {
            emptyStringChunk -> bytes.materialize
          }
        }

        def readThenTransduce(buffer: Chunk[Byte]): ZChannel[Env, Err, Chunk[Byte], Any, Err, Chunk[String], Any] =
          ZChannel.readWith(
            received => {
              val (string, buffered) = process(buffer, received)

              ZChannel.write(string) *> readThenTransduce(buffered)
            },
            error = ZChannel.fail(_),
            done = _ =>
              if (buffer.isEmpty)
                ZChannel.unit
              else
                ZChannel.write(stringChunkFrom(buffer))
          )

        new ZStream(
          sourceStream.channel >>> readThenTransduce(emptyByteChunk)
        )
      }
    }

  private def utfEncodeFor(charset: Charset, bom: Chunk[Byte] = Chunk.empty): ZPipeline.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    Nothing,
    String,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
    ({ type OutElem[Elem] = Byte })#OutElem
  ] =
    new ZPipeline[Nothing, Any, Nothing, Any, Nothing, String] {
      override type OutEnv[Env]   = Env
      override type OutErr[Err]   = Err
      override type OutElem[Elem] = Byte

      override def apply[Env, Err, Elem <: String](sourceStream: ZStream[Env, Err, Elem])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, Byte] = {
        def transform: ZChannel[Env, Err, Chunk[String], Any, Err, Chunk[Byte], Any] =
          ZChannel.readWith(
            received =>
              if (received.isEmpty)
                transform
              else {
                val bytes = received.foldLeft[Chunk[Byte]](
                  Chunk.empty
                ) { (acc, string) =>
                  val bytes = string.getBytes(charset)
                  acc ++ Chunk.fromArray(bytes)
                }

                ZChannel.write(bytes)
              },
            error = ZChannel.fail(_),
            done = _ => ZChannel.unit
          )

        ZStream.fromChunk(bom) ++
          new ZStream(
            sourceStream.channel >>> transform
          )
      }
    }
}
