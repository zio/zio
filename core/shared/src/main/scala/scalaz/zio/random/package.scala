package scalaz.zio

package object random extends Random.Interface[Random] {
  val nextBoolean: ZIO[Random, Nothing, Boolean]                = ZIO.readM(_.random.nextBoolean)
  def nextBytes(length: Int): ZIO[Random, Nothing, Chunk[Byte]] = ZIO.readM(_.random.nextBytes(length))
  val nextDouble: ZIO[Random, Nothing, Double]                  = ZIO.readM(_.random.nextDouble)
  val nextFloat: ZIO[Random, Nothing, Float]                    = ZIO.readM(_.random.nextFloat)
  val nextGaussian: ZIO[Random, Nothing, Double]                = ZIO.readM(_.random.nextGaussian)
  def nextInt(n: Int): ZIO[Random, Nothing, Int]                = ZIO.readM(_.random.nextInt(n))
  val nextInt: ZIO[Random, Nothing, Int]                        = ZIO.readM(_.random.nextInt)
  val nextLong: ZIO[Random, Nothing, Long]                      = ZIO.readM(_.random.nextLong)
  val nextPrintableChar: ZIO[Random, Nothing, Char]             = ZIO.readM(_.random.nextPrintableChar)
  def nextString(length: Int): ZIO[Random, Nothing, String]     = ZIO.readM(_.random.nextString(length))
}
