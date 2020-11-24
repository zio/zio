package zio.stm

import zio.random.Random
import zio.{ BuildFrom, BuildFromOps, Chunk, ZLayer, random }

object TRandom extends Serializable {

  trait Service {
    def nextBoolean: STM[Nothing, Boolean]
    def nextBytes(length: Int): STM[Nothing, Chunk[Byte]]
    def nextDouble: STM[Nothing, Double]
    def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): STM[Nothing, Double]
    def nextFloat: STM[Nothing, Float]
    def nextFloatBetween(minInclusive: Float, maxExclusive: Float): STM[Nothing, Float]
    def nextGaussian: STM[Nothing, Double]
    def nextInt: STM[Nothing, Int]
    def nextIntBetween(minInclusive: Int, maxExclusive: Int): STM[Nothing, Int]
    def nextIntBounded(n: Int): STM[Nothing, Int]
    def nextLong: STM[Nothing, Long]
    def nextLongBetween(minInclusive: Long, maxExclusive: Long): STM[Nothing, Long]
    def nextLongBounded(n: Long): STM[Nothing, Long]
    def nextPrintableChar: STM[Nothing, Char]
    def nextString(length: Int): STM[Nothing, String]
    def setSeed(seed: Long): STM[Nothing, Unit]
    def shuffle[A, Collection[+Element] <: Iterable[Element]](collection: Collection[A])(implicit
      bf: BuildFrom[Collection[A], A, Collection[A]]
    ): STM[Nothing, Collection[A]]
  }

  val any: ZLayer[TRandom, Nothing, TRandom] =
    ZLayer.requires[TRandom]

  val live: ZLayer[Random, Nothing, TRandom] =
    ZLayer.fromEffect {
      import PureRandom._
      random.nextLong.flatMap { init =>
        TRef
          .make(init)
          .map { seed =>
            def withSeed[A](f: Seed => (A, Seed)): STM[Nothing, A] =
              seed.modify(f)

            new Service {
              def nextBoolean: STM[Nothing, Boolean] =
                withSeed(rndBoolean)

              def nextBytes(length: Int): STM[Nothing, Chunk[Byte]] =
                withSeed(rndBytes(_, length))

              def nextDouble: STM[Nothing, Double] =
                withSeed(rndDouble)

              def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): STM[Nothing, Double] =
                nextDoubleBetweenWith(minInclusive, maxExclusive)(nextDouble)

              def nextFloat: STM[Nothing, Float] =
                withSeed(rndFloat)

              def nextFloatBetween(minInclusive: Float, maxExclusive: Float): STM[Nothing, Float] =
                nextFloatBetweenWith(minInclusive, maxExclusive)(nextFloat)

              def nextGaussian: STM[Nothing, Double] =
                withSeed(rndGaussian)

              def nextInt: STM[Nothing, Int] =
                withSeed(rndInt)

              def nextIntBetween(minInclusive: Int, maxExclusive: Int): STM[Nothing, Int] =
                nextIntBetweenWith(minInclusive, maxExclusive)(nextInt, nextIntBounded)

              def nextIntBounded(n: Int): STM[Nothing, Int] =
                withSeed(rndInt(_, n))

              def nextLong: STM[Nothing, Long] =
                withSeed(rndLong)

              def nextLongBetween(minInclusive: Long, maxExclusive: Long): STM[Nothing, Long] =
                nextLongBetweenWith(minInclusive, maxExclusive)(nextLong, nextLongBounded)

              def nextLongBounded(n: Long): STM[Nothing, Long] =
                nextLongBoundedWith(n)(nextLong)

              def nextPrintableChar: STM[Nothing, Char] =
                withSeed(rndPrintableChar)

              def nextString(length: Int): STM[Nothing, String] =
                withSeed(rndString(_, length))

              def setSeed(newSeed: Long): STM[Nothing, Unit] =
                seed.set(newSeed)

              def shuffle[A, Collection[+Element] <: Iterable[Element]](collection: Collection[A])(implicit
                bf: BuildFrom[Collection[A], A, Collection[A]]
              ): STM[Nothing, Collection[A]] =
                shuffleWith(nextIntBounded(_), collection)
            }
          }
          .commit
      }
    }

  private[zio] def nextDoubleBetweenWith(minInclusive: Double, maxExclusive: Double)(
    nextDouble: STM[Nothing, Double]
  ): STM[Nothing, Double] =
    if (minInclusive >= maxExclusive)
      STM.die(new IllegalArgumentException("invalid bounds"))
    else
      nextDouble.map { n =>
        val result = n * (maxExclusive - minInclusive) + minInclusive
        if (result < maxExclusive) result
        else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
      }

  private[zio] def nextFloatBetweenWith(minInclusive: Float, maxExclusive: Float)(
    nextFloat: STM[Nothing, Float]
  ): STM[Nothing, Float] =
    if (minInclusive >= maxExclusive)
      STM.die(new IllegalArgumentException("invalid bounds"))
    else
      nextFloat.map { n =>
        val result = n * (maxExclusive - minInclusive) + minInclusive
        if (result < maxExclusive) result
        else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
      }

  private[zio] def nextIntBetweenWith(
    minInclusive: Int,
    maxExclusive: Int
  )(nextInt: STM[Nothing, Int], nextIntBounded: Int => STM[Nothing, Int]): STM[Nothing, Int] =
    if (minInclusive >= maxExclusive) {
      STM.die(new IllegalArgumentException("invalid bounds"))
    } else {
      val difference = maxExclusive - minInclusive
      if (difference > 0) nextIntBounded(difference).map(_ + minInclusive)
      else nextInt.repeatUntil(n => minInclusive <= n && n < maxExclusive)
    }

  private[zio] def nextLongBetweenWith(
    minInclusive: Long,
    maxExclusive: Long
  )(nextLong: STM[Nothing, Long], nextLongBounded: Long => STM[Nothing, Long]): STM[Nothing, Long] =
    if (minInclusive >= maxExclusive)
      STM.die(new IllegalArgumentException("invalid bounds"))
    else {
      val difference = maxExclusive - minInclusive
      if (difference > 0) nextLongBounded(difference).map(_ + minInclusive)
      else nextLong.repeatUntil(n => minInclusive <= n && n < maxExclusive)
    }

  private[zio] def nextLongBoundedWith(n: Long)(nextLong: STM[Nothing, Long]): STM[Nothing, Long] =
    if (n <= 0)
      STM.die(new IllegalArgumentException("n must be positive"))
    else {
      nextLong.flatMap { r =>
        val m = n - 1
        if ((n & m) == 0L)
          STM.succeedNow(r & m)
        else {
          def loop(u: Long): STM[Nothing, Long] =
            if (u + m - u % m < 0L) nextLong.flatMap(r => loop(r >>> 1))
            else STM.succeedNow(u % n)
          loop(r >>> 1)
        }
      }
    }

  private[zio] def shuffleWith[A, Collection[+Element] <: Iterable[Element]](
    nextIntBounded: Int => STM[Nothing, Int],
    collection: Collection[A]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]]): STM[Nothing, Collection[A]] =
    for {
      bufferRef <- TRef.make(new scala.collection.mutable.ArrayBuffer[A])
      _         <- bufferRef.update(_ ++= collection)
      swap = (i1: Int, i2: Int) =>
               bufferRef.update { case buffer =>
                 val tmp = buffer(i1)
                 buffer(i1) = buffer(i2)
                 buffer(i2) = tmp
                 buffer
               }
      _ <-
        STM.foreach((collection.size to 2 by -1).toList)((n: Int) => nextIntBounded(n).flatMap(k => swap(n - 1, k)))
      buffer <- bufferRef.get
    } yield bf.fromSpecific(collection)(buffer)

  private[zio] object PureRandom {
    import scala.util.{ Random => SRandom }

    type Seed = Long

    def rndBoolean(seed: Seed): (Boolean, Seed) = {
      val rng      = new SRandom(seed)
      val boolean  = rng.nextBoolean()
      val nextSeed = rng.nextLong()
      (boolean, nextSeed)
    }

    def rndBytes(seed: Seed, length: Int): (Chunk[Byte], Seed) = {
      val rng   = new SRandom(seed)
      val array = Array.ofDim[Byte](length)
      SRandom.nextBytes(array)
      val nextSeed = rng.nextLong()
      (Chunk.fromArray(array), nextSeed)
    }

    def rndDouble(seed: Seed): (Double, Seed) = {
      val rng      = new SRandom(seed)
      val double   = rng.nextDouble()
      val nextSeed = rng.nextLong()
      (double, nextSeed)
    }

    def rndFloat(seed: Seed): (Float, Seed) = {
      val rng      = new SRandom(seed)
      val float    = rng.nextFloat()
      val nextSeed = rng.nextLong()
      (float, nextSeed)
    }

    def rndGaussian(seed: Seed): (Double, Seed) = {
      val rng      = new SRandom(seed)
      val gaussian = rng.nextGaussian()
      val nextSeed = rng.nextLong()
      (gaussian, nextSeed)
    }

    def rndInt(seed: Seed): (Int, Seed) = {
      val rng      = new SRandom(seed)
      val int      = rng.nextInt()
      val nextSeed = rng.nextLong()
      (int, nextSeed)
    }

    def rndInt(seed: Seed, n: Int): (Int, Seed) = {
      val rng      = new SRandom(seed)
      val int      = rng.nextInt(n)
      val nextSeed = rng.nextLong()
      (int, nextSeed)
    }

    def rndLong(seed: Seed): (Long, Seed) = {
      val rng      = new SRandom(seed)
      val long     = rng.nextLong()
      val nextSeed = rng.nextLong()
      (long, nextSeed)
    }

    def rndPrintableChar(seed: Seed): (Char, Seed) = {
      val rng      = new SRandom(seed)
      val char     = rng.nextPrintableChar()
      val nextSeed = rng.nextLong()
      (char, nextSeed)
    }

    def rndString(seed: Seed, length: Int): (String, Seed) = {
      val rng      = new SRandom(seed)
      val str      = rng.nextString(length)
      val nextSeed = rng.nextLong()
      (str, nextSeed)
    }
  }

  /**
   * Generates a pseudo-random boolean inside a transaction.
   */
  val nextBoolean: ZSTM[TRandom, Nothing, Boolean] =
    ZSTM.accessM(_.get.nextBoolean)

  /**
   * Generates a pseudo-random chunk of bytes of the specified length inside a transaction.
   */
  def nextBytes(length: => Int): ZSTM[TRandom, Nothing, Chunk[Byte]] =
    ZSTM.accessM(_.get.nextBytes(length))

  /**
   * Generates a pseudo-random, uniformly distributed double between 0.0 and
   * 1.0 inside a transaction.
   */
  val nextDouble: ZSTM[TRandom, Nothing, Double] = ZSTM.accessM(_.get.nextDouble)

  /**
   * Generates a pseudo-random double in the specified range inside a transaction.
   */
  def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): ZSTM[TRandom, Nothing, Double] =
    ZSTM.accessM(_.get.nextDoubleBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random, uniformly distributed float between 0.0 and
   * 1.0 inside a transaction.
   */
  val nextFloat: ZSTM[TRandom, Nothing, Float] =
    ZSTM.accessM(_.get.nextFloat)

  /**
   * Generates a pseudo-random float in the specified range inside a transaction.
   */
  def nextFloatBetween(minInclusive: Float, maxExclusive: Float): ZSTM[TRandom, Nothing, Float] =
    ZSTM.accessM(_.get.nextFloatBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random double from a normal distribution with mean 0.0
   * and standard deviation 1.0 inside a transaction.
   */
  val nextGaussian: ZSTM[TRandom, Nothing, Double] =
    ZSTM.accessM(_.get.nextGaussian)

  /**
   * Generates a pseudo-random integer inside a transaction.
   */
  val nextInt: ZSTM[TRandom, Nothing, Int] =
    ZSTM.accessM(_.get.nextInt)

  /**
   * Generates a pseudo-random integer in the specified range inside a transaction.
   */
  def nextIntBetween(minInclusive: Int, maxExclusive: Int): ZSTM[TRandom, Nothing, Int] =
    ZSTM.accessM(_.get.nextIntBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random integer between 0 (inclusive) and the specified
   * value (exclusive) inside a transaction.
   */
  def nextIntBounded(n: => Int): ZSTM[TRandom, Nothing, Int] =
    ZSTM.accessM(_.get.nextIntBounded(n))

  /**
   * Generates a pseudo-random long inside a transaction.
   */
  val nextLong: ZSTM[TRandom, Nothing, Long] =
    ZSTM.accessM(_.get.nextLong)

  /**
   * Generates a pseudo-random long in the specified range inside a transaction.
   */
  def nextLongBetween(minInclusive: Long, maxExclusive: Long): ZSTM[TRandom, Nothing, Long] =
    ZSTM.accessM(_.get.nextLongBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random long between 0 (inclusive) and the specified
   * value (exclusive) inside a transaction.
   */
  def nextLongBounded(n: => Long): ZSTM[TRandom, Nothing, Long] =
    ZSTM.accessM(_.get.nextLongBounded(n))

  /**
   * Generates a pseudo-random character from the ASCII range 33-126 inside a transaction.
   */
  val nextPrintableChar: ZSTM[TRandom, Nothing, Char] =
    ZSTM.accessM(_.get.nextPrintableChar)

  /**
   * Generates a pseudo-random string of the specified length inside a transaction.
   */
  def nextString(length: => Int): ZSTM[TRandom, Nothing, String] =
    ZSTM.accessM(_.get.nextString(length))

  /**
   * Sets the seed of this random number generator inside a transaction.
   */
  def setSeed(seed: Long): ZSTM[TRandom, Nothing, Unit] =
    ZSTM.accessM(_.get.setSeed(seed))

  /**
   * Randomly shuffles the specified list.
   */
  def shuffle[A](list: => List[A]): ZSTM[TRandom, Nothing, List[A]] =
    ZSTM.accessM(_.get.shuffle(list))
}
