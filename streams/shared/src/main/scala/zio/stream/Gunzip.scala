package zio.stream

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.compression.{CompressionException, Gunzipper}
import zio.{Chunk, ZIO, Trace}

private object Gunzip {
  def makeGunzipper[Done](
    bufferSize: Int = 64 * 1024
  )(implicit
    trace: Trace
  ): ZChannel[Any, CompressionException, Chunk[Byte], Done, CompressionException, Chunk[Byte], Done] =
    ZChannel.unwrapScoped {
      ZIO
        .acquireRelease(
          Gunzipper.make(bufferSize)
        )(gunzipper => ZIO.succeed(gunzipper.close()))
        .map {
          case gunzipper => {

            lazy val loop
              : ZChannel[Any, CompressionException, Chunk[Byte], Done, CompressionException, Chunk[Byte], Done] =
              ZChannel.readWithCause(
                chunk =>
                  ZChannel.fromZIO {
                    gunzipper.onChunk(chunk)
                  }.flatMap(chunk => ZChannel.write(chunk) *> loop),
                ZChannel.refailCause,
                done =>
                  ZChannel.fromZIO {
                    gunzipper.onNone
                  }.flatMap(chunk => ZChannel.write(chunk).as(done))
              )

            loop
          }
        }
    }
}
