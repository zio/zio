package zio.stream

import zio.{ Chunk, ZIO }

object Framing {

  val lfDelimiter: Chunk[Char] = Chunk('\n')

  val crLfDelimiter: Chunk[Char] = Chunk('\r', '\n')

  /**
   * Frames a stream of chunks according to a delimiter.
   *
   * This is designed for use with `ZStream#aggregate`.
   * Regardless of how the input stream is chunked, the output stream will emit
   * a single chunk for each occurrence of the delimiter. The delimiters are
   * not included in the output.
   *
   * Example:
   * {{{
   *   val stream = Stream(Chunk(1), Chunk(2, 3, 100), Chunk(101, 4))
   *   val sink = Framing.delimiter(Chunk(100, 101), 100)
   *   stream.aggregate(sink).runCollect
   *   // List(Chunk(1, 2, 3), Chunk(4))
   * }}}
   *
   * @param delimiter The delimiter to use for framing.
   * @param maxFrameLength The maximum frame length allowed. If more than this
   *                       many elements appears without the delimiter,
   *                       this sink fails with `IllegalArgumentException`.
   *                       None of the chunks emitted will be larger than this.
   */
  def delimiter[A](
    delimiter: Chunk[A],
    maxFrameLength: Int
  ): ZSink[Any, IllegalArgumentException, Chunk[A], Chunk[A], Chunk[A]] = {

    val delimiterLength = delimiter.length

    def findDelimiter[B](
      pos: Int
    )(chunk: Chunk[B]): Option[(Chunk[B], Chunk[B])] = {

      @scala.annotation.tailrec
      def go(pos: Int): Option[(Chunk[B], Chunk[B])] = {
        val compare = chunk.drop(pos).take(delimiterLength)
        if (compare.length < delimiterLength) {
          None
        } else if (compare == delimiter) {
          val (matched, remaining) = chunk.splitAt(pos)
          Some((matched, remaining.drop(delimiterLength)))
        } else {
          go(pos + 1)
        }
      }

      go(pos)
    }

    ZSink
      .foldM((true, Chunk.empty: Chunk[A]))(_._1) { (acc, in: Chunk[A]) =>
        val buffer       = acc._2
        val searchBuffer = buffer ++ in
        findDelimiter(math.max(0, buffer.length - delimiterLength + 1))(searchBuffer).map {
          case (found, remaining) =>
            if (found.length > maxFrameLength) {
              ZIO.failNow(new IllegalArgumentException(s"Delimiter not found within $maxFrameLength elements"))
            } else {
              ZIO.succeedNow(((false, found), Chunk.single(remaining)))
            }
        }.getOrElse {
          if (searchBuffer.length > maxFrameLength) {
            ZIO.failNow(new IllegalArgumentException(s"Delimiter not found within $maxFrameLength elements"))
          } else {
            ZIO.succeedNow(((true, searchBuffer), Chunk.empty))
          }
        }
      }
      .map(_._2)
  }

}
