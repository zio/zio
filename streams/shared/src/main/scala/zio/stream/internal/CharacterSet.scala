package zio.stream.internal

import zio.Chunk

import java.nio.charset.Charset

private[zio] object CharacterSet {

  val CharsetUtf32: Charset   = Charset.forName("UTF-32")
  val CharsetUtf32BE: Charset = Charset.forName("UTF-32BE")
  val CharsetUtf32LE: Charset = Charset.forName("UTF-32LE")

  object BOM {
    val Utf8: Chunk[Byte]    = Chunk(-17, -69, -65)
    val Utf16BE: Chunk[Byte] = Chunk(-2, -1)
    val Utf16LE: Chunk[Byte] = Chunk(-1, -2)
    val Utf32BE: Chunk[Byte] = Chunk(0, 0, -2, -1)
    val Utf32LE: Chunk[Byte] = Chunk(-1, -2, 0, 0)
  }
}
