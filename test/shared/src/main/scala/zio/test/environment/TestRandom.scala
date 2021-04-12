package zio.test.environment

import zio.{PlatformSpecific => _, _}

import scala.collection.immutable.Queue
import scala.math.{log, sqrt}

/**
 * `TestRandom` allows for deterministically testing effects involving
 * randomness.
 *
 * `TestRandom` operates in two modes. In the first mode, `TestRandom` is a
 * purely functional pseudo-random number generator. It will generate
 * pseudo-random values just like `scala.util.Random` except that no internal
 * state is mutated. Instead, methods like `nextInt` describe state transitions
 * from one random state to another that are automatically composed together
 * through methods like `flatMap`. The random seed can be set using `setSeed`
 * and `TestRandom` is guaranteed to return the same sequence of values for any
 * given seed. This is useful for deterministically generating a sequence of
 * pseudo-random values and powers the property based testing functionality in
 * ZIO Test.
 *
 * In the second mode, `TestRandom` maintains an internal buffer of values that
 * can be "fed" with methods such as `feedInts` and then when random values of
 * that type are generated they will first be taken from the buffer. This is
 * useful for verifying that functions produce the expected output for a given
 * sequence of "random" inputs.
 *
 * {{{
 * import zio.Random._
 * import zio.test.environment.TestRandom
 *
 * for {
 *   _ <- TestRandom.feedInts(4, 5, 2)
 *   x <- Random.nextIntBounded(6)
 *   y <- Random.nextIntBounded(6)
 *   z <- Random.nextIntBounded(6)
 * } yield x + y + z == 11
 * }}}
 *
 * `TestRandom` will automatically take values from the buffer if a value of
 * the appropriate type is available and otherwise generate a pseudo-random
 * value, so there is nothing you need to do to switch between the two modes.
 * Just generate random values as you normally would to get pseudo-random
 * values, or feed in values of your own to get those values back. You can also
 * use methods like `clearInts` to clear the buffer of values of a given type
 * so you can fill the buffer with new values or go back to pseudo-random
 * number generation.
 */
trait TestRandom extends Restorable {
  def clearBooleans: UIO[Unit]
  def clearBytes: UIO[Unit]
  def clearChars: UIO[Unit]
  def clearDoubles: UIO[Unit]
  def clearFloats: UIO[Unit]
  def clearInts: UIO[Unit]
  def clearLongs: UIO[Unit]
  def clearStrings: UIO[Unit]
  def feedBooleans(booleans: Boolean*): UIO[Unit]
  def feedBytes(bytes: Chunk[Byte]*): UIO[Unit]
  def feedChars(chars: Char*): UIO[Unit]
  def feedDoubles(doubles: Double*): UIO[Unit]
  def feedFloats(floats: Float*): UIO[Unit]
  def feedInts(ints: Int*): UIO[Unit]
  def feedLongs(longs: Long*): UIO[Unit]
  def feedStrings(strings: String*): UIO[Unit]
  def getSeed: UIO[Long]
  def setSeed(seed: Long): UIO[Unit]
}

object TestRandom extends Serializable {

