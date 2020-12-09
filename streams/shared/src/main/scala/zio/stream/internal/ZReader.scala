package zio.stream.internal

import zio.{ Chunk, Exit, FiberFailure, Runtime, ZIO }

import scala.annotation.tailrec

private[zio] class ZReader(private var chunks: Iterator[Chunk[Char]]) extends java.io.Reader {
  private var current: Chunk[Char] = Chunk.empty
  private var currentPos: Int      = 0
  private var currentChunkLen: Int = 0
  private var done: Boolean        = false

  @inline private def availableInCurrentChunk: Int = currentChunkLen - currentPos

  @inline
  private def readOne(): Char = {
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
          readOne().toInt
        } else {
          loadNext()
          go()
        }
      }

    go()
  }

  override def read(cbuf: Array[Char], off: Int, len: Int): Int =
    if (done) {
      -1
    } else {
      if (len != 0) {
        val written = doRead(cbuf, off, len, 0)
        if (written == 0) -1 else written
      } else {
        0
      }
    }

  @tailrec
  private def doRead(cbuf: Array[Char], off: Int, len: Int, written: Int): Int =
    if (len <= availableInCurrentChunk) {
      readFromCurrentChunk(cbuf, off, len)
      written + len
    } else {
      val av = availableInCurrentChunk
      readFromCurrentChunk(cbuf, off, av)
      loadNext()
      if (done) {
        written + av
      } else {
        doRead(cbuf, off + av, len - av, written + av)
      }
    }

  private def readFromCurrentChunk(cbuf: Array[Char], off: Int, len: Int): Unit = {
    var i: Int = 0
    while (i < len) {
      cbuf.update(off + i, readOne())
      i += 1
    }
  }

  override def close(): Unit = {
    chunks = Iterator.empty
    loadNext()
  }
}

private[zio] object ZReader {
  def fromPull[R](runtime: Runtime[R], pull: ZIO[R, Option[Throwable], Chunk[Char]]): ZReader = {
    def unfoldPull: Iterator[Chunk[Char]] =
      runtime.unsafeRunSync(pull) match {
        case Exit.Success(chunk) => Iterator.single(chunk) ++ unfoldPull
        case Exit.Failure(cause) =>
          cause.failureOrCause match {
            case Left(None)    => Iterator.empty
            case Left(Some(e)) => throw e
            case Right(c)      => throw FiberFailure(c)
          }
      }
    new ZReader(Iterator.empty ++ unfoldPull)
  }
}
