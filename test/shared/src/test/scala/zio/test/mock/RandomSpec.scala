package zio.test.mock

import scala.Predef.{ assert => SAssert, _ }

import zio.random.Random
import zio.test.mock.MockRandom.Data
import zio.{ UIO, _ }

object RandomSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(nextIntWithSingleValue, "MockRandom returns next int when data is single value")
    SAssert(nextIntWithEmptyData, "MockRandom returns next int when data is empty")
    SAssert(nextIntWithDefault, "MockRandom returns next int when data is default")
    SAssert(nextIntWithSequence, "MockRandom returns next int when data is sequence")
    SAssert(nextIntWithFeed, "MockRandom returns next int when data is feed")
    SAssert(nextIntWithClear, "MockRandom returns next int when data is clear")
    SAssert(nextIntWithLimit, "MockRandom returns next int when data is respect limit")

    SAssert(nextBooleanWithSingleValue, "MockRandom returns next boolean when data is single value")
    SAssert(nextBooleanWithEmptyData, "MockRandom returns next boolean when data is empty")
    SAssert(nextBooleanWithDefault, "MockRandom returns next boolean when data is default")
    SAssert(nextBooleanWithSequence, "MockRandom returns next boolean when data is sequence")
    SAssert(nextBooleanWithFeed, "MockRandom returns next boolean when data is feed")
    SAssert(nextBooleanWithClear, "MockRandom returns next boolean when data is clear")

    SAssert(nextDoubleWithSingleValue, "MockRandom returns next double when data is single value")
    SAssert(nextDoubleWithEmptyData, "MockRandom returns next double when data is empty")
    SAssert(nextDoubleWithDefault, "MockRandom returns next double when data is default")
    SAssert(nextDoubleWithSequence, "MockRandom returns next double when data is sequence")
    SAssert(nextDoubleWithFeed, "MockRandom returns next double when data is feed")
    SAssert(nextDoubleWithClear, "MockRandom returns next double when data is clear")

    SAssert(nextGaussian, "MockRandom returns next Gaussian same as double")

    SAssert(nextFloatWithSingleValue, "MockRandom returns next float when data is single value")
    SAssert(nextFloatWithEmptyData, "MockRandom returns next float when data is empty")
    SAssert(nextFloatWithDefault, "MockRandom returns next float when data is default")
    SAssert(nextFloatWithSequence, "MockRandom returns next float when data is sequence")
    SAssert(nextFloatWithFeed, "MockRandom returns next float when data is feed")
    SAssert(nextFloatWithClear, "MockRandom returns next float when data is clear")

    SAssert(nextLongWithSingleValue, "MockRandom returns next long when data is single value")
    SAssert(nextLongWithEmptyData, "MockRandom returns next long when data is empty")
    SAssert(nextLongWithDefault, "MockRandom returns next long when data is default")
    SAssert(nextLongWithSequence, "MockRandom returns next long when data is sequence")
    SAssert(nextLongWithFeed, "MockRandom returns next long when data is feed")
    SAssert(nextLongWithClear, "MockRandom returns next long when data is clear")

    SAssert(nextCharWithSingleValue, "MockRandom returns next char when data is single value")
    SAssert(nextCharWithEmptyData, "MockRandom returns next char when data is empty")
    SAssert(nextCharWithDefault, "MockRandom returns next char when data is default")
    SAssert(nextCharWithSequence, "MockRandom returns next char when data is sequence")
    SAssert(nextCharWithFeed, "MockRandom returns next char when data is feed")
    SAssert(nextCharWithClear, "MockRandom returns next char when data is clear")

    SAssert(nextStringWithSingleValue, "MockRandom returns next string when data is single value")
    SAssert(nextStringWithEmptyData, "MockRandom returns next string when data is empty")
    SAssert(nextStringWithDefault, "MockRandom returns next string when data is default")
    SAssert(nextStringWithSequence, "MockRandom returns next string when data is sequence")
    SAssert(nextStringWithFeed, "MockRandom returns next string when data is feed")
    SAssert(nextStringWithClear, "MockRandom returns next string when data is clear")
    SAssert(nextStringWithLength, "MockRandom returns next string when data is single value - respect length")
    SAssert(
      nextStringLengthIsOver,
      "MockRandom returns next string when data is single value - length > length of the next string"
    )

    SAssert(nextBytesWithSingleValue, "MockRandom returns next bytes when data is single value")
    SAssert(nextBytesWithEmptyData, "MockRandom returns next bytes when data is empty")
    SAssert(nextBytesWithDefault, "MockRandom returns next bytes when data is default")
    SAssert(nextBytesWithSequence, "MockRandom returns next bytes when data is sequence")
    SAssert(nextBytesWithFeed, "MockRandom returns next bytes when data is feed")
    SAssert(nextBytesWithClear, "MockRandom returns next bytes when data is clear")
    SAssert(nextBytesWithLength, "MockRandom returns next bytes when data is single value - length < number of bytes")
    SAssert(
      nextBytesLengthIsOver,
      "MockRandom returns next bytes when data is single value - length > length of the next array"
    )

    SAssert(
      shuffleListWithNoElements,
      "MockRandom shuffle returns the same list in the same order always when list contains no elements"
    )
    SAssert(
      shuffleListWithSingleElement,
      "MockRandom shuffle returns the same list in the same order always when list contains single element"
    )
    SAssert(
      shuffleListWithTwoElementsSame,
      "MockRandom shuffle returns the same list in the same order sometimes when list contains two elements"
    )
    SAssert(
      shuffleListWithThreeElementsSame,
      "MockRandom shuffle returns the same list in the same order sometimes when list contains three elements"
    )
    SAssert(
      shuffleListWithManyElementsSame,
      "MockRandom shuffle returns the same list in the same order sometimes when list contains many elements"
    )

    SAssert(
      shuffleListWithTwoElementsReversed,
      "MockRandom shuffle returns the same list in different order sometimes when list contains two elements"
    )
    SAssert(
      shuffleListWithThreeElementsReversed,
      "MockRandom shuffle returns the same list in different order sometimes when list contains three elements"
    )
    SAssert(
      shuffleListWithManyElementsReversed,
      "MockRandom shuffle returns the same list in different order sometimes when list contains many elements"
    )
  }

  def nextIntWithEmptyData =
    checkWith(Data(integers = Nil), List(MockRandom.defaultInteger))(_.nextInt)

  def nextIntWithLimit =
    unsafeRun(
      for {
        mockRandom <- MockRandom.makeMock(Data(integers = List(5, 6, 7)))
        next1      <- mockRandom.nextInt(2)
        next2      <- mockRandom.nextInt(6)
        next3      <- mockRandom.nextInt(99)
      } yield List(next1, next2, next3) == List(2, 6, 7)
    )

  def nextIntWithDefault =
    checkWith(Data(), List(1, 2, 3, 4, 5))(_.nextInt)

  def nextIntWithSingleValue =
    checkWith(Data(integers = List(1)), List(1, 1, 1, 1, 1))(_.nextInt)

  def nextIntWithSequence =
    checkWith(Data(integers = List(1, 2, 3)), List(1, 2, 3, 1, 2))(_.nextInt)

  def nextIntWithFeed =
    checkFeed(_.feedInts, List(6, 7, 8, 9, 10))(_.nextInt)

  def nextIntWithClear =
    checkClear(_.clearInts, MockRandom.defaultInteger)(_.nextInt)

  def nextBooleanWithEmptyData =
    checkWith(Data(booleans = Nil), List(MockRandom.defaultBoolean))(_.nextBoolean)

  def nextBooleanWithDefault =
    checkWith(Data(), List(true, false, true, false, true))(_.nextBoolean)

  def nextBooleanWithSingleValue =
    checkWith(Data(booleans = List(false)), List(false, false, false, false, false))(_.nextBoolean)

  def nextBooleanWithSequence =
    checkWith(Data(booleans = List(true, true, false)), List(true, true, false, true, true))(_.nextBoolean)

  def nextBooleanWithFeed =
    checkFeed(_.feedBooleans, List(false, true))(_.nextBoolean)

  def nextBooleanWithClear =
    checkClear(_.clearBooleans, MockRandom.defaultBoolean)(_.nextBoolean)

  def nextDoubleWithEmptyData =
    checkWith(Data(doubles = Nil), List(MockRandom.defaultDouble))(_.nextDouble)

  def nextDoubleWithDefault =
    checkWith(Data(), List(0.1d, 0.2d, 0.3d, 0.4d, 0.5d))(_.nextDouble)

  def nextDoubleWithSingleValue =
    checkWith(Data(doubles = List(0.1d)), List(0.1d, 0.1d, 0.1d, 0.1d, 0.1d))(_.nextDouble)

  def nextDoubleWithSequence =
    checkWith(Data(doubles = List(0.1d, 0.2d, 0.3d)), List(0.1d, 0.2d, 0.3d, 0.1d, 0.2d))(_.nextDouble)

  def nextDoubleWithFeed =
    checkFeed(_.feedDoubles, List(0.6d, 0.7d, 0.8d, 0.9d, 1.0d))(_.nextDouble)

  def nextDoubleWithClear =
    checkClear(_.clearDoubles, MockRandom.defaultDouble)(_.nextDouble)

  def nextGaussian =
    checkWith(Data(doubles = List(0.1, 0.2)), List(0.1, 0.2, 0.1))(_.nextGaussian)

  def nextFloatWithEmptyData =
    checkWith(Data(floats = Nil), List(MockRandom.defaultFloat))(_.nextFloat)

  def nextFloatWithDefault =
    checkWith(Data(), List(0.1f, 0.2f, 0.3f, 0.4f, 0.5f))(_.nextFloat)

  def nextFloatWithSingleValue =
    checkWith(Data(floats = List(0.1f)), List(0.1f, 0.1f, 0.1f, 0.1f, 0.1f))(_.nextFloat)

  def nextFloatWithSequence =
    checkWith(Data(floats = List(0.1f, 0.2f, 0.3f)), List(0.1f, 0.2f, 0.3f, 0.1f, 0.2f))(_.nextFloat)

  def nextFloatWithFeed =
    checkFeed(_.feedFloats, List(0.6f, 0.7f, 0.8f, 0.9f, 1.0f))(_.nextFloat)

  def nextFloatWithClear =
    checkClear(_.clearFloats, MockRandom.defaultFloat)(_.nextFloat)

  def nextLongWithEmptyData =
    checkWith(Data(longs = Nil), List(MockRandom.defaultLong))(_.nextLong)

  def nextLongWithDefault =
    checkWith(Data(), List(1L, 2L, 3L, 4L, 5L))(_.nextLong)

  def nextLongWithSingleValue =
    checkWith(Data(longs = List(1L)), List(1L, 1L, 1L, 1L, 1L))(_.nextLong)

  def nextLongWithSequence =
    checkWith(Data(longs = List(1L, 2L, 3L)), List(1L, 2L, 3L, 1L, 2L))(_.nextLong)

  def nextLongWithFeed =
    checkFeed(_.feedLongs, List(6L, 7L, 8L, 9L, 10L))(_.nextLong)

  def nextLongWithClear =
    checkClear(_.clearLongs, MockRandom.defaultLong)(_.nextLong)

  def nextCharWithEmptyData =
    checkWith(Data(chars = Nil), List(MockRandom.defaultChar))(_.nextPrintableChar)

  def nextCharWithDefault =
    checkWith(Data(), List('a', 'b', 'c', 'd', 'e'))(_.nextPrintableChar)

  def nextCharWithSingleValue =
    checkWith(Data(chars = List('a')), List('a', 'a', 'a', 'a', 'a'))(_.nextPrintableChar)

  def nextCharWithSequence =
    checkWith(Data(chars = List('a', 'b', 'c')), List('a', 'b', 'c', 'a', 'b'))(_.nextPrintableChar)

  def nextCharWithFeed =
    checkFeed(_.feedChars, List('f', 'g', 'h', 'i', 'j'))(_.nextPrintableChar)

  def nextCharWithClear =
    checkClear(_.clearChars, MockRandom.defaultChar)(_.nextPrintableChar)

  def nextStringWithEmptyData =
    checkWith(Data(strings = Nil), List(MockRandom.defaultString))(_.nextString(1))

  def nextStringWithLength =
    checkWith(Data(strings = List("longer string")), List("longer s"))(_.nextString(8))

  def nextStringLengthIsOver =
    checkWith(Data(strings = List("longer string")), List("longer string"))(_.nextString(99))

  def nextStringWithDefault =
    checkWith(Data(), List("a", "b", "c", "d", "e"))(_.nextString(1))

  def nextStringWithSingleValue =
    checkWith(Data(strings = List("a")), List("a", "a", "a", "a", "a"))(_.nextString(1))

  def nextStringWithSequence =
    checkWith(Data(strings = List("a", "b", "c")), List("a", "b", "c", "a", "b"))(_.nextString(1))

  def nextStringWithFeed =
    checkFeed(_.feedStrings, List("f", "g", "h", "i", "j"))(_.nextString(1))

  def nextStringWithClear =
    checkClear(_.clearStrings, MockRandom.defaultString)(_.nextString(1))

  def nextBytesWithEmptyData =
    checkWith(Data(bytes = Nil), List(MockRandom.defaultBytes))(_.nextBytes(1))

  def nextBytesLengthIsOver =
    checkWith(Data(bytes = Nil), List(MockRandom.defaultBytes))(_.nextBytes(99))

  def nextBytesWithLength =
    checkWith(Data(bytes = List(Chunk(1.toByte, 2.toByte, 3.toByte))), List(Chunk(1.toByte, 2.toByte)))(_.nextBytes(2))

  def nextBytesWithDefault =
    checkWith(Data(), List(1, 2, 3, 4, 5).map(i => Chunk(i.toByte)))(_.nextBytes(1))

  def nextBytesWithSingleValue =
    checkWith(Data(bytes = List(Chunk(1.toByte))), List(1, 1, 1, 1, 1).map(i => Chunk(i.toByte)))(_.nextBytes(1))

  def nextBytesWithSequence =
    checkWith(Data(bytes = List(1, 2, 3).map(i => Chunk(i.toByte))), List(1, 2, 3, 1, 2).map(i => Chunk(i.toByte)))(
      _.nextBytes(1)
    )

  def nextBytesWithFeed =
    checkFeed(_.feedBytes, List(Chunk(6.toByte), Chunk(7.toByte), Chunk(8.toByte)))(_.nextBytes(1))

  def nextBytesWithClear =
    checkClear(_.clearBytes, MockRandom.defaultBytes)(_.nextBytes(1))

  def checkWith[A](data: Data, expected: List[A])(f: Random.Service[Any] => UIO[A]) =
    unsafeRun(
      for {
        mockRandom    <- MockRandom.makeMock(data)
        randomResults <- IO.foreach(1 to expected.length)(_ => f(mockRandom))
      } yield randomResults == expected
    )

  def shuffleListWithNoElements =
    checkShuffleSame(List.empty[Int])

  def shuffleListWithSingleElement =
    checkShuffleSame(List(1))

  def shuffleListWithTwoElementsSame =
    checkShuffleSame(List(1, 2))

  def shuffleListWithTwoElementsReversed =
    checkShuffleReversed(List(1, 2))

  def shuffleListWithThreeElementsSame =
    checkShuffleSame(List(1, 2, 3))

  def shuffleListWithThreeElementsReversed =
    checkShuffleReversed(List(1, 2, 3))

  def shuffleListWithManyElementsSame =
    checkShuffleSame((1 to 100).toList)

  def shuffleListWithManyElementsReversed =
    checkShuffleReversed((1 to 100).toList)

  def checkShuffleSame[A](input: List[A]) = {
    val identitySwapIndexes =
      ((input.length - 1) to 1 by -1).toList

    unsafeRun(
      for {
        mockRandom <- MockRandom.makeMock(Data(integers = identitySwapIndexes))
        shuffled   <- mockRandom.shuffle(input)
      } yield shuffled == input
    )
  }

  def checkShuffleReversed[A](input: List[A]) = {
    val halfLength  = input.length / 2
    val halfIndexes = (0 until halfLength).toList
    val reverseSwapIndexes =
      if (input.length % 2 == 0) halfIndexes ::: halfIndexes.reverse
      else (halfIndexes :+ halfLength) ::: halfIndexes.reverse

    unsafeRun(
      for {
        mockRandom <- MockRandom.makeMock(Data(integers = reverseSwapIndexes))
        shuffled   <- mockRandom.shuffle(input)
      } yield shuffled == input.reverse
    )
  }

  def checkFeed[A](feed: MockRandom.Mock => Seq[A] => UIO[Unit], expected: List[A])(f: Random.Service[Any] => UIO[A]) =
    unsafeRun(
      for {
        mockRandom <- MockRandom.makeMock(Data())
        _          <- feed(mockRandom)(expected)
        results    <- UIO.foreach(1 to expected.length)(_ => f(mockRandom))
      } yield results == expected
    )

  def checkClear[A](clear: MockRandom.Mock => UIO[Unit], default: A)(f: Random.Service[Any] => UIO[A]) =
    unsafeRun(
      for {
        mockRandom <- MockRandom.makeMock(Data())
        _          <- clear(mockRandom)
        result     <- f(mockRandom)
      } yield result == default
    )
}
