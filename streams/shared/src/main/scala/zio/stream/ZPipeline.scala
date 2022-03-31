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

import java.nio.charset.{Charset, StandardCharsets}
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
final case class ZPipeline[-Env, +Err, -In, +Out](
  channel: ZChannel[Env, ZNothing, Chunk[In], Any, Err, Chunk[Out], Any]
) {
  self =>

  def apply[Env1 <: Env, Err1 >: Err](stream: => ZStream[Env1, Err1, In])(implicit
    trace: ZTraceElement
  ): ZStream[Env1, Err1, Out] =
    ZStream.suspend(stream).pipeThroughChannelOrFail(channel)

  /**
   * Composes two pipelines into one pipeline, by first applying the
   * transformation of this pipeline, and then applying the transformation of
   * the specified pipeline.
   */
  def >>>[Env1 <: Env, Err1 >: Err, Out2](
    that: => ZPipeline[Env1, Err1, Out, Out2]
  )(implicit trace: ZTraceElement): ZPipeline[Env1, Err1, In, Out2] =
    ZPipeline(self.channel.pipeToOrFail(that.channel))

  /**
   * Compose this transducer with a sink, resulting in a sink that processes
   * elements by piping them through this transducer and piping the results into
   * the sink.
   */
  def >>>[Env1 <: Env, Err1 >: Err, Leftover, Out2](that: => ZSink[Env1, Err1, Out, Leftover, Out2])(implicit
    trace: ZTraceElement
  ): ZSink[Env1, Err1, In, Leftover, Out2] =
    new ZSink(self.channel.pipeToOrFail(that.channel))

  /**
   * Composes two pipelines into one pipeline, by first applying the
   * transformation of the specified pipeline, and then applying the
   * transformation of this pipeline.
   */
  def <<<[Env1 <: Env, Err1 >: Err, In2](
    that: => ZPipeline[Env1, Err1, In2, In]
  )(implicit trace: ZTraceElement): ZPipeline[Env1, Err1, In2, Out] =
    ZPipeline.suspend(ZPipeline(that.channel.pipeToOrFail(self.channel)))

  /**
   * A named version of the `>>>` operator.
   */
  def andThen[Env1 <: Env, Err1 >: Err, Out2](
    that: => ZPipeline[Env1, Err1, Out, Out2]
  )(implicit trace: ZTraceElement): ZPipeline[Env1, Err1, In, Out2] =
    self >>> that

  /**
   * A named version of the `<<<` operator.
   */
  def compose[Env1 <: Env, Err1 >: Err, In2](
    that: => ZPipeline[Env1, Err1, In2, In]
  )(implicit trace: ZTraceElement): ZPipeline[Env1, Err1, In2, Out] =
    self <<< that
}

object ZPipeline extends ZPipelinePlatformSpecificConstructors {

  def branchAfter[Env, Err, In, Out](
    n: => Int
  )(f: Chunk[In] => ZPipeline[Env, Err, In, Out])(implicit trace: ZTraceElement): ZPipeline[Env, Err, In, Out] =
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

