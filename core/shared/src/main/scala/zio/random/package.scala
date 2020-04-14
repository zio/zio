package zio

package object random {
  type Random = Has[Random.Service]

  object Random extends Serializable {
    trait Service extends Serializable {
      def nextBoolean: UIO[Boolean]
      def nextBytes(length: Int): UIO[Chunk[Byte]]
      def nextDouble: UIO[Double]
      def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): UIO[Double]
      def nextFloat: UIO[Float]
      def nextFloatBetween(minInclusive: Float, maxExclusive: Float): UIO[Float]
      def nextGaussian: UIO[Double]
      def nextInt: UIO[Int]
      def nextIntBetween(minInclusive: Int, maxExclusive: Int): UIO[Int]
      def nextIntBounded(n: Int): UIO[Int]
      def nextLong: UIO[Long]
      def nextLongBetween(minInclusive: Long, maxExclusive: Long): UIO[Long]
      def nextLongBounded(n: Long): UIO[Long]
      def nextPrintableChar: UIO[Char]
      def nextString(length: Int): UIO[String]
      def shuffle[A](list: List[A]): UIO[List[A]]
    }

    import scala.util.{ Random => SRandom }

    object Service {
      val live: Service = make(new SRandom())

      def withSeed(seed: Long): Service =
        make(new SRandom(seed))

      def make(random: SRandom): Service = new Service {
        val nextBoolean: UIO[Boolean] =
          ZIO.effectTotal(random.nextBoolean())
        def nextBytes(length: Int): UIO[Chunk[Byte]] =
          ZIO.effectTotal {
            val array = Array.ofDim[Byte](length)
            random.nextBytes(array)
            Chunk.fromArray(array)
          }
        val nextDouble: UIO[Double] =
          ZIO.effectTotal(random.nextDouble())
        def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): UIO[Double] =
          nextDoubleBetweenWith(minInclusive, maxExclusive)(nextDouble)
        val nextFloat: UIO[Float] =
          ZIO.effectTotal(random.nextFloat())
        def nextFloatBetween(minInclusive: Float, maxExclusive: Float): UIO[Float] =
          nextFloatBetweenWith(minInclusive, maxExclusive)(nextFloat)
        val nextGaussian: UIO[Double] =
          ZIO.effectTotal(random.nextGaussian())
        val nextInt: UIO[Int] =
          ZIO.effectTotal(random.nextInt())
        def nextIntBetween(minInclusive: Int, maxExclusive: Int): UIO[Int] =
          nextIntBetweenWith(minInclusive, maxExclusive)(nextInt, nextIntBounded)
        def nextIntBounded(n: Int): UIO[Int] =
          ZIO.effectTotal(random.nextInt(n))
        val nextLong: UIO[Long] =
          ZIO.effectTotal(random.nextLong())
        def nextLongBetween(minInclusive: Long, maxExclusive: Long): UIO[Long] =
          nextLongBetweenWith(minInclusive, maxExclusive)(nextLong, nextLongBounded)
        def nextLongBounded(n: Long): UIO[Long] =
          Random.nextLongBoundedWith(n)(nextLong)
        val nextPrintableChar: UIO[Char] =
          ZIO.effectTotal(random.nextPrintableChar())
        def nextString(length: Int): UIO[String] =
          ZIO.effectTotal(random.nextString(length))
        def shuffle[A](list: List[A]): UIO[List[A]] =
          Random.shuffleWith(nextIntBounded(_), list)
      }
    }

    val any: ZLayer[Random, Nothing, Random] =
      ZLayer.requires[Random]

    val live: Layer[Nothing, Random] =
      ZLayer.succeed(Service.live)

    def withSeed(seed: Long): Layer[Nothing, Random] =
      ZLayer.succeed(Service.withSeed(seed))

    def make(random: SRandom): Layer[Nothing, Random] =
      ZLayer.succeed(Service.make(random))

    private[zio] def nextDoubleBetweenWith(minInclusive: Double, maxExclusive: Double)(
      nextDouble: UIO[Double]
    ): UIO[Double] =
      if (minInclusive >= maxExclusive)
        UIO.die(new IllegalArgumentException("invalid bounds"))
      else
        nextDouble.map { n =>
          val result = n * (maxExclusive - minInclusive) + minInclusive
          if (result < maxExclusive) result
          else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
        }

    private[zio] def nextFloatBetweenWith(minInclusive: Float, maxExclusive: Float)(
      nextFloat: UIO[Float]
    ): UIO[Float] =
      if (minInclusive >= maxExclusive)
        UIO.die(new IllegalArgumentException("invalid bounds"))
      else
        nextFloat.map { n =>
          val result = n * (maxExclusive - minInclusive) + minInclusive
          if (result < maxExclusive) result
          else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
        }

    private[zio] def nextIntBetweenWith(
      minInclusive: Int,
      maxExclusive: Int
    )(nextInt: UIO[Int], nextIntBounded: Int => UIO[Int]): UIO[Int] =
      if (minInclusive >= maxExclusive) {
        UIO.die(new IllegalArgumentException("invalid bounds"))
      } else {
        val difference = maxExclusive - minInclusive
        if (difference > 0) nextIntBounded(difference).map(_ + minInclusive)
        else nextInt.doUntil(n => minInclusive <= n && n < maxExclusive)
      }

    private[zio] def nextLongBetweenWith(
      minInclusive: Long,
      maxExclusive: Long
    )(nextLong: UIO[Long], nextLongBounded: Long => UIO[Long]): UIO[Long] =
      if (minInclusive >= maxExclusive)
        UIO.die(new IllegalArgumentException("invalid bounds"))
      else {
        val difference = maxExclusive - minInclusive
        if (difference > 0) nextLongBounded(difference).map(_ + minInclusive)
        else nextLong.doUntil(n => minInclusive <= n && n < maxExclusive)
      }

    private[zio] def nextLongBoundedWith(n: Long)(nextLong: UIO[Long]): UIO[Long] =
      if (n <= 0)
        UIO.die(new IllegalArgumentException("n must be positive"))
      else {
        nextLong.flatMap { r =>
          val m = n - 1
          if ((n & m) == 0L)
            UIO.succeedNow(r & m)
          else {
            def loop(u: Long): UIO[Long] =
              if (u + m - u % m < 0L) nextLong.flatMap(r => loop(r >>> 1))
              else UIO.succeedNow(u % n)
            loop(r >>> 1)
          }
        }
      }

    private[zio] def shuffleWith[A](nextIntBounded: Int => UIO[Int], list: List[A]): UIO[List[A]] =
      for {
        bufferRef <- Ref.make(new scala.collection.mutable.ArrayBuffer[A])
        _         <- bufferRef.update(_ ++= list)
        swap = (i1: Int, i2: Int) =>
          bufferRef.update {
            case buffer =>
              val tmp = buffer(i1)
              buffer(i1) = buffer(i2)
              buffer(i2) = tmp
              buffer
          }
        _      <- ZIO.foreach(list.length to 2 by -1)((n: Int) => nextIntBounded(n).flatMap(k => swap(n - 1, k)))
        buffer <- bufferRef.get
      } yield buffer.toList
  }

  /**
   * Generates a pseudo-random boolean.
   */
  val nextBoolean: ZIO[Random, Nothing, Boolean] =
    ZIO.accessM(_.get.nextBoolean)

  /**
   * Generates a pseudo-random chunk of bytes of the specified length.
   */
  def nextBytes(length: => Int): ZIO[Random, Nothing, Chunk[Byte]] =
    ZIO.accessM(_.get.nextBytes(length))

  /**
   * Generates a pseudo-random, uniformly distributed double between 0.0 and
   * 1.0.
   */
  val nextDouble: ZIO[Random, Nothing, Double] = ZIO.accessM(_.get.nextDouble)

  /**
   * Generates a pseudo-random double in the specified range.
   */
  def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): ZIO[Random, Nothing, Double] =
    ZIO.accessM(_.get.nextDoubleBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random, uniformly distributed float between 0.0 and
   * 1.0.
   */
  val nextFloat: ZIO[Random, Nothing, Float] =
    ZIO.accessM(_.get.nextFloat)

  /**
   * Generates a pseudo-random float in the specified range.
   */
  def nextFloatBetween(minInclusive: Float, maxExclusive: Float): ZIO[Random, Nothing, Float] =
    ZIO.accessM(_.get.nextFloatBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random double from a normal distribution with mean 0.0
   * and standard deviation 1.0.
   */
  val nextGaussian: ZIO[Random, Nothing, Double] =
    ZIO.accessM(_.get.nextGaussian)

  /**
   * Generates a pseudo-random integer.
   */
  val nextInt: ZIO[Random, Nothing, Int] =
    ZIO.accessM(_.get.nextInt)

  /**
   * Generates a pseudo-random integer in the specified range.
   */
  def nextIntBetween(minInclusive: Int, maxExclusive: Int): ZIO[Random, Nothing, Int] =
    ZIO.accessM(_.get.nextIntBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random integer between 0 (inclusive) and the specified
   * value (exclusive).
   */
  def nextIntBounded(n: => Int): ZIO[Random, Nothing, Int] =
    ZIO.accessM(_.get.nextIntBounded(n))

  /**
   * Generates a pseudo-random long.
   */
  val nextLong: ZIO[Random, Nothing, Long] =
    ZIO.accessM(_.get.nextLong)

  /**
   * Generates a pseudo-random long in the specified range.
   */
  def nextLongBetween(minInclusive: Long, maxExclusive: Long): ZIO[Random, Nothing, Long] =
    ZIO.accessM(_.get.nextLongBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random long between 0 (inclusive) and the specified
   * value (exclusive).
   */
  def nextLongBounded(n: => Long): ZIO[Random, Nothing, Long] =
    ZIO.accessM(_.get.nextLongBounded(n))

  /**
   * Generates a pseudo-random character from the ASCII range 33-126.
   */
  val nextPrintableChar: ZIO[Random, Nothing, Char] =
    ZIO.accessM(_.get.nextPrintableChar)

  /**
   * Generates a pseudo-random string of the specified length.
   */
  def nextString(length: => Int): ZIO[Random, Nothing, String] =
    ZIO.accessM(_.get.nextString(length))

  /**
   * Randomly shuffles the specified list.
   */
  def shuffle[A](list: => List[A]): ZIO[Random, Nothing, List[A]] =
    ZIO.accessM(_.get.shuffle(list))
}
