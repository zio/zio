import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.SocketChannel

object ZStackClient {
  val client: SocketChannel = SocketChannel.open(new InetSocketAddress("localhost", 1111))
  val buffer: ByteBuffer = ByteBuffer.allocate(256)

  private def StringToByteBuffer(message: String): ByteBuffer = {
    return ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8))
  }
  private def ByteBufferToString(bytes: ByteBuffer): String = {
    return new String(bytes.array()).trim()
    // return Charset.forName("UTF-8").decode(bytes).toString()
  }
  def SendMessage(message: String): String = {
    client.write(StringToByteBuffer(message))
    client.read(buffer)
    val response: String = ByteBufferToString(buffer)
    buffer.clear()
    println(s"Response from server: $response")
    return response
  }
}
