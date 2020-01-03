package zio

package object random {
  type Random = Has[Random.Service]

  val nextBoolean: ZIO[Random, Nothing, Boolean]                = ZIO.accessM(_.get.nextBoolean)
  def nextBytes(length: Int): ZIO[Random, Nothing, Chunk[Byte]] = ZIO.accessM(_.get.nextBytes(length))
  val nextDouble: ZIO[Random, Nothing, Double]                  = ZIO.accessM(_.get.nextDouble)
  val nextFloat: ZIO[Random, Nothing, Float]                    = ZIO.accessM(_.get.nextFloat)
  val nextGaussian: ZIO[Random, Nothing, Double]                = ZIO.accessM(_.get.nextGaussian)
  def nextInt(n: Int): ZIO[Random, Nothing, Int]                = ZIO.accessM(_.get.nextInt(n))
  val nextInt: ZIO[Random, Nothing, Int]                        = ZIO.accessM(_.get.nextInt)
  val nextLong: ZIO[Random, Nothing, Long]                      = ZIO.accessM(_.get.nextLong)
  def nextLong(n: Long): ZIO[Random, Nothing, Long]             = ZIO.accessM(_.get.nextLong(n))
  val nextPrintableChar: ZIO[Random, Nothing, Char]             = ZIO.accessM(_.get.nextPrintableChar)
  def nextString(length: Int): ZIO[Random, Nothing, String]     = ZIO.accessM(_.get.nextString(length))
  def shuffle[A](list: List[A]): ZIO[Random, Nothing, List[A]]  = ZIO.accessM(_.get.shuffle(list))

}
