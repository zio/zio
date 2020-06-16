package zio.stream.compression

import java.util.zip.{ CRC32, DataFormatException, Inflater }
import java.{ util => ju }

import scala.annotation.tailrec

import zio._

private[compression] class Gunzipper private (var state: Gunzipper.State) {
  def onChunk(c: Chunk[Byte]): ZIO[Any, Throwable, Chunk[Byte]] =
    state.feed(c).map {
      case (newState, output) =>
        state = newState
        output
    }

  def close: UIO[Unit] = state.close
}

private[compression] object Gunzipper {

  private val fixedHeaderLength = 10

  sealed trait State {
    def feed: Chunk[Byte] => ZIO[Any, Throwable, (State, Chunk[Byte])]
    def close: UIO[Unit] = ZIO.succeed(())
  }

  private def nextStep(
    bufferSize: Int,
    parsedBytes: Array[Byte],
    checkCrc16: Boolean,
    parseExtra: Boolean,
    commentsToSkip: Int
  ): Gunzipper.State =
    if (parseExtra) new ParseExtraStep(bufferSize, parsedBytes, checkCrc16, commentsToSkip)
    else if (commentsToSkip > 0) new SkipCommentsStep(bufferSize, parsedBytes, checkCrc16, commentsToSkip)
    else if (checkCrc16) new CheckCrc16Step(bufferSize, parsedBytes, Array.empty)
    else Decompress(bufferSize)

  class ParseHeaderStep(bufferSize: Int, oldBytes: Array[Byte]) extends State {

    //TODO: If whole input is shorther than fixed header, not output is produced and no error is singaled. Is it ok?
    def feed: Chunk[Byte] => ZIO[Any, Throwable, (State, Chunk[Byte])] = { c =>
      ZIO.effect {
        val bytes = oldBytes ++ c.toArray[Byte]
        if (bytes.length < fixedHeaderLength) {
          if (bytes.length == oldBytes.length && c.length > 0)
            ZIO.fail(new CompressionException(new RuntimeException("Invalid GZIP header")))
          else ZIO.succeed((new ParseHeaderStep(bufferSize, bytes), Chunk.empty))
        } else {
          val (header, rest) = bytes.splitAt(fixedHeaderLength)
          if (u8(header(0)) != 31 || u8(header(1)) != 139) ZIO.fail(new Exception("Invalid GZIP header"))
          else if (header(2) != 8)
            ZIO.fail(new Exception(s"Only deflate (8) compression method is supported, present: ${header(2)}"))
          else {
            val flags           = header(3) & 0xff
            val checkCrc16      = (flags & 2) > 0
            val hasExtra        = (flags & 4) > 0
            val skipFileName    = (flags & 8) > 0
            val skipFileComment = (flags & 16) > 0
            val commentsToSkip  = (if (skipFileName) 1 else 0) + (if (skipFileComment) 1 else 0)
            nextStep(bufferSize, header, checkCrc16, hasExtra, commentsToSkip).feed(Chunk.fromArray(rest))
          }
        }
      }.flatten
    }
  }

  class ParseExtraStep(bufferSize: Int, bytes: Array[Byte], checkCrc16: Boolean, commentsToSkip: Int) extends State {

    def feed: Chunk[Byte] => ZIO[Any, Throwable, (State, Chunk[Byte])] =
      c =>
        ZIO.effect {
          val header = bytes ++ c.toArray[Byte]
          if (header.length < 12) {
            ZIO.succeed((new ParseExtraStep(bufferSize, header, checkCrc16, commentsToSkip), Chunk.empty))
          } else {
            val extraBytes: Int       = u16(header(fixedHeaderLength), header(fixedHeaderLength + 1))
            val headerWithExtraLength = fixedHeaderLength + extraBytes
            if (header.length < headerWithExtraLength)
              ZIO.succeed((new ParseExtraStep(bufferSize, header, checkCrc16, commentsToSkip), Chunk.empty))
            else {
              val (headerWithExtra, rest) = header.splitAt(headerWithExtraLength)
              nextStep(bufferSize, headerWithExtra, checkCrc16, false, commentsToSkip).feed(Chunk.fromArray(rest))
            }
          }
        }.flatten
  }

  class SkipCommentsStep(bufferSize: Int, pastBytes: Array[Byte], checkCrc16: Boolean, commentsToSkip: Int)
      extends State {
    def feed: Chunk[Byte] => ZIO[Any, Throwable, (State, Chunk[Byte])] =
      c =>
        ZIO.effect {
          val idx           = c.indexWhere(_ == 0)
          val (upTo0, rest) = if (idx == -1) (c, Chunk.empty) else c.splitAt(idx + 1)
          nextStep(bufferSize, pastBytes ++ upTo0.toArray[Byte], checkCrc16, false, commentsToSkip - 1).feed(rest)
        }.flatten
  }

  class CheckCrc16Step(bufferSize: Int, pastBytes: Array[Byte], pastCrc16Bytes: Array[Byte]) extends State {
    def feed: Chunk[Byte] => ZIO[Any, Throwable, (State, Chunk[Byte])] =
      c =>
        ZIO.effect {
          val (crc16Bytes, rest) = (pastCrc16Bytes ++ c.toArray[Byte]).splitAt(2)
          if (crc16Bytes.length < 2) {
            ZIO.succeed((new CheckCrc16Step(bufferSize, pastBytes, crc16Bytes), Chunk.empty))
          } else {
            val crc = new CRC32
            crc.update(pastBytes)
            val computedCrc16 = crc.getValue.toInt & 0xffff
            val expectedCrc   = u16(crc16Bytes(0), crc16Bytes(1))
            if (computedCrc16 != expectedCrc) ZIO.fail(new Exception("CRC16 checksum mismatch"))
            else Decompress(bufferSize).feed(Chunk.fromArray(rest))
          }
        }.flatten
  }
  private[compression] class Decompress private (buffer: Array[Byte], inflater: Inflater, crc32: CRC32) extends State {

    private[compression] def pullOutput(
      inflater: Inflater,
      buffer: Array[Byte]
    ): ZIO[Any, DataFormatException, Chunk[Byte]] =
      ZIO.effect {
        @tailrec
        def next(prev: Chunk[Byte]): Chunk[Byte] = {
          val read     = inflater.inflate(buffer)
          val newBytes = ju.Arrays.copyOf(buffer, read)
          crc32.update(newBytes)
          val current = Chunk.fromArray(newBytes)
          val pulled  = prev ++ current
          if (read > 0 && inflater.getRemaining > 0) next(pulled) else pulled
        }
        if (inflater.needsInput()) Chunk.empty else next(Chunk.empty)
      }.refineOrDie {
        case e: DataFormatException => e
      }

    def feed: Chunk[Byte] => ZIO[Any, Throwable, (State, Chunk[Byte])] =
      chunk => {
        (ZIO.effectTotal(inflater.setInput(chunk.toArray)) *> pullOutput(inflater, buffer)).flatMap { newChunk =>
          if (inflater.finished())
            CheckTrailerStep(buffer.length, crc32, inflater).feed(chunk.takeRight(inflater.getRemaining())).map {
              case (state, _) => (state, newChunk) // CheckTrailerStep returns empty chunk only
            }
          else ZIO.succeed((this, newChunk))
        }
      }
    override def close: UIO[Unit] = ZIO.succeed(())
  }

  object Decompress {
    def apply(bufferSize: Int) = new Decompress(new Array[Byte](bufferSize), new Inflater(true), new CRC32)
  }

  class CheckTrailerStep(bufferSize: Int, oldBytes: Array[Byte], expectedCrc32: Long, expectedIsize: Long)
      extends State {

    def readInt(a: Array[Byte]): Int = u32(a(0), a(1), a(2), a(3))

    override def feed: Chunk[Byte] => ZIO[Any, Throwable, (State, Chunk[Byte])] =
      c => {
        val bytes = oldBytes ++ c.toArray[Byte]
        if (bytes.length < 8) ZIO.succeed((this, Chunk.empty))
        else {
          val (trailerBytes, leftover) = bytes.splitAt(8)
          val crc32                    = readInt(trailerBytes.take(4))
          val isize                    = readInt(trailerBytes.drop(4))
          if (expectedCrc32.toInt != crc32) ZIO.fail(new Exception("Invalid CRC32"))
          else if (expectedIsize.toInt != isize) ZIO.fail(new Exception("Invalid ISIZE"))
          else new ParseHeaderStep(bufferSize, leftover).feed(Chunk.empty)
        }
      }
  }
  object CheckTrailerStep {
    def apply(bufferSize: Int, crc32: CRC32, inflater: Inflater) =
      new CheckTrailerStep(bufferSize, Array.empty, crc32.getValue(), inflater.getBytesWritten())
  }

  def make(bufferSize: Int): ZIO[Any, Nothing, Gunzipper] =
    ZIO.succeed(new Gunzipper(new ParseHeaderStep(bufferSize, Array.empty)))
}
