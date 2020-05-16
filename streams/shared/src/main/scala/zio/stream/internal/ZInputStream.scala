package zio.stream.internal

import zio.{ Chunk, Exit, FiberFailure, ZIO }
import zio.Runtime
import scala.annotation.tailrec

private[zio] class ZInputStream(chunks: Iterator[Chunk[Byte]]) extends java.io.InputStream {
  private var current: Iterator[Byte]      = Iterator.empty
  private var availableInCurrentChunk: Int = 0
  private var done: Boolean                = false

  private def loadNext(): Unit =
    if (chunks.hasNext) {
      val c = chunks.next()
      availableInCurrentChunk = c.length
      current = c.iterator
    } else {
      done = true
    }

  override def read(): Int = {
    @tailrec
    def go(): Int =
      if (done) {
        -1
      } else {
        if (current.hasNext) {
          availableInCurrentChunk -= 1
          current.next() & 0xFF
        } else {
          loadNext()
          go()
        }
      }

    go()
  }

  override def read(bytes: Array[Byte], off: Int, len: Int): Int =
    if (done) {
      -1
    } else {
      //cater to InputStream specification
      if (len != 0) {
        val written = doRead(bytes, off, len, 0)
        if (written == 0) -1 else written
      } else {
        0
      }
    }

  @tailrec
  private def doRead(bytes: Array[Byte], off: Int, len: Int, written: Int): Int =
    if (len <= availableInCurrentChunk) {
      availableInCurrentChunk -= len
      readFromCurrentChunk(bytes, off, len)
      written + len
    } else {
      val av = availableInCurrentChunk
      readFromCurrentChunk(bytes, off, av)
      loadNext()
      if (done) {
        written + av
      } else {
        doRead(bytes, off + av, len - av, written + av)
      }
    }

  private def readFromCurrentChunk(bytes: Array[Byte], off: Int, len: Int): Unit = {
    var i: Int = 0
    while (i < len) {
      bytes.update(off + i, current.next())
      i += 1
    }
  }

  override def available(): Int = availableInCurrentChunk
}

private[zio] object ZInputStream {
  def fromPull[R](runtime: Runtime[R], pull: ZIO[R, Option[Throwable], Chunk[Byte]]): ZInputStream = {
    def unfoldPull: Iterator[Chunk[Byte]] =
      runtime.unsafeRunSync(pull) match {
        case Exit.Success(chunk) => Iterator.single(chunk) ++ unfoldPull
        case Exit.Failure(cause) =>
          cause.failureOrCause match {
            case Left(None)    => Iterator.empty
            case Left(Some(e)) => throw e
            case Right(c)      => throw FiberFailure(c)
          }
      }
    new ZInputStream(unfoldPull)
  }
}
