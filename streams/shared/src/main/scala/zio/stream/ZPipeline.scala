/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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

import java.nio.{Buffer, ByteBuffer, CharBuffer}
import java.nio.charset.{
  CharacterCodingException,
  Charset,
  CoderResult,
  MalformedInputException,
  StandardCharsets,
  UnmappableCharacterException
}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

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
final class ZPipeline[-Env, +Err, -In, +Out] private (
  val channel: ZChannel[Env, ZNothing, Chunk[In], Any, Err, Chunk[Out], Any]
) {
  self =>

  /** Attach this pipeline to the given stream */
  def apply[Env1 <: Env, Err1 >: Err](stream: => ZStream[Env1, Err1, In])(implicit
    trace: Trace
  ): ZStream[Env1, Err1, Out] =
    ZStream.suspend(stream).pipeThroughChannelOrFail(channel)

  /**
   * Composes two pipelines into one pipeline, by first applying the
   * transformation of this pipeline, and then applying the transformation of
   * the specified pipeline.
   */
  def >>>[Env1 <: Env, Err1 >: Err, Out2](
    that: => ZPipeline[Env1, Err1, Out, Out2]
  )(implicit trace: Trace): ZPipeline[Env1, Err1, In, Out2] =
    new ZPipeline(self.channel.pipeToOrFail(that.channel))

  /**
   * Compose this transducer with a sink, resulting in a sink that processes
   * elements by piping them through this transducer and piping the results into
   * the sink.
   */
  def >>>[Env1 <: Env, Err1 >: Err, Leftover, Out2](that: => ZSink[Env1, Err1, Out, Leftover, Out2])(implicit
    trace: Trace
  ): ZSink[Env1, Err1, In, Leftover, Out2] =
    ZSink.fromChannel(self.channel.pipeToOrFail(that.channel))

  /**
   * Composes two pipelines into one pipeline, by first applying the
   * transformation of the specified pipeline, and then applying the
   * transformation of this pipeline.
   */
  def <<<[Env1 <: Env, Err1 >: Err, In2](
    that: => ZPipeline[Env1, Err1, In2, In]
  )(implicit trace: Trace): ZPipeline[Env1, Err1, In2, Out] =
    ZPipeline.suspend(new ZPipeline(that.channel.pipeToOrFail(self.channel)))

  /**
   * A named version of the `>>>` operator.
   */
  def andThen[Env1 <: Env, Err1 >: Err, Out2](
    that: => ZPipeline[Env1, Err1, Out, Out2]
  )(implicit trace: Trace): ZPipeline[Env1, Err1, In, Out2] =
    self >>> that

  /**
   * A named version of the `<<<` operator.
   */
  def compose[Env1 <: Env, Err1 >: Err, In2](
    that: => ZPipeline[Env1, Err1, In2, In]
  )(implicit trace: Trace): ZPipeline[Env1, Err1, In2, Out] =
    self <<< that

  /**
   * Transforms the errors emitted by this pipeline using `f`.
   */
  def mapError[Err2](
    f: Err => Err2
  )(implicit trace: Trace): ZPipeline[Env, Err2, In, Out] =
    new ZPipeline(self.channel.mapError(f))

  /**
   * A more powerful version of [[mapError]] which also surfaces the [[Cause]]
   * of the channel failure
   */
  def mapErrorCause[Err2](
    f: Cause[Err] => Cause[Err2]
  )(implicit trace: Trace): ZPipeline[Env, Err2, In, Out] =
    new ZPipeline(self.channel.mapErrorCause(f))

  /**
   * Translates pipeline failure into death of the fiber, making all failures
   * unchecked and not a part of the type of the effect.
   */
  def orDie(implicit
    ev: Err <:< Throwable,
    trace: Trace
  ): ZPipeline[Env, Nothing, In, Out] =
    orDieWith(ev)

  /**
   * Keeps none of the errors, and terminates the fiber with them, using the
   * specified function to convert the `E` into a `Throwable`.
   */
  def orDieWith(f: Err => Throwable)(implicit trace: Trace): ZPipeline[Env, Nothing, In, Out] =
    new ZPipeline(self.channel.orDieWith(f))

  /** Converts this pipeline to its underlying channel */
  def toChannel: ZChannel[Env, ZNothing, Chunk[In], Any, Err, Chunk[Out], Any] =
    self.channel
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
  def apply[In](implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    identity[In]

  /**
   * A dynamic pipeline that first collects `n` elements from the stream, then
   * creates another pipeline with the function `f` and sends all the following
   * elements through that.
   */
  def branchAfter[Env, Err, In, Out](
    n: => Int
  )(f: Chunk[In] => ZPipeline[Env, Err, In, Out])(implicit trace: Trace): ZPipeline[Env, Err, In, Out] =
    ZPipeline.suspend {

      def collecting(buf: Chunk[In]): ZChannel[Env, Err, Chunk[In], Any, Err, Chunk[Out], Any] =
        ZChannel.readWithCause(
          (chunk: Chunk[In]) => {
            val newBuf = buf ++ chunk
            if (newBuf.length >= n) {
              val (is, is1) = newBuf.splitAt(n)
              val pipeline  = f(is)
              pipeline(ZStream.fromChunk(is1)).channel *> emitting(pipeline)
            } else
              collecting(newBuf)
          },
          (cause: Cause[Err]) => ZChannel.failCause(cause),
          (_: Any) =>
            if (buf.isEmpty)
              ZChannel.unit
            else {
              val pipeline = f(buf)
              pipeline(ZStream.empty).channel
            }
        )

      def emitting(pipeline: ZPipeline[Env, Err, In, Out]): ZChannel[Env, Err, Chunk[In], Any, Err, Chunk[Out], Any] =
        ZChannel.readWithCause(
          (chunk: Chunk[In]) => pipeline(ZStream.fromChunk(chunk)).channel *> emitting(pipeline),
          (cause: Cause[Err]) => ZChannel.failCause(cause),
          (_: Any) => ZChannel.unit
        )

      new ZPipeline(collecting(Chunk.empty))
    }

  /**
   * Creates a pipeline that exposes the chunk structure of the stream.
   */
  def chunks[In](implicit trace: Trace): ZPipeline[Any, Nothing, In, Chunk[In]] =
    mapChunks(Chunk.single)

  /**
   * Creates a pipeline that collects elements with the specified partial
   * function.
   *
   * {{{
   * ZPipeline.collect[Option[Int], Int] { case Some(v) => v }
   * }}}
   */
  def collect[In, Out](f: PartialFunction[In, Out])(implicit trace: Trace): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOut(_.collect(f)))

  /**
   * Delays the emission of values by holding new values for a set duration. If
   * no new values arrive during that time the value is emitted, however if a
   * new value is received during the holding period the previous value is
   * discarded and the process is repeated with the new value.
   *
   * This operator is useful if you have a stream of "bursty" events which
   * eventually settle down and you only need the final event of the burst.
   *
   * @example
   * A search engine may only want to initiate a search after a user has
   * paused typing so as to not prematurely recommend results.
   */
  def debounce[Env, Err, In](d: => Duration)(implicit trace: Trace): ZPipeline[Env, Err, In, In] = {
    import ZStream.DebounceState
    import ZStream.DebounceState._
    import ZStream.HandoffSignal
    import ZStream.HandoffSignal._

    ZPipeline.unwrap(
      ZIO.transplant { grafter =>
        for {
          d <- ZIO.succeed(d)
          handoff <- ZStream.Handoff.make[HandoffSignal[Err, In]]
        } yield {
          def enqueue(last: Chunk[In]) =
            for {
              f <- grafter(Clock.sleep(d).as(last).fork)
            } yield consumer(Previous(f))

          lazy val producer: ZChannel[Env, Err, Chunk[In], Any, Err, Nothing, Any] =
            ZChannel.readWithCause(
              (in: Chunk[In]) =>
                in.lastOption.fold(producer) { last =>
                  ZChannel.fromZIO(handoff.offer(Emit(Chunk.single(last)))) *> producer
                },
              (cause: Cause[Err]) => ZChannel.fromZIO(handoff.offer(Halt(cause))),
              (_: Any) => ZChannel.fromZIO(handoff.offer(End(ZStream.SinkEndReason.UpstreamEnd)))
            )

          def consumer(state: DebounceState[Err, In]): ZChannel[Env, Any, Any, Any, Err, Chunk[In], Any] =
            ZChannel.unwrap(
              state match {
                case NotStarted =>
                  handoff.take.map {
                    case Emit(last) =>
                      ZChannel.unwrap(enqueue(last))
                    case HandoffSignal.Halt(error) =>
                      ZChannel.failCause(error)
                    case HandoffSignal.End(_) =>
                      ZChannel.unit
                  }
                case Current(fiber) =>
                  fiber.join.map {
                    case HandoffSignal.Emit(last) => ZChannel.unwrap(enqueue(last))
                    case HandoffSignal.Halt(error) => ZChannel.failCause(error)
                    case HandoffSignal.End(_) => ZChannel.unit
                  }
                case Previous(fiber) =>
                  fiber.join
                    .raceWith[Env, Err, Err, HandoffSignal[Err, In], ZChannel[
                      Env,
                      Any,
                      Any,
                      Any,
                      Err,
                      Chunk[In],
                      Any
                    ]](
                      handoff.take
                    )(
                      {
                        case (Exit.Success(a), current) =>
                          ZIO.succeedNow(ZChannel.write(a) *> consumer(Current(current)))
                        case (Exit.Failure(cause), current) =>
                          current.interrupt as ZChannel.failCause(cause)
                      },
                      {
                        case (Exit.Success(Emit(last)), previous) =>
                          previous.interrupt *> enqueue(last)
                        case (Exit.Success(Halt(cause)), previous) =>
                          previous.interrupt as ZChannel.failCause(cause)
                        case (Exit.Success(End(_)), previous) =>
                          previous.join.map(ZChannel.write(_) *> ZChannel.unit)
                        case (Exit.Failure(cause), previous) =>
                          previous.interrupt as ZChannel.failCause(cause)
                      }
                    )
              }
            )

          ZChannel.scoped[Env](producer.runScoped.forkScoped) *>
            new ZPipeline(consumer(NotStarted))
        }
      }
    )
  }

  /**
   * Creates a pipeline that decodes a stream of bytes into a stream of strings
   * using the given charset
   */
  def decodeStringWith(
    charset: => Charset
  )(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeCharsWith(charset) >>> ZPipeline.mapChunks((chars: Chunk[Char]) => Chunk.single(new String(chars.toArray)))

  /**
   * Creates a pipeline that decodes a stream of bytes into a stream of
   * characters using the given charset
   */
  def decodeCharsWith(
    charset: => Charset,
    bufSize: => Int = 4096
  )(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, Char] =
    ZPipeline.suspend {
      val decoder    = charset.newDecoder()
      val byteBuffer = ByteBuffer.allocate(bufSize)
      val charBuffer = CharBuffer.allocate((bufSize.toFloat * decoder.averageCharsPerByte).round)

      def handleCoderResult(coderResult: CoderResult) =
        if (coderResult.isUnderflow || coderResult.isOverflow) {
          ZIO.succeed {
            byteBuffer.compact()
            charBuffer.flip()
            val array = new Array[Char](charBuffer.remaining)
            charBuffer.get(array)
            charBuffer.clear()
            Chunk.fromArray(array)
          }
        } else if (coderResult.isMalformed) {
          ZIO.fail(new MalformedInputException(coderResult.length()))
        } else if (coderResult.isUnmappable) {
          ZIO.fail(new UnmappableCharacterException(coderResult.length()))
        } else {
          ZIO.dieMessage(s"Unexpected coder result: $coderResult")
        }

      def decodeChunk(inBytes: Chunk[Byte]): IO[CharacterCodingException, Chunk[Char]] =
        for {
          remainingBytes <- ZIO.succeed {
                              val bufRemaining = byteBuffer.remaining
                              val (decodeBytes, remainingBytes) =
                                if (inBytes.length > bufRemaining)
                                  inBytes.splitAt(bufRemaining)
                                else
                                  (inBytes, Chunk.empty)
                              byteBuffer.put(decodeBytes.toArray)
                              byteBuffer.flip()
                              remainingBytes
                            }
          result         <- ZIO.succeed(decoder.decode(byteBuffer, charBuffer, false))
          decodedChars   <- handleCoderResult(result)
          remainderChars <- if (remainingBytes.isEmpty) ZIO.succeed(Chunk.empty) else decodeChunk(remainingBytes)
        } yield decodedChars ++ remainderChars

      def endOfInput: IO[CharacterCodingException, Chunk[Char]] =
        for {
          result         <- ZIO.succeed(decoder.decode(byteBuffer, charBuffer, true))
          decodedChars   <- handleCoderResult(result)
          remainderChars <- if (result.isOverflow) endOfInput else ZIO.succeed(Chunk.empty)
        } yield decodedChars ++ remainderChars

      def flushRemaining: IO[CharacterCodingException, Chunk[Char]] =
        for {
          result         <- ZIO.succeed(decoder.flush(charBuffer))
          decodedChars   <- handleCoderResult(result)
          remainderChars <- if (result.isOverflow) flushRemaining else ZIO.succeed(Chunk.empty)
        } yield decodedChars ++ remainderChars

      val push: Option[Chunk[Byte]] => IO[CharacterCodingException, Chunk[Char]] = {
        case Some(inChunk) => decodeChunk(inChunk)
        case None =>
          for {
            _              <- ZIO.succeed(byteBuffer.flip())
            decodedChars   <- endOfInput
            remainingBytes <- flushRemaining
            result          = decodedChars ++ remainingBytes
            _ <- ZIO.succeed {
                   byteBuffer.clear()
                   charBuffer.clear()
                 }
          } yield result
      }

      val createPush: ZIO[Any, Nothing, Option[Chunk[Byte]] => IO[CharacterCodingException, Chunk[Char]]] =
        for {
          _ <- ZIO.succeed(decoder.reset)
        } yield push

      ZPipeline.fromPush(createPush)
    }

  /**
   * Creates a pipeline that drops n elements.
   */
  def drop[In](n: => Int)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    ZPipeline.suspend {
      def loop(r: Int): ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[In], Any] =
        ZChannel
          .readWith(
            (in: Chunk[In]) => {
              val dropped  = in.drop(r)
              val leftover = (r - in.length).max(0)
              val more     = in.isEmpty || leftover > 0

              if (more) loop(leftover)
              else ZChannel.write(dropped) *> ZChannel.identity
            },
            (e: ZNothing) => ZChannel.fail(e),
            (_: Any) => ZChannel.unit
          )

      new ZPipeline(loop(n))
    }

  /**
   * Creates a pipeline that drops elements until the specified predicate
   * evaluates to true.
   *
   * {{{
   * ZPipeline.dropUntil[Int](_ > 100)
   * }}}
   */
  def dropUntil[In](f: In => Boolean)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    ZPipeline.dropWhile[In](!f(_)) >>> ZPipeline.drop(1)

  /**
   * Creates a pipeline that drops elements while the specified predicate
   * evaluates to true.
   *
   * {{{
   * ZPipeline.dropWhile[Int](_ <= 100)
   * }}}
   */
  def dropWhile[In](f: In => Boolean)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] = {

    def dropWhile(f: In => Boolean): ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[In], Any] =
      ZChannel.readWith(
        in => {
          val out = in.dropWhile(f)
          if (out.isEmpty) dropWhile(f)
          else ZChannel.write(out) *> ZChannel.identity
        },
        err => ZChannel.fail(err),
        out => ZChannel.succeedNow(out)
      )

    new ZPipeline(dropWhile(f))
  }

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the given charset
   */
  def encodeStringWith(
    charset: => Charset,
    bom: => Chunk[Byte] = Chunk.empty
  )(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] = {
    val withoutBOM =
      ZPipeline.mapChunks((s: Chunk[String]) =>
        s.foldLeft[Chunk[Char]](Chunk.empty)((acc, str) => acc ++ Chunk.fromArray(str.toCharArray))
      ) >>> encodeCharsWith(charset)

    if (bom.isEmpty) withoutBOM
    else {
      ZPipeline.fromChannel(ZChannel.write(bom) *> withoutBOM.channel)
    }
  }

  /**
   * Creates a pipeline that converts a stream of characters into a stream of
   * bytes using the given charset
   */
  def encodeCharsWith(
    charset: => Charset,
    bufferSize: => Int = 4096
  )(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Char, Byte] =
    ZPipeline.suspend {
      val encoder    = charset.newEncoder()
      val charBuffer = CharBuffer.allocate((bufferSize.toFloat / encoder.averageBytesPerChar).round)
      val byteBuffer = ByteBuffer.allocate(bufferSize)

      def handleCoderResult(coderResult: CoderResult): ZIO[Any, CharacterCodingException, Chunk[Byte]] =
        if (coderResult.isUnderflow || coderResult.isOverflow) {
          ZIO.succeed {
            charBuffer.compact()
            byteBuffer.flip()
            val array = new Array[Byte](byteBuffer.remaining())
            byteBuffer.get(array)
            byteBuffer.clear()
            Chunk.fromArray(array)
          }
        } else if (coderResult.isMalformed) {
          ZIO.fail(new MalformedInputException(coderResult.length()))
        } else if (coderResult.isUnmappable) {
          ZIO.fail(new UnmappableCharacterException(coderResult.length()))
        } else {
          ZIO.dieMessage(s"Invalid CoderResult state")
        }

      def encodeChunk(inChars: Chunk[Char]): IO[CharacterCodingException, Chunk[Byte]] =
        for {
          remainingChars <- ZIO.succeed {
                              val bufRemaining = charBuffer.remaining()
                              val (decodeChars, remainingChars) = {
                                if (inChars.length > bufRemaining) {
                                  inChars.splitAt(bufRemaining)
                                } else
                                  (inChars, Chunk.empty)
                              }
                              charBuffer.put(decodeChars.toArray)
                              charBuffer.flip()
                              remainingChars
                            }
          result         <- ZIO.succeed(encoder.encode(charBuffer, byteBuffer, false))
          encodedBytes   <- handleCoderResult(result)
          remainderBytes <- if (remainingChars.isEmpty) ZIO.succeed(Chunk.empty) else encodeChunk(remainingChars)

        } yield encodedBytes ++ remainderBytes

      def endOfInput: IO[CharacterCodingException, Chunk[Byte]] =
        for {
          result         <- ZIO.succeed(encoder.encode(charBuffer, byteBuffer, true))
          encodedBytes   <- handleCoderResult(result)
          remainderBytes <- if (result.isOverflow) endOfInput else ZIO.succeed(Chunk.empty)
        } yield encodedBytes ++ remainderBytes

      def flushRemaining: IO[CharacterCodingException, Chunk[Byte]] =
        for {
          result         <- ZIO.succeed(encoder.flush(byteBuffer))
          encodedBytes   <- handleCoderResult(result)
          remainderBytes <- if (result.isOverflow) flushRemaining else ZIO.succeed(Chunk.empty)
        } yield encodedBytes ++ remainderBytes

      val push: Option[Chunk[Char]] => IO[CharacterCodingException, Chunk[Byte]] = {
        case Some(inChunk: Chunk[Char]) =>
          encodeChunk(inChunk)
        case None =>
          for {
            _              <- ZIO.succeed(charBuffer.flip())
            encodedBytes   <- endOfInput
            remainingBytes <- flushRemaining
            result          = encodedBytes ++ remainingBytes
            _ <- ZIO.succeed {
                   charBuffer.clear()
                   byteBuffer.clear()
                 }
          } yield result
      }

      val createPush: ZIO[Any, Nothing, Option[Chunk[Char]] => IO[CharacterCodingException, Chunk[Byte]]] = {
        for {
          _ <- ZIO.succeed(encoder.reset)
        } yield push
      }

      ZPipeline.fromPush(createPush)
    }

  /**
   * Accesses the environment of the pipeline in the context of a pipeline.
   */
  def environmentWithPipeline[Env]: EnvironmentWithPipelinePartiallyApplied[Env] =
    new EnvironmentWithPipelinePartiallyApplied[Env]

  /**
   * Creates a pipeline that filters elements according to the specified
   * predicate.
   */
  def filter[In](f: In => Boolean)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOut(_.filter(f)))

  /**
   * Creates a pipeline that submerges chunks into the structure of the stream.
   */
  def flattenChunks[In](implicit trace: Trace): ZPipeline[Any, Nothing, Chunk[In], In] =
    ZPipeline.mapChunks(_.flatten)

  /**
   * Creates a pipeline that groups on adjacent keys, calculated by function f.
   */
  def groupAdjacentBy[In, Key](
    f: In => Key
  )(implicit trace: Trace): ZPipeline[Any, Nothing, In, (Key, NonEmptyChunk[In])] = {
    type Out = (Key, NonEmptyChunk[In])
    def go(in: Chunk[In], state: Option[Out]): (Chunk[Out], Option[Out]) =
      in.foldLeft[(Chunk[Out], Option[Out])]((Chunk.empty, state)) {
        case ((os, None), a) =>
          (os, Some((f(a), NonEmptyChunk(a))))
        case ((os, Some(agg @ (k, aggregated))), a) =>
          val k2 = f(a)
          if (k == k2)
            (os, Some((k, aggregated :+ a)))
          else
            (os :+ agg, Some((k2, NonEmptyChunk(a))))
      }

    def chunkAdjacent(buffer: Option[Out]): ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[Out], Any] =
      ZChannel.readWithCause(
        in = chunk => {
          val (outputs, newBuffer) = go(chunk, buffer)
          ZChannel.write(outputs) *> chunkAdjacent(newBuffer)
        },
        halt = ZChannel.failCause(_),
        done = _ =>
          buffer match {
            case Some(o) => ZChannel.write(Chunk.single(o))
            case None    => ZChannel.unit
          }
      )

    new ZPipeline(chunkAdjacent(None))
  }

  /**
   * Creates a pipeline that sends all the elements through the given channel.
   */
  def fromChannel[Env, Err, In, Out](
    channel: => ZChannel[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any]
  ): ZPipeline[Env, Err, In, Out] =
    new ZPipeline(channel)

  /**
   * Creates a pipeline from a chunk processing function.
   */
  def fromPush[Env, Err, In, Out](
    push: => ZIO[Scope with Env, Nothing, Option[Chunk[In]] => ZIO[Env, Err, Chunk[Out]]]
  )(implicit trace: Trace): ZPipeline[Env, Err, In, Out] = {

    def pull(
      push: Option[Chunk[In]] => ZIO[Env, Err, Chunk[Out]]
    ): ZChannel[Env, ZNothing, Chunk[In], Any, Err, Chunk[Out], Any] =
      ZChannel.readWith(
        in =>
          ZChannel
            .fromZIO(push(Some(in)))
            .flatMap(out => ZChannel.write(out)) *> pull(push),
        err => ZChannel.fail(err),
        _ => ZChannel.fromZIO(push(None)).flatMap(out => ZChannel.write(out))
      )

    val channel: ZChannel[Env, ZNothing, Chunk[In], Any, Err, Chunk[Out], Any] =
      ZChannel.unwrapScoped[Env](push.map(pull))

    new ZPipeline(channel)
  }

  /**
   * Creates a pipeline that repeatedly sends all elements through the given
   * sink.
   */
  def fromSink[Env, Err, In, Out](
    sink: => ZSink[Env, Err, In, In, Out]
  )(implicit trace: Trace): ZPipeline[Env, Err, In, Out] =
    new ZPipeline(
      ZChannel.suspend {
        val leftovers: AtomicReference[Chunk[Chunk[In]]] = new AtomicReference(Chunk.empty)
        val upstreamDone: AtomicBoolean                  = new AtomicBoolean(false)

        lazy val buffer: ZChannel[Any, Err, Chunk[In], Any, Err, Chunk[In], Any] =
          ZChannel.suspend {
            val l = leftovers.get

            if (l.isEmpty)
              ZChannel.readWith(
                (c: Chunk[In]) => ZChannel.write(c) *> buffer,
                (e: Err) => ZChannel.fail(e),
                (done: Any) => ZChannel.succeedNow(done)
              )
            else {
              leftovers.set(Chunk.empty)
              ZChannel.writeChunk(l) *> buffer
            }
          }

        def concatAndGet(c: Chunk[Chunk[In]]): Chunk[Chunk[In]] = {
          val ls     = leftovers.get
          val concat = ls ++ c.filter(_.nonEmpty)
          leftovers.set(concat)
          concat
        }

        lazy val upstreamMarker: ZChannel[Any, Err, Chunk[In], Any, Err, Chunk[In], Any] =
          ZChannel.readWith(
            (in: Chunk[In]) => ZChannel.write(in) *> upstreamMarker,
            (err: Err) => ZChannel.fail(err),
            (done: Any) => ZChannel.succeed(upstreamDone.set(true)) *> ZChannel.succeedNow(done)
          )

        lazy val transducer: ZChannel[Env, ZNothing, Chunk[In], Any, Err, Chunk[Out], Unit] =
          sink.channel.collectElements.flatMap { case (leftover, z) =>
            ZChannel
              .succeed((upstreamDone.get, concatAndGet(leftover)))
              .flatMap { case (done, newLeftovers) =>
                val nextChannel =
                  if (done && newLeftovers.isEmpty) ZChannel.unit
                  else transducer

                ZChannel.write(Chunk.single(z)) *> nextChannel
              }
          }

        upstreamMarker >>>
          buffer pipeToOrFail
          transducer
      }
    )

  /**
   * The identity pipeline, which does not modify streams in any way.
   */
  def identity[In](implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline(ZChannel.identity)

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the ISO_8859_1 charset
   */
  def iso_8859_1Decode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeStringWith(StandardCharsets.ISO_8859_1)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the ISO_8859_1 charset
   */
  def iso_8859_1Encode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.ISO_8859_1)

  /**
   * Creates a pipeline that maps elements with the specified function.
   */
  def map[In, Out](f: In => Out)(implicit trace: Trace): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline(ZChannel.identity.mapOut(_.map(f)))

  /**
   * Creates a pipeline that statefully maps elements with the specified
   * function.
   */
  def mapAccum[In, State, Out](
    s: => State
  )(f: (State, In) => (State, Out))(implicit trace: Trace): ZPipeline[Any, Nothing, In, Out] =
    mapAccumZIO(s)((s, in) => ZIO.succeedNow(f(s, in)))

  /**
   * Creates a pipeline that statefully maps elements with the specified effect.
   */
  def mapAccumZIO[Env, Err, In, State, Out](
    s: => State
  )(f: (State, In) => ZIO[Env, Err, (State, Out)])(implicit trace: Trace): ZPipeline[Env, Err, In, Out] =
    ZPipeline.suspend {
      def accumulator(s: State): ZChannel[Env, ZNothing, Chunk[In], Any, Err, Chunk[Out], Any] =
        ZChannel.readWith(
          (in: Chunk[In]) =>
            ZChannel.unwrap(
              ZIO.suspendSucceed {
                val outputChunk            = ChunkBuilder.make[Out](in.size)
                val emit: Out => UIO[Unit] = (o: Out) => ZIO.succeed(outputChunk += o).unit
                ZIO
                  .foldLeft[Env, Err, State, In](in)(s)((s1, a) => f(s1, a).flatMap(sa => emit(sa._2) as sa._1))
                  .fold(
                    failure => {
                      val partialResult = outputChunk.result()
                      if (partialResult.nonEmpty)
                        ZChannel.write(partialResult) *> ZChannel.fail(failure)
                      else
                        ZChannel.fail(failure)
                    },
                    out => ZChannel.write(outputChunk.result()) *> accumulator(out)
                  )
              }
            ),
          ZChannel.fail(_),
          (_: Any) => ZChannel.unit
        )

      new ZPipeline(accumulator(s))
    }

  /**
   * Creates a pipeline that maps chunks of elements with the specified
   * function.
   */
  def mapChunks[In, Out](
    f: Chunk[In] => Chunk[Out]
  )(implicit trace: Trace): ZPipeline[Any, Nothing, In, Out] =
    new ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOut(f))

  /**
   * Creates a pipeline that maps chunks of elements with the specified effect.
   */
  def mapChunksZIO[Env, Err, In, Out](
    f: Chunk[In] => ZIO[Env, Err, Chunk[Out]]
  )(implicit trace: Trace): ZPipeline[Env, Err, In, Out] =
    new ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOutZIO(f))

  /**
   * Creates a pipeline that maps elements with the specified function that
   * returns a stream.
   */
  def mapStream[Env, Err, In, Out](
    f: In => ZStream[Env, Err, Out]
  )(implicit trace: Trace): ZPipeline[Env, Err, In, Out] =
    new ZPipeline(
      ZChannel.identity[Nothing, Chunk[In], Any].concatMap(_.map(f).map(_.channel).fold(ZChannel.unit)(_ *> _))
    )

  /**
   * Creates a pipeline that maps elements with the specified effectful
   * function.
   */
  def mapZIO[Env, Err, In, Out](f: In => ZIO[Env, Err, Out])(implicit
    trace: Trace
  ): ZPipeline[Env, Err, In, Out] =
    new ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOutZIO(_.mapZIO(f)))

  /**
   * Emits the provided chunk before emitting any other value.
   */
  def prepend[In](values: => Chunk[In])(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    new ZPipeline(ZChannel.write(values) *> ZChannel.identity)

  /**
   * A pipeline that rechunks the stream into chunks of the specified size.
   */
  def rechunk[In](n: => Int)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] = {

    def process(
      rechunker: ZStream.Rechunker[In],
      target: Int
    ): ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[In], Any] =
      ZChannel.readWithCause(
        (chunk: Chunk[In]) =>
          if (chunk.size == target && rechunker.isEmpty) {
            ZChannel.write(chunk) *> process(rechunker, target)
          } else if (chunk.size > 0) {
            var chunks: List[Chunk[In]] = Nil
            var result: Chunk[In]       = null
            var i                       = 0

            while (i < chunk.size) {
              while (i < chunk.size && (result eq null)) {
                result = rechunker.write(chunk(i))
                i += 1
              }

              if (result ne null) {
                chunks = result :: chunks
                result = null
              }
            }

            ZChannel.writeAll(chunks.reverse: _*) *> process(rechunker, target)
          } else process(rechunker, target),
        (cause: Cause[ZNothing]) => rechunker.emitIfNotEmpty() *> ZChannel.failCause(cause),
        (_: Any) => rechunker.emitIfNotEmpty()
      )

    val target = scala.math.max(n, 1)
    new ZPipeline(ZChannel.suspend(process(new ZStream.Rechunker(target), target)))
  }

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scan[In, Out](s: => Out)(f: (Out, In) => Out)(implicit trace: Trace): ZPipeline[Any, Nothing, In, Out] =
    scanZIO(s)((out, in) => ZIO.succeedNow(f(out, in)))

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scanZIO[Env, Err, In, Out](
    s: => Out
  )(f: (Out, In) => ZIO[Env, Err, Out])(implicit trace: Trace): ZPipeline[Env, Err, In, Out] =
    ZPipeline.suspend {
      new ZPipeline(
        ZChannel.write(Chunk.single(s)) *>
          mapAccumZIO[Env, Err, In, Out, Out](s)((s, a) => f(s, a).map(s => (s, s))).channel
      )
    }

  /**
   * Accesses the specified service in the environment of the pipeline in the
   * context of a pipeline.
   */
  def serviceWithPipeline[Service]: ServiceWithPipelinePartiallyApplied[Service] =
    new ServiceWithPipelinePartiallyApplied[Service]

  /**
   * Splits strings on a delimiter.
   */
  def splitOn(delimiter: => String)(implicit trace: Trace): ZPipeline[Any, Nothing, String, String] =
    ZPipeline.mapChunks[String, Char](_.flatMap(string => Chunk.fromArray(string.toArray))) >>>
      ZPipeline.splitOnChunk[Char](Chunk.fromArray(delimiter.toArray)) >>>
      ZPipeline.mapChunks[Char, String](chunk => Chunk.single(chunk.mkString("")))

  /**
   * Splits strings on a delimiter.
   */
  def splitOnChunk[In](delimiter: => Chunk[In])(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    ZPipeline.suspend {

      def next(
        leftover: Option[Chunk[In]],
        delimiterIndex: Int
      ): ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[In], Any] =
        ZChannel.readWithCause(
          inputChunk => {
            var buffer = null.asInstanceOf[collection.mutable.ArrayBuffer[Chunk[In]]]
            inputChunk.foldLeft((leftover getOrElse Chunk.empty, delimiterIndex)) {
              case ((carry, delimiterCursor), a) =>
                val concatenated = carry :+ a
                if (delimiterCursor < delimiter.length && a == delimiter(delimiterCursor)) {
                  if (delimiterCursor + 1 == delimiter.length) {
                    if (buffer eq null) buffer = collection.mutable.ArrayBuffer[Chunk[In]]()
                    buffer += concatenated.take(concatenated.length - delimiter.length)
                    (Chunk.empty, 0)
                  } else (concatenated, delimiterCursor + 1)
                } else (concatenated, if (a == delimiter(0)) 1 else 0)
            } match {
              case (carry, delimiterCursor) =>
                ZChannel.writeChunk(if (buffer eq null) Chunk.empty else Chunk.fromArray(buffer.toArray)) *> next(
                  if (carry.nonEmpty) Some(carry) else None,
                  delimiterCursor
                )
            }
          },
          halt =>
            leftover match {
              case Some(chunk) => ZChannel.write(chunk) *> ZChannel.failCause(halt)
              case None        => ZChannel.failCause(halt)
            },
          done =>
            leftover match {
              case Some(chunk) => ZChannel.write(chunk) *> ZChannel.succeed(done)
              case None        => ZChannel.succeed(done)
            }
        )
      new ZPipeline(next(None, 0))
    }

  /**
   * Splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX
   * newlines (`\n`).
   */
  def splitLines(implicit trace: Trace): ZPipeline[Any, Nothing, String, String] = {
    def next(
      leftover: Option[String],
      wasSplitCRLF: Boolean
    ): ZChannel[Any, ZNothing, Chunk[String], Any, Nothing, Chunk[String], Any] =
      ZChannel.readWithCause(
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
                        else (sliceStart, false, false)
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

    new ZPipeline(next(None, wasSplitCRLF = false))
  }

  /**
   * Lazily constructs a pipeline.
   */
  def suspend[Env, Err, In, Out](pipeline: => ZPipeline[Env, Err, In, Out]): ZPipeline[Env, Err, In, Out] =
    new ZPipeline(ZChannel.suspend(pipeline.channel))

  /**
   * Creates a pipeline that takes n elements.
   */
  def take[In](n: => Long)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] =
    ZPipeline.suspend {

      def loop(n: Long): ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[In], Any] =
        ZChannel
          .readWith(
            (chunk: Chunk[In]) => {
              val taken    = chunk.take(n.min(Int.MaxValue).toInt)
              val leftover = (n - taken.length).max(0)
              val more     = leftover > 0

              if (more)
                ZChannel.write(taken) *> loop(leftover)
              else ZChannel.write(taken)
            },
            ZChannel.fail(_),
            ZChannel.succeed(_)
          )

      new ZPipeline(
        if (0 < n)
          loop(n)
        else
          ZChannel.unit
      )
    }

  /**
   * Creates a pipeline that takes elements until the specified predicate
   * evaluates to true.
   */
  def takeUntil[In](f: In => Boolean)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] = {
    lazy val loop: ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[In], Any] =
      ZChannel
        .readWith(
          (chunk: Chunk[In]) => {
            val taken = chunk.takeWhile(!f(_))
            val last  = chunk.drop(taken.length).take(1)

            if (last.isEmpty) ZChannel.write(taken) *> loop
            else ZChannel.write(taken ++ last)
          },
          ZChannel.fail(_),
          ZChannel.succeed(_)
        )

    new ZPipeline(loop)
  }

  /**
   * Creates a pipeline that takes elements while the specified predicate
   * evaluates to true.
   */
  def takeWhile[In](f: In => Boolean)(implicit trace: Trace): ZPipeline[Any, Nothing, In, In] = {
    lazy val loop: ZChannel[Any, ZNothing, Chunk[In], Any, Nothing, Chunk[In], Any] =
      ZChannel
        .readWith(
          (chunk: Chunk[In]) => {
            val taken = chunk.takeWhile(f)
            val more  = taken.length == chunk.length

            if (more) ZChannel.write(taken) *> loop
            else ZChannel.write(taken)
          },
          ZChannel.fail(_),
          ZChannel.succeed(_)
        )

    new ZPipeline(loop)
  }

  /**
   * Creates a pipeline produced from an effect.
   */
  def unwrap[Env, Err, In, Out](zio: ZIO[Env, Err, ZPipeline[Env, Err, In, Out]])(implicit
    trace: Trace
  ): ZPipeline[Env, Err, In, Out] =
    new ZPipeline(ZChannel.unwrap(zio.map(_.channel)))

  /**
   * Creates a single-valued pipeline from a scoped resource
   */
  def scoped[Env]: ScopedPartiallyApplied[Env] =
    new ScopedPartiallyApplied[Env]

  /**
   * Created a pipeline produced from a scoped effect.
   */
  def unwrapScoped[Env]: UnwrapScopedPartiallyApplied[Env] =
    new UnwrapScopedPartiallyApplied[Env]

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the US ASCII charset
   */
  def usASCIIDecode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeStringWith(StandardCharsets.US_ASCII)

  /**
   * utfDecode determines the right encoder to use based on the Byte Order Mark
   * (BOM). If it doesn't detect one, it defaults to utf8Decode. In the case of
   * utf16 and utf32 without BOM, `utf16Decode` and `utf32Decode` should be used
   * instead as both default to their own default decoder respectively.
   */
  def utfDecode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
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

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the UTF_8 charset
   */
  def utf8Decode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    utfDecodeDetectingBom(
      bomSize = 3,
      {
        case BOM.Utf8 =>
          Chunk.empty -> utf8DecodeNoBom
        case bytes =>
          bytes -> utf8DecodeNoBom
      }
    )

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the UTF_16 charset
   */
  def utf16Decode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
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

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the UTF_16BE charset
   */
  def utf16BEDecode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeStringWith(StandardCharsets.UTF_16BE)

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the UTF_16LE charset
   */
  def utf16LEDecode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeStringWith(StandardCharsets.UTF_16LE)

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the UTF_32 charset
   */
  def utf32Decode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    utfDecodeDetectingBom(
      bomSize = 4,
      {
        case bytes @ BOM.Utf32LE =>
          bytes -> utf32LEDecode
        case bytes =>
          bytes -> utf32BEDecode
      }
    )

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the UTF_32BE charset
   */
  def utf32BEDecode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeStringWith(CharsetUtf32BE)

  /**
   * Creates a pipeline that converts a stream of bytes into a stream of strings
   * using the UTF_32LE charset
   */
  def utf32LEDecode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeStringWith(CharsetUtf32LE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the US ASCII charset
   */
  def usASCIIEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.US_ASCII)

