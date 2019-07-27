/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import zio._
import zio.random.Random

trait MockRandom extends Random {
  val random: MockRandom.Service[Any]
}

object MockRandom {

  trait Service[R] extends Random.Service[R] {
    def feedInts(ints: Int*): UIO[Unit]
    def feedBooleans(booleans: Boolean*): UIO[Unit]
    def feedDoubles(doubles: Double*): UIO[Unit]
    def feedFloats(floats: Float*): UIO[Unit]
    def feedLongs(longs: Long*): UIO[Unit]
    def feedChars(chars: Char*): UIO[Unit]
    def feedStrings(strings: String*): UIO[Unit]
    def feedBytes(bytes: Chunk[Byte]*): UIO[Unit]
    def clearInts: UIO[Unit]
    def clearBooleans: UIO[Unit]
    def clearDoubles: UIO[Unit]
    def clearFloats: UIO[Unit]
    def clearLongs: UIO[Unit]
    def clearChars: UIO[Unit]
    def clearStrings: UIO[Unit]
    def clearBytes: UIO[Unit]
  }

  case class Mock(randomState: Ref[MockRandom.Data]) extends MockRandom.Service[Any] {

    val nextBoolean: UIO[Boolean] = nextRandom(shiftBooleans)

    def nextBytes(length: Int): UIO[Chunk[Byte]] = nextRandom(shiftBytes(length))

    val nextDouble: UIO[Double] = nextRandom(shiftDoubles)

    val nextFloat: UIO[Float] = nextRandom(shiftFloats)

    val nextGaussian: UIO[Double] = nextDouble

    def nextInt(n: Int): UIO[Int] = nextRandom(shiftIntWithLimit(n))

    val nextInt: UIO[Int] = nextRandom(shiftIntegers)

    val nextLong: UIO[Long] = nextRandom(shiftLongs)

    val nextPrintableChar: UIO[Char] = nextRandom(shiftChars)

    def nextString(length: Int): UIO[String] = nextRandom(shiftStrings(length))

    def shuffle[A](list: List[A]): UIO[List[A]] = Random.shuffleWith(nextInt, list)

    def feedInts(ints: Int*): UIO[Unit] =
      randomState.update(data => data.copy(integers = ints.toList ::: data.integers)).unit

    def feedBooleans(booleans: Boolean*): UIO[Unit] =
      randomState.update(data => data.copy(booleans = booleans.toList ::: data.booleans)).unit

    def feedDoubles(doubles: Double*): UIO[Unit] =
      randomState.update(data => data.copy(doubles = doubles.toList ::: data.doubles)).unit

    def feedFloats(floats: Float*): UIO[Unit] =
      randomState.update(data => data.copy(floats = floats.toList ::: data.floats)).unit

    def feedLongs(longs: Long*): UIO[Unit] =
      randomState.update(data => data.copy(longs = longs.toList ::: data.longs)).unit

    def feedChars(chars: Char*): UIO[Unit] =
      randomState.update(data => data.copy(chars = chars.toList ::: data.chars)).unit

    def feedStrings(strings: String*): UIO[Unit] =
      randomState.update(data => data.copy(strings = strings.toList ::: data.strings)).unit

    def feedBytes(bytes: Chunk[Byte]*): UIO[Unit] =
      randomState.update(data => data.copy(bytes = bytes.toList ::: data.bytes)).unit

    val clearInts: UIO[Unit] =
      randomState.update(data => data.copy(integers = List.empty)).unit

    val clearBooleans: UIO[Unit] =
      randomState.update(data => data.copy(booleans = List.empty)).unit

    val clearDoubles: UIO[Unit] =
      randomState.update(data => data.copy(doubles = List.empty)).unit

    val clearFloats: UIO[Unit] =
      randomState.update(data => data.copy(floats = List.empty)).unit

    val clearLongs: UIO[Unit] =
      randomState.update(data => data.copy(longs = List.empty)).unit

    val clearChars: UIO[Unit] =
      randomState.update(data => data.copy(chars = List.empty)).unit

    val clearStrings: UIO[Unit] =
      randomState.update(data => data.copy(strings = List.empty)).unit

    val clearBytes: UIO[Unit] =
      randomState.update(data => data.copy(bytes = List.empty)).unit

    private def nextRandom[T](shift: Data => (T, Data)) =
      for {
        data            <- randomState.get
        (next, shifted) = shift(data)
        _               <- randomState.update(_ => shifted)
      } yield next

    private def shiftBooleans(data: Data) =
      (
        data.booleans.headOption.fold(MockRandom.defaultBoolean)(identity),
        data.copy(booleans = shiftLeft(data.booleans))
      )

    private def shiftIntegers(data: Data) =
      (
        data.integers.headOption.fold(MockRandom.defaultInteger)(identity),
        data.copy(integers = shiftLeft(data.integers))
      )

    private def shiftIntWithLimit(limit: Int)(data: Data) = {
      val next = data.integers.headOption.fold(MockRandom.defaultInteger)(identity)
      (Math.min(limit, next), data.copy(integers = shiftLeft(data.integers)))
    }

    private def shiftDoubles(data: Data) =
      (data.doubles.headOption.fold(MockRandom.defaultDouble)(identity), data.copy(doubles = shiftLeft(data.doubles)))

    private def shiftFloats(data: Data) =
      (data.floats.headOption.fold(MockRandom.defaultFloat)(identity), data.copy(floats = shiftLeft(data.floats)))

    private def shiftLongs(data: Data) =
      (data.longs.headOption.fold(MockRandom.defaultLong)(identity), data.copy(longs = shiftLeft(data.longs)))

    private def shiftChars(data: Data) =
      (data.chars.headOption.fold(MockRandom.defaultChar)(identity), data.copy(chars = shiftLeft(data.chars)))

    private def shiftStrings(length: Int)(data: Data) = {
      val next = data.strings.headOption.fold(MockRandom.defaultString)(identity)
      (next.substring(0, Math.min(length, next.length)), data.copy(strings = shiftLeft(data.strings)))
    }

    private def shiftBytes(length: Int)(data: Data) = {
      val next = data.bytes.headOption.fold(MockRandom.defaultBytes)(identity)
      (next.take(length), data.copy(bytes = shiftLeft(data.bytes)))
    }

    private def shiftLeft[T](l: List[T]): List[T] = l match {
      case x :: xs => xs :+ x
      case _       => l
    }
  }

