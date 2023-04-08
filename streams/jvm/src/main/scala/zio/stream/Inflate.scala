package zio.stream

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.compression.CompressionException
import zio.{Chunk, ZIO, Trace}

import java.util.zip.{DataFormatException, Inflater}
import java.{util => ju}
import scala.annotation.tailrec

private object Inflate {
  def makeInflater[Err >: CompressionException, Done](
    bufferSize: Int = 64 * 1024,
    noWrap: Boolean = false
  )(implicit trace: Trace): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
    ZChannel.unwrapScoped {
      ZIO
        .acquireRelease(ZIO.succeed((new Array[Byte](bufferSize), new Inflater(noWrap)))) { case (_, inflater) =>
          ZIO.succeed(inflater.end())
        }
        .map { case (buffer, inflater) =>
          lazy val loop: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
            ZChannel.readWithCause(
              chunk =>
                ZChannel.fromZIO {
                  ZIO.attempt {
                    inflater.setInput(chunk.toArray)
                    pullAllOutput(inflater, buffer, chunk)
                  }.refineOrDie { case e: DataFormatException =>
                    CompressionException(e)
                  }
                }.flatMap(chunk => ZChannel.write(chunk) *> loop),
              ZChannel.refailCause,
              done =>
                ZChannel.fromZIO {
                  ZIO.attempt {
                    if (inflater.finished()) {
                      inflater.reset()
                      Chunk.empty
                    } else {
                      throw CompressionException("Inflater is not finished when input stream completed")
                    }
                  }.refineOrDie { case e: DataFormatException =>
                    CompressionException(e)
                  }
                }.flatMap(chunk => ZChannel.write(chunk).as(done))
            )

          loop
        }
    }

  // Pulls all available output from the inflater.
  private def pullAllOutput(
    inflater: Inflater,
    buffer: Array[Byte],
    input: Chunk[Byte]
  ): Chunk[Byte] = {
    @tailrec
    def next(acc: Chunk[Byte]): Chunk[Byte] = {
      val read      = inflater.inflate(buffer)
      val remaining = inflater.getRemaining()
      val current   = Chunk.fromArray(ju.Arrays.copyOf(buffer, read))
      if (remaining > 0) {
        if (read > 0) next(acc ++ current)
        else if (inflater.finished()) {
          val leftover = input.takeRight(remaining)
          inflater.reset()
          inflater.setInput(leftover.toArray)
          next(acc ++ current)
        } else {
          // Impossible happened (aka programmer error). Die.
          throw new Exception("read = 0, remaining > 0, not finished")
        }
      } else if (read > 0) next(acc ++ current)
      else acc ++ current
    }

    if (inflater.needsInput()) Chunk.empty else next(Chunk.empty)
  }

}