  /**
   * Adapted from @gzmo work in Scala.js (https://github.com/scala-js/scala-js/pull/780)
   */
  final case class Test(randomState: Ref[Data], bufferState: Ref[Buffer]) extends Random with TestRandom {

    /**
     * Clears the buffer of booleans.
     */
    val clearBooleans: UIO[Unit] =
      bufferState.update(_.copy(booleans = List.empty))

    /**
     * Clears the buffer of bytes.
     */
    val clearBytes: UIO[Unit] =
      bufferState.update(_.copy(bytes = List.empty))

    /**
     * Clears the buffer of characters.
     */
    val clearChars: UIO[Unit] =
      bufferState.update(_.copy(chars = List.empty))

    /**
     * Clears the buffer of doubles.
     */
    val clearDoubles: UIO[Unit] =
      bufferState.update(_.copy(doubles = List.empty))

    /**
     * Clears the buffer of floats.
     */
    val clearFloats: UIO[Unit] =
      bufferState.update(_.copy(floats = List.empty))

    /**
     * Clears the buffer of integers.
     */
    val clearInts: UIO[Unit] =
      bufferState.update(_.copy(integers = List.empty))

    /**
     * Clears the buffer of longs.
     */
    val clearLongs: UIO[Unit] =
      bufferState.update(_.copy(longs = List.empty))

    /**
     * Clears the buffer of strings.
     */
    val clearStrings: UIO[Unit] =
      bufferState.update(_.copy(strings = List.empty))

    /**
     * Feeds the buffer with specified sequence of booleans. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedBooleans(booleans: Boolean*): UIO[Unit] =
      bufferState.update(data => data.copy(booleans = booleans.toList ::: data.booleans))

    /**
     * Feeds the buffer with specified sequence of chunks of bytes. The first
     * value in the sequence will be the first to be taken. These values will
     * be taken before any values that were previously in the buffer.
     */
    def feedBytes(bytes: Chunk[Byte]*): UIO[Unit] =
      bufferState.update(data => data.copy(bytes = bytes.toList ::: data.bytes))

    /**
     * Feeds the buffer with specified sequence of characters. The first value
     * in the sequence will be the first to be taken. These values will be
     * taken before any values that were previously in the buffer.
     */
    def feedChars(chars: Char*): UIO[Unit] =
      bufferState.update(data => data.copy(chars = chars.toList ::: data.chars))

    /**
     * Feeds the buffer with specified sequence of doubles. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedDoubles(doubles: Double*): UIO[Unit] =
      bufferState.update(data => data.copy(doubles = doubles.toList ::: data.doubles))

    /**
     * Feeds the buffer with specified sequence of floats. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedFloats(floats: Float*): UIO[Unit] =
      bufferState.update(data => data.copy(floats = floats.toList ::: data.floats))

    /**
     * Feeds the buffer with specified sequence of integers. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedInts(ints: Int*): UIO[Unit] =
      bufferState.update(data => data.copy(integers = ints.toList ::: data.integers))

    /**
     * Feeds the buffer with specified sequence of longs. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedLongs(longs: Long*): UIO[Unit] =
      bufferState.update(data => data.copy(longs = longs.toList ::: data.longs))

    /**
     * Feeds the buffer with specified sequence of strings. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedStrings(strings: String*): UIO[Unit] =
      bufferState.update(data => data.copy(strings = strings.toList ::: data.strings))

    /**
     * Gets the seed of this `TestRandom`.
     */
    val getSeed: UIO[Long] =
      randomState.get.map { case Data(seed1, seed2, _) =>
        ((seed1.toLong << 24) | seed2) ^ 0x5deece66dL
      }

    /**
     * Takes a boolean from the buffer if one exists or else generates a
     * pseudo-random boolean.
     */
    lazy val nextBoolean: UIO[Boolean] =
      getOrElse(bufferedBoolean)(randomBoolean)

    /**
     * Takes a chunk of bytes from the buffer if one exists or else generates a
     * pseudo-random chunk of bytes of the specified length.
     */
    def nextBytes(length: Int): UIO[Chunk[Byte]] =
      getOrElse(bufferedBytes)(randomBytes(length))

    /**
     * Takes a double from the buffer if one exists or else generates a
     * pseudo-random, uniformly distributed double between 0.0 and 1.0.
     */
    lazy val nextDouble: UIO[Double] =
      getOrElse(bufferedDouble)(randomDouble)

    /**
     * Takes a double from the buffer if one exists or else generates a
     * pseudo-random double in the specified range.
     */
    def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): UIO[Double] =
      getOrElse(bufferedDouble)(randomDoubleBetween(minInclusive, maxExclusive))

    /**
     * Takes a float from the buffer if one exists or else generates a
     * pseudo-random, uniformly distributed float between 0.0 and 1.0.
     */
    lazy val nextFloat: UIO[Float] =
      getOrElse(bufferedFloat)(randomFloat)