  def make(data: Data): UIO[MockRandom] =
    makeMock(data).map { mock =>
      new MockRandom {
        val random = mock
      }
    }

  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data).map(Mock(_))

  def feedInts(ints: Int*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedInts(ints: _*))

  def feedBooleans(booleans: Boolean*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedBooleans(booleans: _*))

  def feedDoubles(doubles: Double*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedDoubles(doubles: _*))

  def feedFloats(floats: Float*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedFloats(floats: _*))

  def feedLongs(longs: Long*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedLongs(longs: _*))

  def feedChars(chars: Char*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedChars(chars: _*))

  def feedStrings(strings: String*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedStrings(strings: _*))

  def feedBytes(bytes: Chunk[Byte]*): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.feedBytes(bytes: _*))

  val clearInts: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearInts)

  val clearBooleans: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearBooleans)

  val clearDoubles: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearDoubles)

  val clearFloats: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearFloats)

  val clearLongs: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearLongs)

  val clearChars: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearChars)

  val clearStrings: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearStrings)

  val clearBytes: ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.clearBytes)

  val DefaultData: Data = Data()

  val defaultInteger = 1
  val randomIntegers = defaultInteger :: 2 :: 3 :: 4 :: 5 :: Nil
  val defaultBoolean = true
  val randomBooleans = defaultBoolean :: false :: Nil
  val defaultDouble  = (defaultInteger / 10).toDouble
  val randomDoubles  = randomIntegers.map(_.toDouble / 10)
  val defaultFloat   = (defaultInteger / 10).toFloat
  val randomFloats   = randomIntegers.map(_.toFloat / 10)
  val defaultLong    = defaultInteger.toLong
  val randomLongs    = randomIntegers.map(_.toLong)
  val defaultChar    = 'a'
  val randomChars    = defaultChar :: 'b' :: 'c' :: 'd' :: 'e' :: Nil
  val defaultString  = defaultChar.toString
  val randomStrings  = randomChars.map(_.toString)
  val defaultBytes   = Chunk(defaultInteger.toByte)
  val randomBytes    = randomIntegers.map(i => Chunk(i.toByte))

  final case class Data(
    integers: List[Int] = randomIntegers,
    booleans: List[Boolean] = randomBooleans,
    doubles: List[Double] = randomDoubles,
    floats: List[Float] = randomFloats,
    longs: List[Long] = randomLongs,
    chars: List[Char] = randomChars,
    strings: List[String] = randomStrings,
    bytes: List[Chunk[Byte]] = randomBytes
  )
}
