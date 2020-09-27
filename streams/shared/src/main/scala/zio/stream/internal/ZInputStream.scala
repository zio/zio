package zio.stream.internal

import scala.annotation.tailrec

import zio.{ Chunk, Exit, FiberFailure, Runtime, ZIO }

private[zio] class ZInputStream(private var chunks: Iterator[Chunk[Byte]]) extends java.io.InputStream {
  private var current: Chunk[Byte] = Chunk.empty
  private var currentPos: Int      = 0
  private var currentChunkLen: Int = 0
  private var done: Boolean        = false

  @inline private def availableInCurrentChunk: Int = currentChunkLen - currentPos

  @inline
  private def readOne(): Byte = {
    val res = current(currentPos)
    currentPos += 1
    res
  }

  private def loadNext(): Unit =
    if (chunks.hasNext) {
      current = chunks.next()
      currentChunkLen = current.length
      currentPos = 0
    } else {
      done = true
    }

  override def read(): Int = {
    @tailrec
    def go(): Int =
      if (done) {
        -1
      } else {
        if (availableInCurrentChunk > 0) {
          readOne() & 0xff
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
      bytes.update(off + i, readOne())
      i += 1
    }
  }

  override def available(): Int = availableInCurrentChunk

  override def close(): Unit = {
    chunks = Iterator.empty
    loadNext()
  }
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
    new ZInputStream(Iterator.empty ++ unfoldPull)
  }
}
