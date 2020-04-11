package zio.stream.experimental

import scala.collection.mutable

import zio._

abstract class ZTransducer[-R, +E, -I, +O](
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Option[E], Chunk[O]]]
) extends ZConduit[R, E, I, O, Unit](push.map(_.andThen(_.mapError {
      case Some(e) => Left(e)
      case None    => Right(())
    }))) { self =>

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](that: ZTransducer[R1, E1, O2, O3]): ZTransducer[R1, E1, I, O3] =
    ZTransducer {
      for {
        pushLeft  <- self.push
        pushRight <- that.push
        push = (input: Option[Chunk[I]]) =>
          pushLeft(input).foldM(
            {
              case Some(e) => ZIO.fail(Some(e))
              case None    => pushRight(None)
            },
            chunk => pushRight(Some(chunk))
          )
      } yield push
    }

  /**
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, I1 <: I, Z](that: ZSink[R1, E1, O2, Z]): ZSink[R1, E1, I1, Z] =
    ZSink {
      for {
        pushSelf <- self.push
        pushThat <- that.push
        push = (is: Option[Chunk[I]]) =>
          pushSelf(is).foldM(
            {
              case Some(e) => ZIO.fail(Left(e))
              case None    => pushThat(None)
            },
            chunk => pushThat(Some(chunk))
          )
      } yield push
    }

  final def map[P](f: O => P): ZTransducer[R, E, I, P] =
    ZTransducer(self.push.map(push => i => push(i).map(_.map(f))))
}

object ZTransducer {
  def apply[R, E, I, O](
    push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Option[E], Chunk[O]]]
  ): ZTransducer[R, E, I, O] =
    new ZTransducer(push) {}

  /**
   * A transducer that re-chunks the elements fed to it into chunks of up to
   * `n` elements each.
   */
  def chunkN[I](n: Int): ZTransducer[Any, Nothing, I, I] =
    ZTransducer {
      for {
        buffered <- ZRef.makeManaged[Chunk[I]](Chunk.empty)
        push = { (input: Option[Chunk[I]]) =>
          input match {
            case None =>
              buffered.modify(buf => (if (buf.isEmpty) Push.done else Push.emit(buf)) -> Chunk.empty).flatten
            case Some(is) =>
              buffered.modify { buf0 =>
                val buf = buf0 ++ is
                if (buf.length >= n) {
                  val (out, buf1) = buf.splitAt(n)
                  Push.emit(out) -> buf1
                } else
                  Push.next -> buf
              }.flatten
          }
        }
      } yield push
    }

  def collectAllWhile[I](p: I => Boolean): ZTransducer[Any, Nothing, I, List[I]] =
    ZTransducer {
      for {
        buffered <- ZRef.makeManaged[(Chunk[I], Chunk[I])](Chunk.empty -> Chunk.empty)
        push = { (in: Option[Chunk[I]]) =>
          buffered.modify {
            case (buf0, out0) =>
              if (buf0.isEmpty && in.isEmpty)
                (if (out0.isEmpty) Push.done else Push.emit(out0.toList)) -> (Chunk.empty -> Chunk.empty)
              else {
                val buf = in.foldLeft(buf0)(_ ++ _)
                val out = buf.takeWhile(p)
                if (out.nonEmpty && out.length < buf.length)
                  Push.emit((out0 ++ out).toList) -> (buf.drop(out.length).dropWhile(!p(_)) -> Chunk.empty)
                else
                  Push.next -> (buf.drop(out.length).dropWhile(!p(_)) -> (out0 ++ out))
              }
          }.flatten
        }
      } yield push
    }

  def die(e: => Throwable): ZTransducer[Any, Nothing, Any, Nothing] =
    ZTransducer(Managed.succeed((_: Any) => IO.die(e)))
    
  def fail[E](e: => E): ZTransducer[Any, E, Any, Nothing] =
    ZTransducer(ZManaged.succeed((_: Option[Any]) => Push.fail(e)))

  def fromEffect[R, E, A](zio: ZIO[R, E, A]): ZTransducer[R, E, Any, A] =
    ZTransducer(Managed.succeed((_: Any) => zio.map(Chunk.single(_)).mapError(Some(_))))

  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, Option[E], Chunk[O]]): ZTransducer[R, E, I, O] =
    ZTransducer(Managed.succeed(push))

  /**
   * Splits strings on newlines. Handles both Windows newlines (`\r\n`) and UNIX newlines (`\n`).
   */
  val splitLines: ZTransducer[Any, Nothing, String, String] =
    ZTransducer {
      ZRef.makeManaged[(Option[String], Boolean)]((None, false)).map { stateRef =>
        {
          case None =>
            stateRef.getAndSet((None, false)).flatMap {
              case (None, _)      => ZIO.fail(None)
              case (Some(str), _) => ZIO.succeed(Chunk(str))
            }

          case Some(strings) =>
            stateRef.modify {
              case (leftover, wasSplitCRLF) =>
                val buf    = mutable.ArrayBuffer[String]()
                var inCRLF = wasSplitCRLF
                var carry  = leftover getOrElse ""

                (Chunk.fromIterable(leftover) ++ strings).foreach { string =>
                  val concat = carry + string

                  if (concat.length() > 0) {
                    var i =
                      // If we had a split CRLF, we start reading
                      // from the last character of the leftover (which was the '\r')
                      if (inCRLF && carry.length > 0) carry.length - 1
                      // Otherwise we just skip over the entire previous leftover as
                      // it doesn't contain a newline.
                      else carry.length
                    var sliceStart = 0

                    while (i < concat.length()) {
                      if (concat(i) == '\n') {
                        buf += concat.substring(sliceStart, i)
                        i += 1
                        sliceStart = i
                      } else if (concat(i) == '\r' && (i + 1) < concat.length && concat(i + 1) == '\n') {
                        buf += concat.substring(sliceStart, i)
                        i += 2
                        sliceStart = i
                      } else if (concat(i) == '\r' && i == concat.length - 1) {
                        inCRLF = true
                        i += 1
                      } else {
                        i += 1
                      }
                    }

                    carry = concat.substring(sliceStart, concat.length)
                  }
                }

                (Chunk.fromArray(buf.toArray), (if (carry.length() > 0) Some(carry) else None, inCRLF))
            }
        }
      }
    }

  /**
   * Decodes chunks of UTF-8 bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  val utf8Decode: ZTransducer[Any, Nothing, Byte, String] =
    ZTransducer {
      def is2ByteSequenceStart(b: Byte) = (b & 0xE0) == 0xC0
      def is3ByteSequenceStart(b: Byte) = (b & 0xF0) == 0xE0
      def is4ByteSequenceStart(b: Byte) = (b & 0xF8) == 0xF0
      def computeSplit(chunk: Chunk[Byte]) = {
        // There are 3 bad patterns we need to check to detect an incomplete chunk:
        // - 2/3/4 byte sequences that start on the last byte
        // - 3/4 byte sequences that start on the second-to-last byte
        // - 4 byte sequences that start on the third-to-last byte
        //
        // Otherwise, we can convert the entire concatenated chunk to a string.
        val len = chunk.length

        if (len >= 1 &&
            (is2ByteSequenceStart(chunk(len - 1)) ||
            is3ByteSequenceStart(chunk(len - 1)) ||
            is4ByteSequenceStart(chunk(len - 1))))
          len - 1
        else if (len >= 2 &&
                 (is3ByteSequenceStart(chunk(len - 2)) ||
                 is4ByteSequenceStart(chunk(len - 2))))
          len - 2
        else if (len >= 3 && is4ByteSequenceStart(chunk(len - 3)))
          len - 3
        else len
      }

      ZRef.makeManaged[Chunk[Byte]](Chunk.empty).map { stateRef =>
        {
          case None =>
            stateRef.getAndSet(Chunk.empty).flatMap { leftovers =>
              if (leftovers.isEmpty) ZIO.fail(None)
              else ZIO.succeed(Chunk.single(new String(leftovers.toArray[Byte], "UTF-8")))
            }

          case Some(bytes) =>
            stateRef.modify { leftovers =>
              val concat = leftovers ++ bytes

              val (toConvert, newLeftovers) = concat.splitAt(computeSplit(concat))

              if (toConvert.isEmpty) (Chunk.empty, newLeftovers)
              else (Chunk.single(new String(toConvert.toArray[Byte], "UTF-8")), newLeftovers)
            }
        }
      }
    }

  object Push {
    def emit[A](a: A): UIO[Chunk[A]]                 = IO.succeedNow(Chunk.single(a))
    def emit[A](as: Chunk[A]): UIO[Chunk[A]]         = IO.succeedNow(as)
    def fail[E](e: E): IO[Option[E], Nothing]        = IO.fail(Some(e))
    def halt[E](c: Cause[E]): IO[Option[E], Nothing] = IO.halt(c).mapError(Some(_))
    val done: IO[Option[Nothing], Nothing]           = IO.fail(None)
    val next: UIO[Chunk[Nothing]]                    = IO.succeedNow(Chunk.empty)
  }
}
