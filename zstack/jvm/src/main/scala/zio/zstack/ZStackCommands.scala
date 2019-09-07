package zio.zstack

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

sealed trait ZStackCommands

case object Test extends ZStackCommands
case object Stop extends ZStackCommands

object ZStackCommands {
  def ByteBufferToString(bytes: ByteBuffer): String = {
    return new String(bytes.array()).trim()
  }

  def StringToByteBuffer(message: String): ByteBuffer = {
    return ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8))
  }
}

case class ZStackConfig(host: String, port: Int, debug: Boolean)
object ZStackConfig {
  def empty = new ZStackConfig("localhost", 1111, false)
}
