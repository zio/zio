package zio.stream.experimental

import zio.stream.compression.Gunzipper
import zio.{Chunk, ZIO, ZManaged}
import zio.stream.compression.CompressionException

object Gunzip {
  def makeGunzipper[Done](
    bufferSize: Int = 64 * 1024
  ): ZChannel[Any, CompressionException, Chunk[Byte], Done, CompressionException, Chunk[Byte], Done] =
    ZChannel.managed(
      ZManaged.make(
        Gunzipper.make(bufferSize)
      )(gunzipper => ZIO.effectTotal(gunzipper.close()))
    ) {
      case guzipper => {

        lazy val loop: ZChannel[Any, CompressionException, Chunk[Byte], Done, CompressionException, Chunk[Byte], Done] =
          ZChannel.readWithCause(
            chunk => {
              val r = ZChannel.fromEffect {
                guzipper.onChunk(chunk)
              }.flatMap(chunk => ZChannel.write(chunk) *> loop)
              r
            },
            ZChannel.halt(_),
            done =>
              ZChannel.fromEffect {
                guzipper.onNone
              }.flatMap(chunk => ZChannel.write(chunk).as(done))
          )

        loop
      }
    }

}