//    `utf*Encode` pipelines adhere to the same behavior of Java's
//    String#getBytes(charset)`, that is:
//      - utf8: No BOM
//      - utf16: Has BOM (the outlier)
//      - utf16BE & utf16LE: No BOM
//      - All utf32 variants: No BOM
//
//    If BOM is required, users can use the `*WithBomEncode` variants. (As
//    alluded above, `utf16Encode` always prepends BOM, just like
//    `getBytes("UTF-16")` in Java. In fact, it is an alias to both
//    `utf16BEWithBomEncode` and `utf16WithBomEncode`.

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_8 charset, without adding a BOM
   */
  def utf8Encode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.UTF_8)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_8 charset prefixing it with a BOM
   */
  def utf8WithBomEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.UTF_8, bom = BOM.Utf8)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_16BE charset, without adding a BOM
   */
  def utf16BEEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.UTF_16BE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_16BE charset prefixing it with a BOM
   */
  def utf16BEWithBomEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.UTF_16BE, bom = BOM.Utf16BE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_16LE charset, without adding a BOM
   */
  def utf16LEEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.UTF_16LE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_16LE charset prefixing it with a BOM
   */
  def utf16LEWithBomEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(StandardCharsets.UTF_16LE, bom = BOM.Utf16LE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_16BE charset prefixing it with a BOM
   */
  def utf16Encode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    utf16BEWithBomEncode

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_16 charset prefixing it with a BOM
   */
  def utf16WithBomEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    utf16BEWithBomEncode

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_32BE charset, without adding a BOM
   */
  def utf32BEEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(CharsetUtf32BE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_32BE charset prefixing it with a BOM
   */
  def utf32BEWithBomEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(CharsetUtf32BE, bom = BOM.Utf32BE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_32LE charset, without adding a BOM
   */
  def utf32LEEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(CharsetUtf32LE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_32LE charset prefixing it with a BOM
   */
  def utf32LEWithBomEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    encodeStringWith(CharsetUtf32LE, bom = BOM.Utf32LE)

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_32BE charset, without adding a BOM
   */
  def utf32Encode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    utf32BEEncode

  /**
   * Creates a pipeline that converts a stream of strings into a stream of bytes
   * using the UTF_32BE charset prefixing it with a BOM
   */
  def utf32WithBomEncode(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, String, Byte] =
    utf32BEWithBomEncode

  private def utfDecodeDetectingBom(
    bomSize: => Int,
    processBom: Chunk[Byte] => (
      Chunk[Byte],
      ZPipeline[Any, CharacterCodingException, Byte, String]
    )
  )(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    ZPipeline.suspend {

      type DecodingChannel = ZChannel[Any, ZNothing, Chunk[Byte], Any, CharacterCodingException, Chunk[String], Any]

      def lookingForBom(buffer: Chunk[Byte], bomSize: Int): DecodingChannel =
        ZChannel.readWith(
          received => {
            val data = buffer ++ received

            if (data.length >= bomSize) {
              val (bom, rest)                      = data.splitAt(bomSize)
              val (bomRemainder, decodingPipeline) = processBom(bom)

              val decoderChannel = decodingPipeline.channel
              (ZChannel.write(bomRemainder ++ rest) *> ZChannel.identity[ZNothing, Chunk[Byte], Any]) >>> decoderChannel
            } else {
              lookingForBom(data, bomSize)
            }
          },
          error = ZChannel.fail(_),
          done = _ =>
            if (buffer.isEmpty) ZChannel.unit
            else {
              val (bomRemainder, decodingPipeline) = processBom(buffer)
              (ZChannel
                .write(bomRemainder) *> ZChannel.identity[ZNothing, Chunk[Byte], Any]) >>> decodingPipeline.channel
            }
        )

      new ZPipeline(lookingForBom(Chunk.empty, bomSize))
    }

  private def utf8DecodeNoBom(implicit trace: Trace): ZPipeline[Any, CharacterCodingException, Byte, String] =
    decodeStringWith(StandardCharsets.UTF_8)

  final class EnvironmentWithPipelinePartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {
    def apply[Env1 <: Env, Err, In, Out](f: ZEnvironment[Env] => ZPipeline[Env1, Err, In, Out])(implicit
      trace: Trace
    ): ZPipeline[Env with Env1, Err, In, Out] =
      ZPipeline.unwrap(ZIO.environmentWith(f))
  }

  final class ServiceWithPipelinePartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[Env <: Service, Err, In, Out](f: Service => ZPipeline[Env, Err, In, Out])(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZPipeline[Env with Service, Err, In, Out] =
      ZPipeline.unwrap(ZIO.serviceWith[Service](f))
  }

  final class UnwrapScopedPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {
    def apply[Err, In, Out](scoped: => ZIO[Scope with Env, Err, ZPipeline[Env, Err, In, Out]])(implicit
      trace: Trace
    ): ZPipeline[Env, Err, In, Out] =
      new ZPipeline(ZChannel.unwrapScoped[Env](scoped.map(_.channel)))
  }

  final class ScopedPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {
    def apply[Err, In](scoped: => ZIO[Scope with Env, Err, In])(implicit
      trace: Trace
    ): ZPipeline[Env, Err, In, In] =
      new ZPipeline(ZChannel.scoped[Env](scoped.map(Chunk.single)))
  }
}
