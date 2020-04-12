package zio.stream.experimental

import scala.collection.mutable

import zio._

// Contract notes for transducers:
// - When a None is received, the transducer must flush all of its internal state
//   and remain empty until subsequent Some(Chunk) values.
//
//   Stated differently, after a first push(None), all subsequent push(None) must
//   result in Chunk.empty.
abstract class ZTransducer[-R, +E, -I, +O](
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, E, Chunk[O]]]
) extends ZConduit[R, E, I, O, Nothing](push.map(_.andThen(_.mapError(Left(_))))) { self =>

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](that: ZTransducer[R1, E1, O2, O3]): ZTransducer[R1, E1, I, O3] =
    ZTransducer {
      self.push.zipWith(that.push) { (pushLeft, pushRight) =>
        {
          case None =>
            pushLeft(None).flatMap(cl =>
              if (cl.isEmpty) pushRight(None)
              else pushRight(Some(cl)).zipWith(pushRight(None))(_ ++ _)
            )
          case inputs @ Some(_) =>
            pushLeft(inputs).flatMap(cl => pushRight(Some(cl)))
        }
      }
    }

  /**
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, I1 <: I, Z](that: ZSink[R1, E1, O2, Z]): ZSink[R1, E1, I1, Z] =
    ZSink {
      self.push.zipWith(that.push) { (pushSelf, pushThat) =>
        {
          case None =>
            pushSelf(None)
              .mapError(Left(_))
              .flatMap(chunk => pushThat(Some(chunk)) *> pushThat(None))
          case inputs @ Some(_) =>
            pushSelf(inputs)
              .mapError(Left(_))
              .flatMap(chunk => pushThat(Some(chunk)))
        }
      }
    }

  final def map[P](f: O => P): ZTransducer[R, E, I, P] =
    ZTransducer(self.push.map(push => i => push(i).map(_.map(f))))
}

object ZTransducer {
  def apply[R, E, I, O](
    push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, E, Chunk[O]]]
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
              buffered
                .modify(buf => (if (buf.isEmpty) Push.emit(Chunk.empty) else Push.emit(buf)) -> Chunk.empty)
                .flatten
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
      ZRef.makeManaged[Chunk[I]](Chunk.empty).map { buffered =>
        {
          case None =>
            buffered
              .getAndSet(Chunk.empty)
              .map(out0 => if (out0.isEmpty) Chunk.empty else Chunk.single(out0.toList))
          case Some(in) =>
            buffered.modify { buf0 =>
              val (outBuffer, newBuffer) = (buf0 ++ in).fold((List[List[I]](), List[I]())) {
                case ((outBuffer, newBuffer), el) =>
                  if (!p(el)) (newBuffer.reverse :: outBuffer, List())
                  else (outBuffer, el :: newBuffer)
              }

              (Chunk.fromIterable(outBuffer.reverse.filter(_.nonEmpty)), Chunk.fromIterable(newBuffer.reverse))
            }
        }
      }
    }

  def fail[E](e: => E): ZTransducer[Any, E, Any, Nothing] =
    ZTransducer(ZManaged.succeed((_: Option[Any]) => ZIO.fail(e)))

  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, E, Chunk[O]]): ZTransducer[R, E, I, O] =
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
              case (None, _)      => ZIO.succeed(Chunk.empty)
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
              if (leftovers.isEmpty) ZIO.succeed(Chunk.empty)
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
    def emit[A](a: A): UIO[Chunk[A]]         = IO.succeedNow(Chunk.single(a))
    def emit[A](as: Chunk[A]): UIO[Chunk[A]] = IO.succeedNow(as)
    val next: UIO[Chunk[Nothing]]            = IO.succeedNow(Chunk.empty)
  }
}
