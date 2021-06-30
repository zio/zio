package zio.stream.experimental

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import zio.Chunk

object UTFDecode {

  implicit class ByteStreamDecode[R, E, A](val stream: ZStream[R, E, Byte]) extends AnyVal {

    def utfDecode: ZStream[R, E, String]     = new ZStream[R, E, String](stream.channel >>> UTFDecode.utfDecode)
    def utf8Decode: ZStream[R, E, String]    = new ZStream[R, E, String](stream.channel >>> UTFDecode.utf8Decode)
    def utf16Decode: ZStream[R, E, String]   = new ZStream[R, E, String](stream.channel >>> UTFDecode.utf16Decode)
    def utf16BEDecode: ZStream[R, E, String] = new ZStream[R, E, String](stream.channel >>> UTFDecode.utf16BEDecode)
    def utf16LEDecode: ZStream[R, E, String] = new ZStream[R, E, String](stream.channel >>> UTFDecode.utf16LEDecode)
    def utf32Decode: ZStream[R, E, String]   = new ZStream[R, E, String](stream.channel >>> UTFDecode.utf32Decode)
    def utf32BEDecode: ZStream[R, E, String] = new ZStream[R, E, String](stream.channel >>> UTFDecode.utf32BEDecode)
    def utf32LEDecode: ZStream[R, E, String] = new ZStream[R, E, String](stream.channel >>> UTFDecode.utf32LEDecode)
    def usASCIIDecode: ZStream[R, E, String] = new ZStream[R, E, String](stream.channel >>> UTFDecode.usASCIIDecode)

  }

  def utfDecode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    ZChannel.readWith(
      chunk =>
        if (chunk.startsWith(0 :: 0 :: -2 :: -1 :: Nil) && Charset.isSupported("UTF-32BE"))
          utf32BEDecode(chunk.drop(4))
        else if (chunk.startsWith(-2 :: -1 :: 0 :: 0 :: Nil) && Charset.isSupported("UTF-32LE"))
          utf32LEDecode(chunk.drop(4))
        else if (chunk.startsWith(-17 :: -69 :: -65 :: Nil))
          utf8Decode(chunk.drop(3))
        else if (chunk.startsWith(-2 :: -1 :: Nil))
          utf16BEDecode(chunk.drop(2))
        else if (chunk.startsWith(-1 :: -2 :: Nil))
          utf16LEDecode(chunk.drop(2))
        else utf8Decode(chunk),
      ZChannel.fail(_),
      ZChannel.end(_)
    )

  /**
   * Decodes chunks of UTF-8 bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  def utf8Decode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utf8Decode(Chunk.empty)

  def utf8Decode[Err, Done](initial: Chunk[Byte]): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] = {
    def is2ByteSequenceStart(b: Byte) = (b & 0xe0) == 0xc0
    def is3ByteSequenceStart(b: Byte) = (b & 0xf0) == 0xe0
    def is4ByteSequenceStart(b: Byte) = (b & 0xf8) == 0xf0
    def computeSplit(chunk: Chunk[Byte]) = {
      // There are 3 bad patterns we need to check to detect an incomplete chunk:
      // - 2/3/4 byte sequences that start on the last byte
      // - 3/4 byte sequences that start on the second-to-last byte
      // - 4 byte sequences that start on the third-to-last byte
      //
      // Otherwise, we can convert the entire concatenated chunk to a string.
      val len = chunk.length

      if (
        len >= 1 &&
        (is2ByteSequenceStart(chunk(len - 1)) ||
          is3ByteSequenceStart(chunk(len - 1)) ||
          is4ByteSequenceStart(chunk(len - 1)))
      )
        len - 1
      else if (
        len >= 2 &&
        (is3ByteSequenceStart(chunk(len - 2)) ||
          is4ByteSequenceStart(chunk(len - 2)))
      )
        len - 2
      else if (len >= 3 && is4ByteSequenceStart(chunk(len - 3)))
        len - 3
      else len
    }

    def writeChannel(
      bytes: Chunk[Byte]
    ) = {
      val (toConvert, newLeftovers) = bytes.splitAt(computeSplit(bytes))
      if (toConvert.isEmpty) (ZChannel.end(()), newLeftovers.materialize)
      else (ZChannel.write(Chunk.single(new String(toConvert.toArray[Byte], "UTF-8"))), newLeftovers.materialize)
    }

    def channel(leftovers: Chunk[Byte]): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
      ZChannel.readWith(
        { bytes =>
          val concat                = leftovers ++ bytes
          val (write, newLeftovers) = writeChannel(concat)
          write *> channel(newLeftovers)
        },
        err => ZChannel.fail(err),
        done =>
          if (leftovers.isEmpty) ZChannel.end(done)
          else
            ZChannel.write(Chunk.single(new String(leftovers.toArray[Byte], StandardCharsets.UTF_8))) *>
              ZChannel.end(done)
      )

    // handle optional byte order mark
    val chunk              = if (initial.startsWith(-17 :: -69 :: -65 :: Nil)) initial.drop(3) else initial
    val (write, leftovers) = writeChannel(chunk)
    write *> channel(leftovers)
  }

  /**
   * Decodes chunks of UTF-16 bytes into strings.
   * If no byte order mark is found big-endianness is assumed.
   *
   * This transducer uses the endisn-specific String constructor's behavior when handling
   * malformed byte sequences.
   */
  def utf16Decode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    ZChannel.readWith(
      chunk =>
        if (chunk.startsWith(-2 :: -1 :: Nil)) utf16BEDecode(chunk.drop(2))
        else if (chunk.startsWith(-1 :: -2 :: Nil)) utf16LEDecode(chunk.drop(2))
        else utf16BEDecode(chunk),
      ZChannel.fail(_),
      ZChannel.end(_)
    )

