/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.stream.compression

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * Groups a compression level, strategy and flush mode for a deflate or gzip
 * compressor. On the JVM and Scala Native, `ZPipeline.deflate` and
 * `ZPipeline.gzip` take these three settings as separate parameters.
 */
case class CompressionParameters(
  level: CompressionLevel,
  strategy: CompressionStrategy,
  flushMode: FlushMode
)

/**
 * A compression level of `java.util.zip.Deflater`, from `NoCompression` (0) to
 * `BestCompression` (9), plus `DefaultCompression` (-1). `jValue` is the value
 * passed to the `Deflater` constructor.
 */
sealed abstract class CompressionLevel(val jValue: Int)

/** The compression levels, one per `java.util.zip.Deflater` level. */
object CompressionLevel {

  /** Level -1, `Deflater.DEFAULT_COMPRESSION`: the zlib default level. */
  case object DefaultCompression extends CompressionLevel(-1)

  /**
   * Level 0, `Deflater.NO_COMPRESSION`: stores the data without compressing it.
   */
  case object NoCompression extends CompressionLevel(0)

  /** Level 1, `Deflater.BEST_SPEED`: the fastest compression. */
  case object BestSpeed extends CompressionLevel(1)

  /** Level 2 of `java.util.zip.Deflater`. */
  case object CompressionLevel2 extends CompressionLevel(2)

  /** Level 3 of `java.util.zip.Deflater`. */
  case object CompressionLevel3 extends CompressionLevel(3)

  /** Level 4 of `java.util.zip.Deflater`. */
  case object CompressionLevel4 extends CompressionLevel(4)

  /** Level 5 of `java.util.zip.Deflater`. */
  case object CompressionLevel5 extends CompressionLevel(5)

  /** Level 6 of `java.util.zip.Deflater`. */
  case object CompressionLevel6 extends CompressionLevel(6)

  /** Level 7 of `java.util.zip.Deflater`. */
  case object CompressionLevel7 extends CompressionLevel(7)

  /** Level 8 of `java.util.zip.Deflater`. */
  case object CompressionLevel8 extends CompressionLevel(8)

  /** Level 9, `Deflater.BEST_COMPRESSION`: the smallest output. */
  case object BestCompression extends CompressionLevel(9)
}

/**
 * A compression strategy of `java.util.zip.Deflater`. `jValue` is the value
 * passed to `Deflater.setStrategy`.
 */
sealed abstract class CompressionStrategy(val jValue: Int)

/** The compression strategies, one per `java.util.zip.Deflater` strategy. */
object CompressionStrategy {

  /** `Deflater.DEFAULT_STRATEGY` (0). */
  case object DefaultStrategy extends CompressionStrategy(0)

  /**
   * `Deflater.FILTERED` (1), for data made of small values with a random
   * distribution.
   */
  case object Filtered extends CompressionStrategy(1)

  /**
   * `Deflater.HUFFMAN_ONLY` (2): Huffman coding only, without string matching.
   */
  case object HuffmanOnly extends CompressionStrategy(2)
}

/**
 * A flush mode of `java.util.zip.Deflater`. `jValue` is the value passed to
 * each `Deflater.deflate` call.
 */
sealed abstract class FlushMode(val jValue: Int)

/** The flush modes, one per `java.util.zip.Deflater` flush mode. */
object FlushMode {

  /**
   * `Deflater.NO_FLUSH` (0): the deflater decides how much data to buffer
   * before writing output.
   */
  case object NoFlush extends FlushMode(0)

  /**
   * `Deflater.SYNC_FLUSH` (2): writes all pending output for the input so far,
   * so it can be decompressed up to that point.
   */
  case object SyncFlush extends FlushMode(2)

  /**
   * `Deflater.FULL_FLUSH` (3): like `SyncFlush`, and also resets the
   * compression state, so decompression can restart from this point.
   */
  case object FullFlush extends FlushMode(3)
}
