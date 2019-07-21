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

package zio.testkit

import zio._
import zio.random.Random
import zio.testkit.TestRandom.Data

final case class TestRandom(ref: Ref[TestRandom.Data]) extends Random.Service[Any] {

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

  private def nextRandom[T](shift: Data => (T, Data)) =
    for {
      data            <- ref.get
      (next, shifted) = shift(data)
      _               <- ref.update(_ => shifted)
    } yield next

  private def shiftBooleans(data: Data) =
    (data.booleans.headOption.fold(TestRandom.defaultBoolean)(identity), data.copy(booleans = shiftLeft(data.booleans)))

  private def shiftIntegers(data: Data) =
    (data.integers.headOption.fold(TestRandom.defaultInteger)(identity), data.copy(integers = shiftLeft(data.integers)))

  private def shiftIntWithLimit(limit: Int)(data: Data) = {
    val next = data.integers.headOption.fold(TestRandom.defaultInteger)(identity)
    (Math.min(limit, next), data.copy(integers = shiftLeft(data.integers)))
  }

  private def shiftDoubles(data: Data) =
    (data.doubles.headOption.fold(TestRandom.defaultDouble)(identity), data.copy(doubles = shiftLeft(data.doubles)))

  private def shiftFloats(data: Data) =
    (data.floats.headOption.fold(TestRandom.defaultFloat)(identity), data.copy(floats = shiftLeft(data.floats)))

  private def shiftLongs(data: Data) =
    (data.longs.headOption.fold(TestRandom.defaultLong)(identity), data.copy(longs = shiftLeft(data.longs)))

  private def shiftChars(data: Data) =
    (data.chars.headOption.fold(TestRandom.defaultChar)(identity), data.copy(chars = shiftLeft(data.chars)))

  private def shiftStrings(length: Int)(data: Data) = {
    val next = data.strings.headOption.fold(TestRandom.defaultString)(identity)
    (next.substring(0, Math.min(length, next.length)), data.copy(strings = shiftLeft(data.strings)))
  }

  private def shiftBytes(length: Int)(data: Data) = {
    val next = data.bytes.headOption.fold(TestRandom.defaultBytes)(identity)
    (next.take(length), data.copy(bytes = shiftLeft(data.bytes)))
  }

  private def shiftLeft[T](l: List[T]): List[T] = l match {
    case x :: xs => xs :+ x
    case _       => l
  }
}

object TestRandom {

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