    /**
     * Takes a float from the buffer if one exists or else generates a
     * pseudo-random float in the specified range.
     */
    def nextFloatBetween(minInclusive: Float, maxExclusive: Float): UIO[Float] =
      getOrElse(bufferedFloat)(randomFloatBetween(minInclusive, maxExclusive))

    /**
     * Takes a double from the buffer if one exists or else generates a
     * pseudo-random double from a normal distribution with mean 0.0 and
     * standard deviation 1.0.
     */
    lazy val nextGaussian: UIO[Double] =
      getOrElse(bufferedDouble)(randomGaussian)

    /**
     * Takes an integer from the buffer if one exists or else generates a
     * pseudo-random integer.
     */
    lazy val nextInt: UIO[Int] =
      getOrElse(bufferedInt)(randomInt)

    /**
     * Takes an integer from the buffer if one exists or else generates a
     * pseudo-random integer in the specified range.
     */
    def nextIntBetween(minInclusive: Int, maxExclusive: Int): UIO[Int] =
      getOrElse(bufferedInt)(randomIntBetween(minInclusive, maxExclusive))

    /**
     * Takes an integer from the buffer if one exists or else generates a
     * pseudo-random integer between 0 (inclusive) and the specified value
     * (exclusive).
     */
    def nextIntBounded(n: Int): UIO[Int] =
      getOrElse(bufferedInt)(randomIntBounded(n))

    /**
     * Takes a long from the buffer if one exists or else generates a
     * pseudo-random long.
     */
    lazy val nextLong: UIO[Long] =
      getOrElse(bufferedLong)(randomLong)

    /**
     * Takes a long from the buffer if one exists or else generates a
     * pseudo-random long in the specified range.
     */
    def nextLongBetween(minInclusive: Long, maxExclusive: Long): UIO[Long] =
      getOrElse(bufferedLong)(randomLongBetween(minInclusive, maxExclusive))

    /**
     * Takes a long from the buffer if one exists or else generates a
     * pseudo-random long between 0 (inclusive) and the specified value
     * (exclusive).
     */
    def nextLongBounded(n: Long): UIO[Long] =
      getOrElse(bufferedLong)(randomLongBounded(n))

    /**
     * Takes a character from the buffer if one exists or else generates a
     * pseudo-random character from the ASCII range 33-126.
     */
    lazy val nextPrintableChar: UIO[Char] =
      getOrElse(bufferedChar)(randomPrintableChar)

    /**
     * Takes a string from the buffer if one exists or else generates a
     * pseudo-random string of the specified length.
     */
    def nextString(length: Int): UIO[String] =
      getOrElse(bufferedString)(randomString(length))

    /**
     * Saves the `TestRandom`'s current state in an effect which, when run,
     * will restore the `TestRandom` state to the saved state.
     */
    val save: UIO[UIO[Unit]] =
      for {
        randomData <- randomState.get
        bufferData <- bufferState.get
      } yield randomState.set(randomData) *> bufferState.set(bufferData)

    /**
     * Sets the seed of this `TestRandom` to the specified value.
     */
    def setSeed(seed: Long): UIO[Unit] =
      randomState.set {
        val newSeed = (seed ^ 0x5deece66dL) & ((1L << 48) - 1)
        val seed1   = (newSeed >>> 24).toInt
        val seed2   = newSeed.toInt & ((1 << 24) - 1)
        Data(seed1, seed2, Queue.empty)
      }