  /**
   * Decodes chunks of UTF-16BE bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  def utf16BEDecode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utf16BEDecode(Chunk.empty)

  private def utf16BEDecode[Err, Done](
    initial: Chunk[Byte]
  ): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utfFixedLengthDecode(StandardCharsets.UTF_16BE, 2, initial)

  /**
   * Decodes chunks of UTF-16LE bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  def utf16LEDecode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utf16LEDecode(Chunk.empty)

  private def utf16LEDecode[Err, Done](
    initial: Chunk[Byte]
  ): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utfFixedLengthDecode(StandardCharsets.UTF_16LE, 2, initial)

  /**
   * Decodes chunks of UTF-32 bytes into strings.
   * If no byte order mark is found big-endianness is assumed.
   */
  def utf32Decode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    ZChannel.readWith(
      chunk =>
        if (chunk.startsWith(0 :: 0 :: -2 :: -1 :: Nil) && Charset.isSupported("UTF-32BE"))
          utf32BEDecode(chunk.drop(4))
        else if (chunk.startsWith(-2 :: -1 :: 0 :: 0 :: Nil) && Charset.isSupported("UTF-32LE"))
          utf32LEDecode(chunk.drop(4))
        else utf32BEDecode(chunk),
      ZChannel.fail(_),
      ZChannel.end(_)
    )

  /**
   * Decodes chunks of UTF-32BE bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  def utf32BEDecode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utf32BEDecode(Chunk.empty)

  private def utf32BEDecode[Err, Done](
    initial: Chunk[Byte]
  ): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utfFixedLengthDecode(Charset.forName("UTF-32BE"), 4, initial)

  /**
   * Decodes chunks of UTF-32LE bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  def utf32LEDecode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utf32LEDecode(Chunk.empty)

  private def utf32LEDecode[Err, Done](
    initial: Chunk[Byte]
  ): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    utfFixedLengthDecode(Charset.forName("UTF-32LE"), 4, initial)

  private def utfFixedLengthDecode[Err, Done](
    charset: Charset,
    width: Int,
    initial: Chunk[Byte]
  ): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] = {

    def writeChannel(data: Chunk[Byte]) = {
      val remainder         = data.length % width
      val (fullChunk, rest) = data.splitAt(data.length - remainder)
      val decoded           = new String(fullChunk.toArray[Byte], charset)
      (rest, ZChannel.write(Chunk.single(decoded)))
    }

    def channel(old: Chunk[Byte]): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
      ZChannel.readWith(
        { bytes =>
          val data          = old ++ bytes
          val (rest, write) = writeChannel(data)
          write *> channel(rest)
        },
        err => ZChannel.fail(err),
        done => ZChannel.write(Chunk.single(new String(old.toArray[Byte], charset))) >>> ZChannel.end(done)
      )

    val (rest, write) = writeChannel(initial)
    write *> channel(rest)
  }

  /**
   * Decodes chunks of US-ASCII bytes into strings.
   *
   * This transducer uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  def usASCIIDecode[Err, Done]: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[String], Done] =
    ZChannel.readWith(
      chunk =>
        ZChannel.write(Chunk.single(new String(chunk.toArray[Byte], StandardCharsets.US_ASCII))) *> usASCIIDecode,
      ZChannel.fail(_),
      ZChannel.end(_)
    )

}
