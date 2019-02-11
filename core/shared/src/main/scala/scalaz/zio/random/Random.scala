package scalaz.zio.random

import scalaz.zio.{ Chunk, ZIO }

trait Random extends Serializable {
  val random: Random.Interface[Any]
}
object Random extends Serializable {
  trait Interface[R] extends Serializable {
    val nextBoolean: ZIO[R, Nothing, Boolean]
    def nextBytes(length: Int): ZIO[R, Nothing, Chunk[Byte]]
    val nextDouble: ZIO[R, Nothing, Double]
    val nextFloat: ZIO[R, Nothing, Float]
    val nextGaussian: ZIO[R, Nothing, Double]
    def nextInt(n: Int): ZIO[R, Nothing, Int]
    val nextInt: ZIO[R, Nothing, Int]
    val nextLong: ZIO[R, Nothing, Long]
    val nextPrintableChar: ZIO[R, Nothing, Char]
    def nextString(length: Int): ZIO[R, Nothing, String]
  }
  trait Live extends Random {
    object random extends Interface[Any] {
      import scala.util.{ Random => SRandom }

      val nextBoolean: ZIO[Any, Nothing, Boolean] = ZIO.sync(SRandom.nextBoolean())
      def nextBytes(length: Int): ZIO[Any, Nothing, Chunk[Byte]] =
        ZIO.sync {
          val array = Array.ofDim[Byte](length)

          SRandom.nextBytes(array)

          Chunk.fromArray(array)
        }
      val nextDouble: ZIO[Any, Nothing, Double]              = ZIO.sync(SRandom.nextDouble())
      val nextFloat: ZIO[Any, Nothing, Float]                = ZIO.sync(SRandom.nextFloat())
      val nextGaussian: ZIO[Any, Nothing, Double]            = ZIO.sync(SRandom.nextGaussian())
      def nextInt(n: Int): ZIO[Any, Nothing, Int]            = ZIO.sync(SRandom.nextInt(n))
      val nextInt: ZIO[Any, Nothing, Int]                    = ZIO.sync(SRandom.nextInt())
      val nextLong: ZIO[Any, Nothing, Long]                  = ZIO.sync(SRandom.nextLong())
      val nextPrintableChar: ZIO[Any, Nothing, Char]         = ZIO.sync(SRandom.nextPrintableChar())
      def nextString(length: Int): ZIO[Any, Nothing, String] = ZIO.sync(SRandom.nextString(length))
    }
  }
  object Live extends Live
}
