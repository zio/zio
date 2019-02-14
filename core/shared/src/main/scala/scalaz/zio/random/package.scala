package scalaz.zio

package object random extends Random.Interface[Random] {
  final val randomService: ZIO[Random, Nothing, Random.Interface[Any]] =
    ZIO.access(_.random)

  val nextBoolean: ZIO[Random, Nothing, Boolean]                = ZIO.accessM(_.random.nextBoolean)
  def nextBytes(length: Int): ZIO[Random, Nothing, Chunk[Byte]] = ZIO.accessM(_.random.nextBytes(length))
  val nextDouble: ZIO[Random, Nothing, Double]                  = ZIO.accessM(_.random.nextDouble)
  val nextFloat: ZIO[Random, Nothing, Float]                    = ZIO.accessM(_.random.nextFloat)
  val nextGaussian: ZIO[Random, Nothing, Double]                = ZIO.accessM(_.random.nextGaussian)
  def nextInt(n: Int): ZIO[Random, Nothing, Int]                = ZIO.accessM(_.random.nextInt(n))
  val nextInt: ZIO[Random, Nothing, Int]                        = ZIO.accessM(_.random.nextInt)
  val nextLong: ZIO[Random, Nothing, Long]                      = ZIO.accessM(_.random.nextLong)
  val nextPrintableChar: ZIO[Random, Nothing, Char]             = ZIO.accessM(_.random.nextPrintableChar)
  def nextString(length: Int): ZIO[Random, Nothing, String]     = ZIO.accessM(_.random.nextString(length))
}
