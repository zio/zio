package zio.stream

import java.io.{ IOException, OutputStream }

import zio._
import zio.blocking._

trait ZStreamPlatformSpecific {

  private final def exitToInputStreamRead[E <: Throwable](exit: Exit[Option[E], Byte]): Int = exit match {
    case Exit.Success(value) => value.toInt
    case Exit.Failure(cause) =>
      cause.failureOrCause match {
        case Left(value) =>
          value match {
            case Some(value) => throw value
            case None        => -1
          }
        case Right(value) => throw FiberFailure(value)
      }
  }

  implicit class ZStreamByteOps[-R, +E <: Throwable](val stream: ZStream[R, E, Byte]) {
    final def toInputStream: ZManaged[R, E, java.io.InputStream] =
      for {
        runtime <- ZIO.runtime[R].toManaged_
        pull    <- stream.process
        javaStream = new java.io.InputStream {
          override def read(): Int = {
            val exit = runtime.unsafeRunSync[Option[E], Byte](pull)
            exitToInputStreamRead(exit)
          }
        }
      } yield javaStream
  }

  implicit class ZStreamChunkByteOps[-R, +E <: Throwable](val stream: ZStreamChunk[R, E, Byte]) {
    final def toInputStream: ZManaged[R, E, java.io.InputStream] =
      for {
        runtime <- ZIO.runtime[R].toManaged_
        pull    <- stream.process
        javaStream = new java.io.InputStream {
          override def read(): Int = {
            val exit = runtime.unsafeRunSync[Option[E], Byte](pull)
            exitToInputStreamRead(exit)
          }
        }
      } yield javaStream
  }

  implicit class StreamEffectByteOps[-R, +E <: Throwable](val stream: StreamEffect[R, E, Byte]) {
    final def toInputStream: ZManaged[R, E, java.io.InputStream] =
      for {
        pull <- stream.processEffect
        javaStream = new java.io.InputStream {
          override def read(): Int =
            try {
              pull().toInt
            } catch {
              case StreamEffect.End        => -1
              case StreamEffect.Failure(e) => throw e.asInstanceOf[E]
            }
        }
      } yield javaStream
  }

  implicit class StreamEffectChunkByteOps[-R, +E <: Throwable](val stream: StreamEffectChunk[R, E, Byte]) {
    final def toInputStream: ZManaged[R, E, java.io.InputStream] =
      for {
        thunk <- stream.chunks.processEffect
        javaStream = {
          new java.io.InputStream {
            var counter            = 0
            var chunk: Chunk[Byte] = Chunk.empty
            override def read(): Int =
              try {
                while (counter >= chunk.length) {
                  chunk = thunk()
                  counter = 0
                }
                val item = chunk(counter).toInt
                counter += 1
                item
              } catch {
                case StreamEffect.End        => -1
                case StreamEffect.Failure(e) => throw e.asInstanceOf[E]
              }
          }
        }
      } yield javaStream
  }
}

trait ZSinkPlatformSpecific {

  /**
   * Uses the provided `OutputStream` to create a [[ZSink]] that consumes byte chunks
   * and writes them to the `OutputStream`. The sink will yield the count of bytes written.
   *
   * The caller of this function is responsible for closing the `OutputStream`.
   */
  def fromOutputStream(
    os: OutputStream
  ): ZSink[Blocking, IOException, Nothing, Chunk[Byte], Int] =
    ZSink.foldM(0)(_ => true) { (bytesWritten, byteChunk: Chunk[Byte]) =>
      effectBlocking {
        val bytes = byteChunk.toArray
        os.write(bytes)
        (bytesWritten + bytes.length, Chunk.empty)
      }.refineOrDie {
        case e: IOException => e
      }
    }
}
