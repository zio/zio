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
 * A `ZPipeline[Env, Err, In, Out]` is a polymorphic stream transformer.
 * Pipelines accept a stream as input, and return the transformed stream as
 * output.
 *
 * Pipelines can be thought of as a recipe for calling a bunch of methods on a
 * source stream, to yield a new (transformed) stream. A nice mental model is
 * the following type alias:
 *
 * {{{
 * type ZPipeline[Env, Err, In, Out] = ZStream[Env, Err, In] => ZStream[Env, Err, Out]
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
trait ZPipeline[-Env, +Err, -In, +Out] { self =>

  def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In])(implicit
    trace: ZTraceElement
  ): ZStream[Env1, Err1, Out]

  /**
   * Composes two pipelines into one pipeline, by first applying the
   * transformation of this pipeline, and then applying the transformation of
   * the specified pipeline.
   */
  final def >>>[Env1 <: Env, Err1 >: Err, Out2](
    that: ZPipeline[Env1, Err1, Out, Out2]
  ): ZPipeline[Env1, Err1, In, Out2] =
    new ZPipeline[Env1, Err1, In, Out2] {
      def apply[Env0 <: Env1, Err0 >: Err1](stream: ZStream[Env0, Err0, In])(implicit
        trace: ZTraceElement
      ): ZStream[Env0, Err0, Out2] =
        that(self(stream))
    }

  /**
   * Composes two pipelines into one pipeline, by first applying the
   * transformation of the specified pipeline, and then applying the
   * transformation of this pipeline.
   */
  final def <<<[Env1 <: Env, Err1 >: Err, In2](
    that: ZPipeline[Env1, Err1, In2, In]
  ): ZPipeline[Env1, Err1, In2, Out] =
    new ZPipeline[Env1, Err1, In2, Out] {
      def apply[Env0 <: Env1, Err0 >: Err1](stream: ZStream[Env0, Err0, In2])(implicit
        trace: ZTraceElement
      ): ZStream[Env0, Err0, Out] =
        self(that(stream))
    }

  /**
   * A named version of the `>>>` operator.
   */
  final def andThen[Env1 <: Env, Err1 >: Err, Out2](
    that: ZPipeline[Env1, Err1, Out, Out2]
  ): ZPipeline[Env1, Err1, In, Out2] =
    self >>> that

  /**
   * A named version of the `<<<` operator.
   */
  final def compose[Env1 <: Env, Err1 >: Err, In2](
    that: ZPipeline[Env1, Err1, In2, In]
  ): ZPipeline[Env1, Err1, In2, Out] =
    self <<< that
}

object ZPipeline extends ZPipelinePlatformSpecificConstructors {

  /**
   * A shorter version of [[ZPipeline.identity]], which can facilitate more
   * compact definition of pipelines.
   *
   * {{{
   * ZPipeline[Int] >>> ZPipeline.filter(_ % 2 != 0)
   * }}}
   */
  def apply[In]: ZPipeline[Any, Nothing, In, In] =
    identity[In]

  def branchAfter[Env, Err, In](n: Int)(f: Chunk[In] => ZPipeline[Env, Err, In, In]): ZPipeline[Env, Err, In, In] =
    new ZPipeline[Env, Err, In, In] {
      def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In])(implicit
        trace: ZTraceElement
      ): ZStream[Env1, Err1, In] =
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
  def collect[In, Out](f: PartialFunction[In, Out]): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline[Any, Nothing, In, Out] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, Out] =
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
  def dropUntil[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
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
  def dropWhile[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream.dropWhile(f)
    }

  /**
   * Creates a pipeline that filters elements according to the specified
   * predicate.
   */
  def filter[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream.filter(f)
    }

  /**
   * Creates a pipeline that groups on adjacent keys, calculated by function f.
   */
  def groupAdjacentBy[In, Key](f: In => Key): ZPipeline[Any, Nothing, In, (Key, NonEmptyChunk[In])] =
    new ZPipeline[Any, Nothing, In, (Key, NonEmptyChunk[In])] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit
        trace: ZTraceElement
      ): ZStream[Env, Err, (Key, NonEmptyChunk[In])] =
        stream.groupAdjacentBy(f)
    }

  /**
   * Creates a pipeline that sends all the elements through the given channel.
   */
  def fromChannel[Env, Err, In, Out](
    channel: ZChannel[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any]
  ): ZPipeline[Env, Err, In, Out] =
    new ZPipeline[Env, Err, In, Out] {
      def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In])(implicit
        trace: ZTraceElement
      ): ZStream[Env1, Err1, Out] =
        stream.pipeThroughChannelOrFail(channel)
    }

  /**
   * The identity pipeline, which does not modify streams in any way.
   */
  def identity[In]: ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream
    }

  def iso_8859_1Decode: ZPipeline[Any, Nothing, Byte, String] =
    textDecodeUsing(StandardCharsets.ISO_8859_1)

  def iso_8859_1Encode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.ISO_8859_1)

  /**
   * Creates a pipeline that maps elements with the specified function.
   */
  def map[In, Out](f: In => Out): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline[Any, Nothing, In, Out] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, Out] =
        stream.map(f)
    }

  /**
   * Creates a pipeline that maps chunks of elements with the specified
   * function.
   */
  def mapChunks[In, Out](
    f: Chunk[In] => Chunk[Out]
  ): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline[Any, Nothing, In, Out] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, Out] =
        stream.mapChunks(f)
    }

  /**
   * Creates a pipeline that maps chunks of elements with the specified effect.
   */
  def mapChunksZIO[Env, Err, In, Out](
    f: Chunk[In] => ZIO[Env, Err, Chunk[Out]]
  ): ZPipeline[Env, Err, In, Out] =
    new ZPipeline[Env, Err, In, Out] {
      def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In])(implicit
        trace: ZTraceElement
      ): ZStream[Env1, Err1, Out] =
        stream.mapChunksZIO(f)
    }

  /**
   * Creates a pipeline that maps elements with the specified effectful
   * function.
   */
  def mapZIO[Env, Err, In, Out](f: In => ZIO[Env, Err, Out]): ZPipeline[Env, Err, In, Out] =
    new ZPipeline[Env, Err, In, Out] {
      def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In])(implicit
        trace: ZTraceElement
      ): ZStream[Env1, Err1, Out] =
        stream.mapZIO(f)
    }

  /**
   * Emits the provided chunk before emitting any other value.
   */
  def prepend[In](values: Chunk[In]): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        ZStream.fromChunk(values) ++ stream
    }

  /**
   * A pipeline that rechunks the stream into chunks of the specified size.
   */
  def rechunk[In](n: Int): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream.rechunk(n)
    }

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scan[In, Out](s: Out)(f: (Out, In) => Out): ZPipeline[Any, Nothing, In, Out] =
    scanZIO(s)((out, in) => ZIO.succeedNow(f(out, in)))

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scanZIO[Env, Err, In, Out](s: Out)(f: (Out, In) => ZIO[Env, Err, Out]): ZPipeline[Env, Err, In, Out] =
    new ZPipeline[Env, Err, In, Out] {
      def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In])(implicit
        trace: ZTraceElement
      ): ZStream[Env1, Err1, Out] =
        stream.scanZIO(s)(f)
    }

  /**
   * Splits strings on a delimiter.
   */
  def splitOn(delimiter: String): ZPipeline[Any, Nothing, String, String] =
    new ZPipeline[Any, Nothing, String, String] {
      def apply[Env, Err](stream: ZStream[Env, Err, String])(implicit trace: ZTraceElement): ZStream[Env, Err, String] =
        stream
          .map(str => Chunk.fromArray(str.toArray))
          .mapChunks(_.flatten)
          .splitOnChunk(Chunk.fromArray(delimiter.toArray))
          .map(_.mkString(""))
    }

  /**
   * Splits strings on a delimiter.
   */
  def splitOnChunk[In](delimiter: Chunk[In]): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream
          .splitOnChunk(delimiter)
          .flattenChunks
    }

  /**
   * Splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX
   * newlines (`\n`).
   */
  def splitLines: ZPipeline[Any, Nothing, String, String] =
    new ZPipeline[Any, Nothing, String, String] {
      def apply[Env, Err](
        stream: ZStream[Env, Err, String]
      )(implicit trace: ZTraceElement): ZStream[Env, Err, String] = {
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
   * Creates a pipeline that takes n elements.
   */
  def take[In](n: Long): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream.take(n)
    }

  /**
   * Creates a pipeline that takes elements until the specified predicate
   * evaluates to true.
   */
  def takeUntil[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream.takeUntil(f)
    }

  /**
   * Creates a pipeline that takes elements while the specified predicate
   * evaluates to true.
   */
  def takeWhile[In](f: In => Boolean): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline[Any, Nothing, In, In] {
      def apply[Env, Err](stream: ZStream[Env, Err, In])(implicit trace: ZTraceElement): ZStream[Env, Err, In] =
        stream.takeWhile(f)
    }

  def usASCIIDecode: ZPipeline[Any, Nothing, Byte, String] =
    textDecodeUsing(StandardCharsets.US_ASCII)

  /**
   * utfDecode determines the right encoder to use based on the Byte Order Mark
   * (BOM). If it doesn't detect one, it defaults to utf8Decode. In the case of
   * utf16 and utf32 without BOM, `utf16Decode` and `utf32Decode` should be used
   * instead as both default to their own default decoder respectively.
   */
  def utfDecode: ZPipeline[Any, Nothing, Byte, String] =
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

  def utf8Decode: ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeDetectingBom(
      bomSize = 3,
      {
        case BOM.Utf8 =>
          Chunk.empty -> utf8DecodeNoBom
        case bytes =>
          bytes -> utf8DecodeNoBom
      }
    )

  def utf16Decode: ZPipeline[Any, Nothing, Byte, String] =
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

  def utf16BEDecode: ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(StandardCharsets.UTF_16BE, fixedLength = 2)

  def utf16LEDecode: ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(StandardCharsets.UTF_16LE, fixedLength = 2)

  def utf32Decode: ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeDetectingBom(
      bomSize = 4,
      {
        case bytes @ BOM.Utf32LE =>
          bytes -> utf32LEDecode
        case bytes =>
          bytes -> utf32BEDecode
      }
    )

  def utf32BEDecode: ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(CharsetUtf32BE, fixedLength = 4)

  def utf32LEDecode: ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(CharsetUtf32LE, fixedLength = 4)

  def usASCIIEncode: ZPipeline[Any, Nothing, String, Byte] =
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
  def utf8Encode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_8)

  def utf8WithBomEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_8, bom = BOM.Utf8)

  def utf16BEEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16BE)

  def utf16BEWithBomEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16BE, bom = BOM.Utf16BE)

  def utf16LEEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16LE)

  def utf16LEWithBomEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16LE, bom = BOM.Utf16LE)

  def utf16Encode: ZPipeline[Any, Nothing, String, Byte] =
    utf16BEWithBomEncode

  def utf16WithBomEncode: ZPipeline[Any, Nothing, String, Byte] =
    utf16BEWithBomEncode

  def utf32BEEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32BE)

  def utf32BEWithBomEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32BE, bom = BOM.Utf32BE)

  def utf32LEEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32LE)

  def utf32LEWithBomEncode: ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32LE, bom = BOM.Utf32LE)

  def utf32Encode: ZPipeline[Any, Nothing, String, Byte] =
    utf32BEEncode

  def utf32WithBomEncode: ZPipeline[Any, Nothing, String, Byte] =
    utf32BEWithBomEncode

  private def textDecodeUsing(charset: Charset): ZPipeline[Any, Nothing, Byte, String] =
    new ZPipeline[Any, Nothing, Byte, String] {
      def apply[Env, Err](stream: ZStream[Env, Err, Byte])(implicit trace: ZTraceElement): ZStream[Env, Err, String] = {

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
          stream.channel >>> transform
        )
      }
    }

  private def utfDecodeDetectingBom(
    bomSize: Int,
    processBom: Chunk[Byte] => (
      Chunk[Byte],
      ZPipeline[Any, Nothing, Byte, String]
    )
  ): ZPipeline[Any, Nothing, Byte, String] =
    new ZPipeline[Any, Nothing, Byte, String] {
      def apply[Env, Err](stream: ZStream[Env, Err, Byte])(implicit trace: ZTraceElement): ZStream[Env, Err, String] = {

        type DecodingChannel = ZChannel[Env, Err, Chunk[Byte], Any, Err, Chunk[String], Any]

        def passThrough(
          decodingPipeline: ZPipeline[Any, Nothing, Byte, String]
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
          stream.channel >>> lookingForBom(Chunk.empty)
        )
      }
    }

  private def utf8DecodeNoBom: ZPipeline[Any, Nothing, Byte, String] =
    new ZPipeline[Any, Nothing, Byte, String] {
      def apply[Env, Err](stream: ZStream[Env, Err, Byte])(implicit trace: ZTraceElement): ZStream[Env, Err, String] = {

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
          stream.channel >>> readThenTransduce(emptyByteChunk)
        )
      }
    }

  private def utfDecodeFixedLength(charset: Charset, fixedLength: Int): ZPipeline[Any, Nothing, Byte, String] =
    new ZPipeline[Any, Nothing, Byte, String] {
      def apply[Env, Err](stream: ZStream[Env, Err, Byte])(implicit trace: ZTraceElement): ZStream[Env, Err, String] = {

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
          stream.channel >>> readThenTransduce(emptyByteChunk)
        )
      }
    }

  private def utfEncodeFor(charset: Charset, bom: Chunk[Byte] = Chunk.empty): ZPipeline[Any, Nothing, String, Byte] =
    new ZPipeline[Any, Nothing, String, Byte] {
      def apply[Env, Err](stream: ZStream[Env, Err, String])(implicit trace: ZTraceElement): ZStream[Env, Err, Byte] = {
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
            stream.channel >>> transform
          )
      }
    }
}