      ZPipeline(collecting(Chunk.empty))
    }

  /**
   * Creates a pipeline that exposes the chunk structure of the stream.
   */
  def chunks[In](implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, Chunk[In]] =
    ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOut(Chunk.single))

  /**
   * Creates a pipeline that collects elements with the specified partial
   * function.
   *
   * {{{
   * ZPipeline.collect[Option[Int], Int] { case Some(v) => v }
   * }}}
   */
  def collect[In, Out](f: PartialFunction[In, Out])(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, Out] =
    ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOut(_.collect(f)))

  /**
   * Creates a pipeline that drops n elements.
   */
  def drop[In](n: => Int)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] =
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

      ZPipeline(loop(n))
    }

  /**
   * Creates a pipeline that drops elements until the specified predicate
   * evaluates to true.
   *
   * {{{
   * ZPipeline.dropUntil[Int](_ > 100)
   * }}}
   */
  def dropUntil[In](f: In => Boolean)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] =
    ZPipeline.dropWhile[In](!f(_)) >>> ZPipeline.drop(1)

  /**
   * Creates a pipeline that drops elements while the specified predicate
   * evaluates to true.
   *
   * {{{
   * ZPipeline.dropWhile[Int](_ <= 100)
   * }}}
   */
  def dropWhile[In](f: In => Boolean)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] = {

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

    ZPipeline(dropWhile(f))
  }

  /**
   * Creates a pipeline that filters elements according to the specified
   * predicate.
   */
  def filter[In](f: In => Boolean)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] =
    ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOut(_.filter(f)))

  /**
   * Creates a pipeline that groups on adjacent keys, calculated by function f.
   */
  def groupAdjacentBy[In, Key](
    f: In => Key
  )(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, (Key, NonEmptyChunk[In])] = {
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

    ZPipeline(chunkAdjacent(None))
  }

  /**
   * Creates a pipeline that sends all the elements through the given channel.
   */
  def fromChannel[Env, Err, In, Out](
    channel: => ZChannel[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any]
  ): ZPipeline[Env, Err, In, Out] =
    ZPipeline(channel)

  /**
   * Creates a pipeline from a chunk processing function.
   */
  def fromPush[Env, Err, In, Out](
    push: => ZIO[Scope with Env, Nothing, Option[Chunk[In]] => ZIO[Env, Err, Chunk[Out]]]
  )(implicit trace: ZTraceElement): ZPipeline[Env, Err, In, Out] = {

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

    ZPipeline(channel)
  }

  /**
   * Creates a pipeline that repeatedly sends all elements through the given
   * sink.
   */
  def fromSink[Env, Err, In, Out](
    sink: => ZSink[Env, Err, In, In, Out]
  )(implicit trace: ZTraceElement): ZPipeline[Env, Err, In, Out] =
    ZPipeline(
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
          sink.channel.doneCollect.flatMap { case (leftover, z) =>
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
  def identity[In](implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] =
    ZPipeline(ZChannel.identity)

  def iso_8859_1Decode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    textDecodeUsing(StandardCharsets.ISO_8859_1)

  def iso_8859_1Encode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.ISO_8859_1)

  /**
   * Creates a pipeline that maps elements with the specified function.
   */
  def map[In, Out](f: In => Out)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, Out] =
    ZPipeline(ZChannel.identity.mapOut(_.map(f)))

  /**
   * Creates a pipeline that statefully maps elements with the specified
   * function.
   */
  def mapAccum[In, State, Out](
    s: => State
  )(f: (State, In) => (State, Out))(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, Out] =
    mapAccumZIO(s)((s, in) => ZIO.succeedNow(f(s, in)))

  /**
   * Creates a pipeline that statefully maps elements with the specified effect.
   */
  def mapAccumZIO[Env, Err, In, State, Out](
    s: => State
  )(f: (State, In) => ZIO[Env, Err, (State, Out)])(implicit trace: ZTraceElement): ZPipeline[Env, Err, In, Out] =
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

      ZPipeline(accumulator(s))
    }

  /**
   * Creates a pipeline that maps chunks of elements with the specified
   * function.
   */
  @deprecated("use map", "2.0.0")
  def mapChunks[In, Out](
    f: Chunk[In] => Chunk[Out]
  )(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, Out] =
    ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOut(f))

  /**
   * Creates a pipeline that maps chunks of elements with the specified effect.
   */
  @deprecated("use mapZIO", "2.0.0")
  def mapChunksZIO[Env, Err, In, Out](
    f: Chunk[In] => ZIO[Env, Err, Chunk[Out]]
  )(implicit trace: ZTraceElement): ZPipeline[Env, Err, In, Out] =
    ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOutZIO(f))

  /**
   * Creates a pipeline that maps elements with the specified effectful
   * function.
   */
  def mapZIO[Env, Err, In, Out](f: In => ZIO[Env, Err, Out])(implicit
    trace: ZTraceElement
  ): ZPipeline[Env, Err, In, Out] =
    ZPipeline(ZChannel.identity[Nothing, Chunk[In], Any].mapOutZIO(_.mapZIO(f)))

  /**
   * Emits the provided chunk before emitting any other value.
   */
  def prepend[In](values: => Chunk[In])(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] =
    ZPipeline(ZChannel.write(values) *> ZChannel.identity)

  /**
   * A pipeline that rechunks the stream into chunks of the specified size.
   */
  def rechunk[In](n: => Int)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] = {

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

    val target = n
    ZPipeline(ZChannel.suspend(process(new ZStream.Rechunker(target), target)))
  }

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scan[In, Out](s: => Out)(f: (Out, In) => Out)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, Out] =
    scanZIO(s)((out, in) => ZIO.succeedNow(f(out, in)))

  /**
   * Creates a pipeline that scans elements with the specified function.
   */
  def scanZIO[Env, Err, In, Out](
    s: => Out
  )(f: (Out, In) => ZIO[Env, Err, Out])(implicit trace: ZTraceElement): ZPipeline[Env, Err, In, Out] =
    ZPipeline.suspend {
      ZPipeline(
        ZChannel.write(Chunk.single(s)) *>
          mapAccumZIO[Env, Err, In, Out, Out](s)((s, a) => f(s, a).map(s => (s, s))).channel
      )
    }

  /**
   * Splits strings on a delimiter.
   */
  def splitOn(delimiter: => String)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, String] =
    ZPipeline.map[String, Chunk[Char]](string => Chunk.fromArray(string.toArray)) >>>
      ZPipeline.unchunks[Char] >>>
      ZPipeline.splitOnChunk(Chunk.fromArray(delimiter.toArray)) >>>
      ZPipeline.chunks[Char] >>>
      ZPipeline.map[Chunk[Char], String](chunk => chunk.mkString(""))

  /**
   * Splits strings on a delimiter.
   */
  def splitOnChunk[In](delimiter: => Chunk[In])(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] =
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
      ZPipeline(next(None, 0))
    }

  /**
   * Splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX
   * newlines (`\n`).
   */
  def splitLines(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, String] = {
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

    ZPipeline(next(None, wasSplitCRLF = false))
  }

  /**
   * Lazily constructs a pipeline.
   */
  def suspend[Env, Err, In, Out](pipeline: => ZPipeline[Env, Err, In, Out]): ZPipeline[Env, Err, In, Out] =
    ZPipeline(ZChannel.suspend(pipeline.channel))

  /**
   * Creates a pipeline that takes n elements.
   */
  def take[In](n: => Long)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] =
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

      ZPipeline(
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
  def takeUntil[In](f: In => Boolean)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] = {
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

    ZPipeline(loop)
  }

  /**
   * Creates a pipeline that takes elements while the specified predicate
   * evaluates to true.
   */
  def takeWhile[In](f: In => Boolean)(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, In, In] = {
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

    ZPipeline(loop)
  }

  /**
   * Creates a pipeline that submerges chunks into the structure of the stream.
   */
  def unchunks[In](implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Chunk[In], In] =
    ZPipeline(ZChannel.identity[Nothing, Chunk[Chunk[In]], Any].mapOut(_.flatten))

  def usASCIIDecode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    textDecodeUsing(StandardCharsets.US_ASCII)

  /**
   * utfDecode determines the right encoder to use based on the Byte Order Mark
   * (BOM). If it doesn't detect one, it defaults to utf8Decode. In the case of
   * utf16 and utf32 without BOM, `utf16Decode` and `utf32Decode` should be used
   * instead as both default to their own default decoder respectively.
   */
  def utfDecode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
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

  def utf8Decode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeDetectingBom(
      bomSize = 3,
      {
        case BOM.Utf8 =>
          Chunk.empty -> utf8DecodeNoBom
        case bytes =>
          bytes -> utf8DecodeNoBom
      }
    )

  def utf16Decode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
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

  def utf16BEDecode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(StandardCharsets.UTF_16BE, fixedLength = 2)

  def utf16LEDecode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(StandardCharsets.UTF_16LE, fixedLength = 2)

  def utf32Decode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeDetectingBom(
      bomSize = 4,
      {
        case bytes @ BOM.Utf32LE =>
          bytes -> utf32LEDecode
        case bytes =>
          bytes -> utf32BEDecode
      }
    )

  def utf32BEDecode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(CharsetUtf32BE, fixedLength = 4)

  def utf32LEDecode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    utfDecodeFixedLength(CharsetUtf32LE, fixedLength = 4)

  def usASCIIEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
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
  def utf8Encode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_8)

  def utf8WithBomEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_8, bom = BOM.Utf8)

  def utf16BEEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16BE)

  def utf16BEWithBomEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16BE, bom = BOM.Utf16BE)

  def utf16LEEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16LE)

  def utf16LEWithBomEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(StandardCharsets.UTF_16LE, bom = BOM.Utf16LE)

  def utf16Encode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utf16BEWithBomEncode

  def utf16WithBomEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utf16BEWithBomEncode

  def utf32BEEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32BE)

  def utf32BEWithBomEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32BE, bom = BOM.Utf32BE)

  def utf32LEEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32LE)

  def utf32LEWithBomEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utfEncodeFor(CharsetUtf32LE, bom = BOM.Utf32LE)

  def utf32Encode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utf32BEEncode

  def utf32WithBomEncode(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    utf32BEWithBomEncode

  private def textDecodeUsing(
    charset: => Charset
  )(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    ZPipeline.suspend {

      def stringChunkFrom(bytes: Chunk[Byte], charset: Charset) =
        Chunk.single(
          new String(bytes.toArray, charset)
        )

      def transform(charset: Charset): ZChannel[Any, ZNothing, Chunk[Byte], Any, Nothing, Chunk[String], Any] =
        ZChannel.readWith(
          received => {
            if (received.isEmpty)
              transform(charset)
            else
              ZChannel.write(stringChunkFrom(received, charset))
          },
          error = ZChannel.fail(_),
          done = _ => ZChannel.unit
        )

      ZPipeline(transform(charset))
    }

  private def utfDecodeDetectingBom(
    bomSize: => Int,
    processBom: Chunk[Byte] => (
      Chunk[Byte],
      ZPipeline[Any, Nothing, Byte, String]
    )
  )(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] =
    ZPipeline.suspend {

      type DecodingChannel = ZChannel[Any, ZNothing, Chunk[Byte], Any, Nothing, Chunk[String], Any]

      def passThrough(
        decodingPipeline: ZPipeline[Any, Nothing, Byte, String]
      ): DecodingChannel =
        ZChannel.readWith(
          received => decodingPipeline(ZStream.fromChunk(received)).channel *> passThrough(decodingPipeline),
          error = ZChannel.fail(_),
          done = _ => ZChannel.unit
        )

      def lookingForBom(buffer: Chunk[Byte], bomSize: Int): DecodingChannel =
        ZChannel.readWith(
          received => {
            val data = buffer ++ received

            if (data.length >= bomSize) {
              val (bom, rest)                        = data.splitAt(bomSize)
              val (dataWithoutBom, decodingPipeline) = processBom(bom)

              decodingPipeline(ZStream.fromChunk(dataWithoutBom ++ rest)).channel *> passThrough(decodingPipeline)
            } else {
              lookingForBom(data, bomSize)
            }
          },
          error = ZChannel.fail(_),
          done = _ =>
            if (buffer.isEmpty) ZChannel.unit
            else {
              val (dataWithoutBom, decodingPipeline) = processBom(buffer)
              decodingPipeline(ZStream.fromChunk(dataWithoutBom)).channel *> passThrough(decodingPipeline)
            }
        )

      ZPipeline(lookingForBom(Chunk.empty, bomSize))
    }

  private def utf8DecodeNoBom(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, String] = {

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

    def readThenTransduce(buffer: Chunk[Byte]): ZChannel[Any, ZNothing, Chunk[Byte], Any, Nothing, Chunk[String], Any] =
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

    ZPipeline(readThenTransduce(emptyByteChunk))
  }

  private def utfDecodeFixedLength(charset: => Charset, fixedLength: => Int)(implicit
    trace: ZTraceElement
  ): ZPipeline[Any, Nothing, Byte, String] =
    ZPipeline.suspend {

      val emptyByteChunk: Chunk[Byte] =
        Chunk.empty
      val emptyStringChunk =
        Chunk.single("")

      def stringChunkFrom(bytes: Chunk[Byte], charset: Charset) =
        Chunk.single(
          new String(bytes.toArray, charset)
        )

      def process(
        buffered: Chunk[Byte],
        received: Chunk[Byte],
        charset: Charset,
        fixedLength: Int
      ): (Chunk[String], Chunk[Byte]) = {
        val bytes     = buffered ++ received
        val remainder = bytes.length % fixedLength

        if (remainder == 0) {
          stringChunkFrom(bytes, charset) -> emptyByteChunk
        } else if (bytes.length > fixedLength) {
          val (fullChunk, rest) = bytes.splitAt(bytes.length - remainder)

          stringChunkFrom(fullChunk, charset) -> rest
        } else {
          emptyStringChunk -> bytes.materialize
        }
      }

      def readThenTransduce(
        buffer: Chunk[Byte],
        charset: Charset,
        fixedLength: Int
      ): ZChannel[Any, ZNothing, Chunk[Byte], Any, Nothing, Chunk[String], Any] =
        ZChannel.readWith(
          received => {
            val (string, buffered) = process(buffer, received, charset, fixedLength)

            ZChannel.write(string) *> readThenTransduce(buffered, charset, fixedLength)
          },
          error = ZChannel.fail(_),
          done = _ =>
            if (buffer.isEmpty)
              ZChannel.unit
            else
              ZChannel.write(stringChunkFrom(buffer, charset))
        )

      ZPipeline(readThenTransduce(emptyByteChunk, charset, fixedLength))
    }

  private def utfEncodeFor(
    charset: => Charset,
    bom: => Chunk[Byte] = Chunk.empty
  )(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, String, Byte] =
    ZPipeline.suspend {
      def transform: ZChannel[Any, ZNothing, Chunk[String], Any, Nothing, Chunk[Byte], Any] =
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

              ZChannel.write(bytes) *> transform
            },
          error = ZChannel.fail(_),
          done = _ => ZChannel.unit
        )

      ZPipeline(ZChannel.write(bom) *> transform)
    }
}