    /**
     * Randomly shuffles the specified list.
     */
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      list: Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]]): UIO[Collection[A]] =
      Random.shuffleWith(randomIntBounded, list)

    private def bufferedBoolean(buffer: Buffer): (Option[Boolean], Buffer) =
      (
        buffer.booleans.headOption,
        buffer.copy(booleans = buffer.booleans.drop(1))
      )

    private def bufferedBytes(buffer: Buffer): (Option[Chunk[Byte]], Buffer) =
      (
        buffer.bytes.headOption,
        buffer.copy(bytes = buffer.bytes.drop(1))
      )

    private def bufferedChar(buffer: Buffer): (Option[Char], Buffer) =
      (
        buffer.chars.headOption,
        buffer.copy(chars = buffer.chars.drop(1))
      )

    private def bufferedDouble(buffer: Buffer): (Option[Double], Buffer) =
      (
        buffer.doubles.headOption,
        buffer.copy(doubles = buffer.doubles.drop(1))
      )

    private def bufferedFloat(buffer: Buffer): (Option[Float], Buffer) =
      (
        buffer.floats.headOption,
        buffer.copy(floats = buffer.floats.drop(1))
      )

    private def bufferedInt(buffer: Buffer): (Option[Int], Buffer) =
      (
        buffer.integers.headOption,
        buffer.copy(integers = buffer.integers.drop(1))
      )

    private def bufferedLong(buffer: Buffer): (Option[Long], Buffer) =
      (
        buffer.longs.headOption,
        buffer.copy(longs = buffer.longs.drop(1))
      )

    private def bufferedString(buffer: Buffer): (Option[String], Buffer) =
      (
        buffer.strings.headOption,
        buffer.copy(strings = buffer.strings.drop(1))
      )

    private def getOrElse[A](buffer: Buffer => (Option[A], Buffer))(random: UIO[A]): UIO[A] =
      bufferState.modify(buffer).flatMap(_.fold(random)(UIO.succeedNow))

    @inline
    private def leastSignificantBits(x: Double): Int =
      toInt(x) & ((1 << 24) - 1)

    @inline
    private def mostSignificantBits(x: Double): Int =
      toInt(x / (1 << 24).toDouble)

    private def randomBits(bits: Int): UIO[Int] =
      randomState.modify { data =>
        val multiplier  = 0x5deece66dL
        val multiplier1 = (multiplier >>> 24).toInt
        val multiplier2 = multiplier.toInt & ((1 << 24) - 1)
        val product1    = data.seed2.toDouble * multiplier1.toDouble + data.seed1.toDouble * multiplier2.toDouble
        val product2    = data.seed2.toDouble * multiplier2.toDouble + 0xb
        val newSeed1    = (mostSignificantBits(product2) + leastSignificantBits(product1)) & ((1 << 24) - 1)
        val newSeed2    = leastSignificantBits(product2)
        val result      = (newSeed1 << 8) | (newSeed2 >> 16)
        (result >>> (32 - bits), Data(newSeed1, newSeed2, data.nextNextGaussians))
      }

    private val randomBoolean: UIO[Boolean] =
      randomBits(1).map(_ != 0)

    private def randomBytes(length: Int): UIO[Chunk[Byte]] = {
      //  Our RNG generates 32 bit integers so to maximize efficiency we want to
      //  pull 8 bit bytes from the current integer until it is exhausted
      //  before generating another random integer
      def loop(i: Int, rnd: UIO[Int], n: Int, acc: UIO[List[Byte]]): UIO[List[Byte]] =
        if (i == length)
          acc.map(_.reverse)
        else if (n > 0)
          rnd.flatMap(rnd => loop(i + 1, UIO.succeedNow(rnd >> 8), n - 1, acc.map(rnd.toByte :: _)))
        else
          loop(i, nextInt, (length - i) min 4, acc)

      loop(0, randomInt, length min 4, UIO.succeedNow(List.empty[Byte])).map(Chunk.fromIterable)
    }

    private val randomDouble: UIO[Double] =
      for {
        i1 <- randomBits(26)
        i2 <- randomBits(27)
      } yield ((i1.toDouble * (1L << 27).toDouble) + i2.toDouble) / (1L << 53).toDouble

    private def randomDoubleBetween(minInclusive: Double, maxExclusive: Double): UIO[Double] =
      Random.nextDoubleBetweenWith(minInclusive, maxExclusive)(randomDouble)

    private val randomFloat: UIO[Float] =
      randomBits(24).map(i => (i.toDouble / (1 << 24).toDouble).toFloat)

    private def randomFloatBetween(minInclusive: Float, maxExclusive: Float): UIO[Float] =
      Random.nextFloatBetweenWith(minInclusive, maxExclusive)(randomFloat)

    private val randomGaussian: UIO[Double] =
      //  The Box-Muller transform generates two normally distributed random
      //  doubles, so we store the second double in a queue and check the
      //  queue before computing a new pair of values to avoid wasted work.
      randomState.modify { case Data(seed1, seed2, queue) =>
        queue.dequeueOption.fold((Option.empty[Double], Data(seed1, seed2, queue))) { case (d, queue) =>
          (Some(d), Data(seed1, seed2, queue))
        }
      }.flatMap {
        case Some(nextNextGaussian) => UIO.succeedNow(nextNextGaussian)
        case None =>
          def loop: UIO[(Double, Double, Double)] =
            randomDouble.zip(randomDouble).flatMap { case (d1, d2) =>
              val x      = 2 * d1 - 1
              val y      = 2 * d2 - 1
              val radius = x * x + y * y
              if (radius >= 1 || radius == 0) loop else UIO.succeedNow((x, y, radius))
            }
          loop.flatMap { case (x, y, radius) =>
            val c = sqrt(-2 * log(radius) / radius)
            randomState.modify { case Data(seed1, seed2, queue) =>
              (x * c, Data(seed1, seed2, queue.enqueue(y * c)))
            }
          }
      }

    private val randomInt: UIO[Int] =
      randomBits(32)

    private def randomIntBounded(n: Int): UIO[Int] =
      if (n <= 0)
        UIO.die(new IllegalArgumentException("n must be positive"))
      else if ((n & -n) == n)
        randomBits(31).map(_ >> Integer.numberOfLeadingZeros(n))
      else {
        def loop: UIO[Int] =
          randomBits(31).flatMap { i =>
            val value = i % n
            if (i - value + (n - 1) < 0) loop
            else UIO.succeedNow(value)
          }
        loop
      }

    private def randomIntBetween(minInclusive: Int, maxExclusive: Int): UIO[Int] =
      Random.nextIntBetweenWith(minInclusive, maxExclusive)(randomInt, randomIntBounded)

    private val randomLong: UIO[Long] =
      for {
        i1 <- randomBits(32)
        i2 <- randomBits(32)
      } yield (i1.toLong << 32) + i2

    private def randomLongBounded(n: Long): UIO[Long] =
      Random.nextLongBoundedWith(n)(randomLong)

    private def randomLongBetween(minInclusive: Long, maxExclusive: Long): UIO[Long] =
      Random.nextLongBetweenWith(minInclusive, maxExclusive)(randomLong, randomLongBounded)

    private val randomPrintableChar: UIO[Char] =
      randomIntBounded(127 - 33).map(i => (i + 33).toChar)

    private def randomString(length: Int): UIO[String] = {
      val safeChar = randomIntBounded(0xd800 - 1).map(i => (i + 1).toChar)
      UIO.collectAll(List.fill(length)(safeChar)).map(_.mkString)
    }

    @inline
    private def toInt(x: Double): Int =
      (x.asInstanceOf[Long] | 0.asInstanceOf[Long]).asInstanceOf[Int]
  }

  /**
   * An arbitrary initial seed for the `TestRandom`.
   */
  val DefaultData: Data = Data(1071905196, 1911589680)

  /**
   * The seed of the `TestRandom`.
   */
  final case class Data(
    seed1: Int,
    seed2: Int,
    private[TestRandom] val nextNextGaussians: Queue[Double] = Queue.empty
  )

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of booleans.
   */
  val clearBooleans: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearBooleans)

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of bytes.
   */
  val clearBytes: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearBytes)

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of characters.
   */
  val clearChars: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearChars)

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of doubles.
   */
  val clearDoubles: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearDoubles)

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of floats.
   */
  val clearFloats: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearFloats)

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of integers.
   */
  val clearInts: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearInts)

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of longs.
   */
  val clearLongs: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearLongs)

  /**
   * Accesses a `TestRandom` instance in the environment and clears the buffer
   * of strings.
   */
  val clearStrings: URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.clearStrings)

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of booleans.
   */
  def feedBooleans(booleans: Boolean*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedBooleans(booleans: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of chunks of bytes.
   */
  def feedBytes(bytes: Chunk[Byte]*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedBytes(bytes: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of characters.
   */
  def feedChars(chars: Char*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedChars(chars: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of doubles.
   */
  def feedDoubles(doubles: Double*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedDoubles(doubles: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of floats.
   */
  def feedFloats(floats: Float*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedFloats(floats: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of integers.
   */
  def feedInts(ints: Int*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedInts(ints: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of longs.
   */
  def feedLongs(longs: Long*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedLongs(longs: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and feeds the buffer
   * with the specified sequence of strings.
   */
  def feedStrings(strings: String*): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.feedStrings(strings: _*))

  /**
   * Accesses a `TestRandom` instance in the environment and gets the seed.
   */
  val getSeed: URIO[Has[TestRandom], Long] =
    ZIO.accessM(_.get.getSeed)

  /**
   * Constructs a new `TestRandom` with the specified initial state. This can
   * be useful for providing the required environment to an effect that
   * requires a `Random`, such as with `ZIO#provide`.
   */
  def make(data: Data): Layer[Nothing, Has[Random] with Has[TestRandom]] =
    ZLayer.many(for {
      data   <- Ref.make(data)
      buffer <- Ref.make(Buffer())
      test    = Test(data, buffer)
    } yield Has.allOf[Random, TestRandom](test, test))

  val any: ZLayer[Has[Random] with Has[TestRandom], Nothing, Has[Random] with Has[TestRandom]] =
    ZLayer.requires[Has[Random] with Has[TestRandom]]

  val deterministic: Layer[Nothing, Has[Random] with Has[TestRandom]] =
    make(DefaultData)

  val random: ZLayer[Has[Clock], Nothing, Has[Random] with Has[TestRandom]] =
    (ZLayer.service[Clock] ++ deterministic) >>>
      ZLayer.many {
        for {
          random     <- ZIO.service[Random]
          testRandom <- ZIO.service[TestRandom]
          time       <- Clock.nanoTime
          _          <- TestRandom.setSeed(time)
        } yield Has.allOf[Random, TestRandom](random, testRandom)
      }

  /**
   * Constructs a new `Test` object that implements the `TestRandom` interface.
   * This can be useful for mixing in with implementations of other interfaces.
   */
  def makeTest(data: Data): UIO[Test] =
    for {
      data   <- Ref.make(data)
      buffer <- Ref.make(Buffer())
    } yield Test(data, buffer)

  /**
   * Accesses a `TestRandom` instance in the environment and saves the random
   * state in an effect which, when run, will restore the `TestRandom` to the
   * saved state.
   */
  val save: ZIO[Has[TestRandom], Nothing, UIO[Unit]] =
    ZIO.accessM(_.get.save)

  /**
   * Accesses a `TestRandom` instance in the environment and sets the seed to
   * the specified value.
   */
  def setSeed(seed: => Long): URIO[Has[TestRandom], Unit] =
    ZIO.accessM(_.get.setSeed(seed))

  /**
   * The buffer of the `TestRandom`.
   */
  final case class Buffer(
    booleans: List[Boolean] = List.empty,
    bytes: List[Chunk[Byte]] = List.empty,
    chars: List[Char] = List.empty,
    doubles: List[Double] = List.empty,
    floats: List[Float] = List.empty,
    integers: List[Int] = List.empty,
    longs: List[Long] = List.empty,
    strings: List[String] = List.empty
  )
}
