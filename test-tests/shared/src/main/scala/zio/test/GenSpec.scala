package zio.test

import scala.concurrent.Future

import zio.random.Random
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.GenUtils._
import zio.test.TestUtils.{ isSuccess, label }
import zio.ZIO

object GenSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(monadLeftIdentity, "monad left identity"),
    label(monadRightIdentity, "monad right identity"),
    label(monadAssociativity, "monad associativity"),
    label(alphaNumericCharGeneratesLettersAndDigits, "alphaNumericChar generates letter and digits"),
    label(alphaNumericCharShrinksToZero, "alphaNumericChar shrinks to zero"),
    label(anyByteShrinksToZero, "anyByte shrinks to zero"),
    label(anyCharShrinksToZero, "anyChar shrinks to zero"),
    label(anyFloatShrinksToZero, "anyFloat shrinks to zero"),
    label(anyIntShrinksToZero, "anyInt shrinks to zero"),
    label(anyLongShrinksToZero, "anyLong shrinks to zero"),
    label(anyShortShrinksToZero, "anyShort shrinks to zero"),
    label(anyStringShrinksToEmptyString, "anyString shrinks to empty string"),
    label(anyUnicodeCharShrinksToZero, "anyUnicodeChar shrinks to zero"),
    label(booleanGeneratesTrueAndFalse, "boolean generates true and false"),
    label(booleanShrinksToFalse, "boolean shrinks to false"),
    label(byteGeneratesValuesInRange, "byte generates values in range"),
    label(byteShrinksToBottomOfRange, "byte shrinks to bottom of range"),
    label(charGeneratesValuesInRange, "char generates values in range"),
    label(charShrinksToBottomOfRange, "char shrinks to bottom of range"),
    label(constGeneratesConstantValue, "const generates constant value"),
    label(doubleGeneratesValuesInRange, "double generates values in range"),
    label(doubleShrinksToBottomOfRange, "double shrinks to bottom of range"),
    label(eitherShrinksToLeft, "either shrinks to left"),
    label(exponentialGeneratesValuesInRange, "exponential generates values between 0 and positive infinity"),
    label(exponentialShrinksToZero, "exponential shrinks to zero"),
    label(filterFiltersValuesAccordingToPredicate, "filter filters values according to predicate"),
    label(filterFiltersShrinksAccordingToPredicate, "filter filters shrinks according to predicate"),
    label(fromIterableConstructsDeterministicGenerators, "fromIterable constructs deterministic generators"),
    label(functionGeneratesDifferentFunctions, "function generates different functions"),
    label(functionGeneratesFunctionsThatAreNotConstant, "function generates functions that are not constant"),
    label(functionGeneratesReferentiallyTransparentFunctions, "function generates referentially transparent functions"),
    label(intGeneratesValuesInRange, "int generates values in range"),
    label(intShrinksToBottomOfRange, "int shrinks to bottom of range"),
    label(largeGeneratesSizesInRange, "large generates sizes in range"),
    label(listOfGeneratesSizesInRange, "listOf generates sizes in range"),
    label(listOfShrinksToEmptyList, "listOf shrinks to empty list"),
    label(listOf1GeneratesNonEmptyLists, "listOf1 generates nonempty lists"),
    label(listOf1ShrinksToSingletonList, "listOf1 shrinks to singleton list"),
    label(listOfNGeneratesListsOfCorrectSize, "listOfN generates lists of correct size"),
    label(listOfNShrinksElements, "listOfN shrinks elements"),
    label(longGeneratesValuesInRange, "long generates values in range"),
    label(longShrinksToBottomOfRange, "long shrinks to bottom of range"),
    label(mapMMapsAnEffectualFunctionOverAGenerator, "mapMMapsAnEffectualFunctionOverAGenerator"),
    label(mediumGeneratesSizesInRange, "medium generates sizes in range"),
    label(none, "none generates the constant empty value"),
    label(optionOfGeneratesOptionalValues, "optionOf generates optional values"),
    label(optionOfShrinksToNone, "optionOf shrinks to None"),
    label(partialFunctionGeneratesPartialFunctions, "partialFunction generates partial functions"),
    label(printableCharGeneratesValuesInRange, "printableChar generates values in range"),
    label(printableCharShrinksToBottomOfRange, "printableChar shrinks to bottom of range"),
    label(reshrinkAppliesNewShrinkingLogic, "reShrink applies new shrinking logic"),
    label(shortGeneratesValuesInRange, "short generates values in range"),
    label(shortShrinksToBottomOfRange, "short shrinks to bottom of range"),
    label(sizeCanBeModifiedLocally, "size can be modified locally"),
    label(sizedAccessesSizeInEnvironment, "sized accesses size in environment"),
    label(smallGeneratesSizesInRange, "small generates sizes in range"),
    label(someShrinksToSmallestValue, "some shrinks to smallest value"),
    label(stringGeneratesSizesInRange, "string generates sizes in range"),
    label(stringShrinksToEmptyString, "string shrinks to empty string"),
    label(string1GeneratesNonEmptyStrings, "string1 generates nonempty strings"),
    label(string1ShrinksToSingleCharacter, "string1 shrinks to single character"),
    label(stringNGeneratesStringsOfCorrectSize, "stringN generates strings of correct size"),
    label(stringNShrinksCharacters, "stringN shrinks characters"),
    label(suspendLazilyConstructsAGenerator, "suspend lazily constructs a generator"),
    label(uniformGeneratesValuesInRange, "uniform generates values between 0 and 1"),
    label(uniformShrinksToZero, "uniform shrinks to zero"),
    label(unit, "unit generates the constant unit value"),
    label(vectorOfGeneratesSizesInRange, "vectorOf generates sizes in range"),
    label(vectorOfShrinksToEmptyVector, "vectorOf shrinks to empty vector"),
    label(vectorOf1GeneratesNonEmptyVectors, "vectorOf1 generates nonempty vectors"),
    label(vectorOf1ShrinksToSingletonVector, "vectorOf1 shrinks to singleton vector"),
    label(vectorOfNGeneratesVectorsOfCorrectSize, "vectorOfN generates vectors of correct size"),
    label(vectorOfNShrinksElements, "vectorOfN shrinks elements"),
    label(weightedGeneratesWeightedDistribution, "weighted generates weighted distribution"),
    label(zipShrinksCorrectly, "zip shrinks correctly"),
    label(zipWithShrinksCorrectly, "zipWith shrinks correctly"),
    label(testBogusReverseProperty, "integration test with bogus reverse property"),
    label(testShrinkingNonEmptyList, "integration test with shrinking nonempty list"),
    label(testBogusEvenProperty, "integration test with bogus even property"),
    label(testTakeWhileProperty, "integration test with randomly generated functions"),
    label(testSwapProperty, "integration test with multiple parameter function generator")
  )

  val smallInt = Gen.int(-10, 10)
  val three = Gen(ZStream(Sample.unfold[Any, Int, Int](3) { n =>
    if (n == 0) (n, ZStream.empty)
    else (n, ZStream(n - 1))
  }))

  val genIntList: Gen[Random, List[Int]] = Gen.oneOf(
    Gen.const(List.empty),
    for {
      tail <- Gen.suspend(genIntList)
      head <- Gen.int(-10, 10)
    } yield head :: tail
  )

  val genStringIntFn: Gen[Random, String => Int] = Gen.function(Gen.int(-10, 10))

  def monadLeftIdentity: Future[Boolean] =
    checkEqual(smallInt.flatMap(a => Gen.const(a)), smallInt)

  def monadRightIdentity: Future[Boolean] = {
    val n                           = 10
    def f(n: Int): Gen[Random, Int] = Gen.int(-n, n)
    checkEqual(Gen.const(n).flatMap(f), f(n))
  }

  def monadAssociativity: Future[Boolean] = {
    val fa = Gen.int(0, 2)
    def f(p: Int): Gen[Random, (Int, Int)] =
      Gen.const(p) <*> Gen.int(0, 3)
    def g(p: (Int, Int)): Gen[Random, (Int, Int, Int)] =
      Gen.const(p).zipWith(Gen.int(0, 5)) { case ((x, y), z) => (x, y, z) }
    checkEqual(fa.flatMap(f).flatMap(g), fa.flatMap(a => f(a).flatMap(g)))
  }

  def alphaNumericCharGeneratesLettersAndDigits: Future[Boolean] =
    checkSample(Gen.alphaNumericChar)(_.forall(_.isLetterOrDigit))

  def alphaNumericCharShrinksToZero: Future[Boolean] =
    checkShrink(Gen.alphaNumericChar)('0')

  def anyByteShrinksToZero: Future[Boolean] =
    checkShrink(Gen.anyByte)(0)

  def anyCharShrinksToZero: Future[Boolean] =
    checkShrink(Gen.anyChar)(0)

  def anyFloatShrinksToZero: Future[Boolean] =
    checkShrink(Gen.anyFloat)(0f)

  def anyIntShrinksToZero: Future[Boolean] =
    checkShrink(Gen.anyInt)(0)

  def anyLongShrinksToZero: Future[Boolean] =
    checkShrink(Gen.anyLong)(0L)

  def anyShortShrinksToZero: Future[Boolean] =
    checkShrink(Gen.anyShort)(0)

  def anyStringShrinksToEmptyString: Future[Boolean] =
    checkShrink(Gen.anyString)("")

  def anyUnicodeCharShrinksToZero: Future[Boolean] =
    checkShrink(Gen.anyUnicodeChar)(0)

  def booleanGeneratesTrueAndFalse: Future[Boolean] =
    checkSample(Gen.boolean)(ps => ps.exists(identity) && ps.exists(!_))

  def booleanShrinksToFalse: Future[Boolean] =
    checkShrink(Gen.boolean)(false)

  def byteGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.byte(38, 38))(_.forall(_ == 38))

  def byteShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(Gen.byte(38, 38))(38)

  def charGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.char(33, 123))(_.forall(n => 33 <= n && n <= 123))

  def charShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(Gen.char(33, 123))(33)

  def constGeneratesConstantValue: Future[Boolean] =
    checkSample(Gen.const("constant"))(_.forall(_ == "constant"))

  def doubleGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.double(5.0, 9.0))(_.forall(n => 5.0 <= n && n < 9.0))

  def doubleShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(Gen.double(5.0, 9.0))(5.0)

  def eitherShrinksToLeft: Future[Boolean] =
    checkShrink(Gen.either(smallInt, smallInt))(Left(-10))

  def exponentialGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.exponential)(_.forall(_ >= 0))

  def exponentialShrinksToZero: Future[Boolean] =
    checkShrink(Gen.exponential)(0.0)

  def filterFiltersValuesAccordingToPredicate: Future[Boolean] =
    checkSample(smallInt.filter(_ % 2 == 0))(_.forall(_ % 2 == 0))

  def filterFiltersShrinksAccordingToPredicate: Future[Boolean] =
    checkShrink(Gen.int(1, 10).filter(_ % 2 == 0))(2)

  def fromIterableConstructsDeterministicGenerators: Future[Boolean] = {
    val exhaustive = Gen.fromIterable(1 to 6)
    val actual     = exhaustive.zipWith(exhaustive)(_ + _)
    val expected   = (1 to 6).flatMap(x => (1 to 6).map(y => x + y))
    checkFinite(actual)(_ == expected)
  }

  def functionGeneratesDifferentFunctions: Future[Boolean] = {
    val gen = for {
      f <- genStringIntFn
      g <- genStringIntFn
      s <- Gen.string(Gen.anyChar)
    } yield f(s) == g(s)
    checkSample(gen)(_.exists(!_))
  }

  def functionGeneratesFunctionsThatAreNotConstant: Future[Boolean] = {
    val gen = for {
      f  <- genStringIntFn
      s1 <- Gen.string(Gen.anyChar)
      s2 <- Gen.string(Gen.anyChar)
    } yield f(s1) == f(s2)
    checkSample(gen)(_.exists(!_))
  }

  def functionGeneratesReferentiallyTransparentFunctions: Future[Boolean] = {
    val gen = for {
      f <- genStringIntFn
      s <- Gen.string(Gen.anyChar)
    } yield f(s) == f(s)
    checkSample(gen)(_.forall(identity))
  }

  def intGeneratesValuesInRange: Future[Boolean] =
    checkSample(smallInt)(_.forall(n => -10 <= n && n <= 10))

  def intShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(smallInt)(-10)

  def largeGeneratesSizesInRange: Future[Boolean] = {
    val gen = Gen.large(Gen.listOfN(_)(Gen.int(-10, 10)))
    checkSample(gen)(_.forall(_.length <= 100))
  }

  def listOfGeneratesSizesInRange: Future[Boolean] =
    checkSample(Gen.listOf(smallInt))(_.forall { as =>
      val n = as.length
      0 <= n && n <= 100
    })

  def listOfShrinksToEmptyList: Future[Boolean] =
    checkShrink(Gen.listOf(smallInt))(Nil)

  def listOf1GeneratesNonEmptyLists: Future[Boolean] =
    checkSample(Gen.listOf1(smallInt), size = 0)(_.forall(_.nonEmpty))

  def listOf1ShrinksToSingletonList: Future[Boolean] =
    checkShrink(Gen.listOf1(smallInt))(List(-10))

  def listOfNGeneratesListsOfCorrectSize: Future[Boolean] =
    checkSample(Gen.listOfN(10)(smallInt))(_.forall(_.length == 10))

  def listOfNShrinksElements: Future[Boolean] =
    checkShrink(Gen.listOfN(10)(smallInt))(List.fill(10)(-10))

  def longGeneratesValuesInRange: Future[Boolean] = {
    val min = -775050485969923566L
    val max = 2826409893363053690L
    checkSample(Gen.long(min, max))(_.forall(n => min <= n && n <= max))
  }

  def longShrinksToBottomOfRange: Future[Boolean] = {
    val min = -8649088475068069159L
    val max = 7907688119669724678L
    checkShrink(Gen.long(min, max))(min)
  }

  def mapMMapsAnEffectualFunctionOverAGenerator: Future[Boolean] = {
    val gen = Gen.int(1, 6).mapM(n => ZIO.succeed(n + 6))
    checkSample(gen)(_.forall(n => 7 <= n && n <= 12))
  }

  def mediumGeneratesSizesInRange: Future[Boolean] = {
    val gen = Gen.medium(Gen.listOfN(_)(Gen.int(-10, 10)))
    checkSample(gen)(_.forall(_.length <= 100))
  }

  def none: Future[Boolean] =
    checkSample(Gen.none)(_.forall(_ == None))

  def optionOfGeneratesOptionalValues: Future[Boolean] =
    checkSample(Gen.option(smallInt))(as => as.exists(_.isEmpty) && as.exists(_.nonEmpty))

  def optionOfShrinksToNone: Future[Boolean] =
    checkShrink(Gen.option(smallInt))(None)

  def partialFunctionGeneratesPartialFunctions: Future[Boolean] = {
    val gen = for {
      f <- Gen.partialFunction[Random, String, Int](Gen.int(-10, 10))
      s <- Gen.string(Gen.anyChar)
    } yield f.lift(s)
    checkSample(gen) { results =>
      results.exists(_.isEmpty) && results.exists(_.nonEmpty)
    }
  }

  def printableCharGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.printableChar)(_.forall(c => 33 <= c && c <= 126))

  def printableCharShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(Gen.printableChar)('!')

  def reshrinkAppliesNewShrinkingLogic: Future[Boolean] = {
    val gen = Gen.int(0, 10).reshrink(Sample.shrinkIntegral(10))
    checkShrink(gen)(10)
  }

  def shortGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.short(5, 10))(_.forall(n => 5 <= n && n <= 10))

  def shortShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(Gen.short(5, 10))(5)

  def sizeCanBeModifiedLocally: Future[Boolean] = {
    val getSize = Gen.size.sample.map(_.value).runCollect.map(_.head)
    val result = for {
      x <- Sized.withSize(200)(getSize)
      y <- getSize
    } yield x == 2 * y
    unsafeRunToFuture(provideSize(result)(100))
  }

  def sizedAccessesSizeInEnvironment: Future[Boolean] =
    checkSample(Gen.sized(Gen.const(_)), size = 50)(_.forall(_ == 50))

  def smallGeneratesSizesInRange: Future[Boolean] = {
    val gen = Gen.small(Gen.listOfN(_)(Gen.int(-10, 10)))
    checkSample(gen)(_.forall(_.length <= 100))
  }

  def someShrinksToSmallestValue: Future[Boolean] =
    checkShrink(Gen.some(smallInt))(Some(-10))

  def stringGeneratesSizesInRange: Future[Boolean] =
    checkSample(Gen.string(Gen.printableChar))(_.forall { as =>
      val n = as.length
      0 <= n && n <= 100
    })

  def stringShrinksToEmptyString: Future[Boolean] =
    checkShrink(Gen.string(Gen.printableChar))("")

  def string1GeneratesNonEmptyStrings: Future[Boolean] =
    checkSample(Gen.string1(Gen.printableChar), size = 0)(_.forall(_.nonEmpty))

  def string1ShrinksToSingleCharacter: Future[Boolean] =
    checkShrink(Gen.string1(Gen.printableChar))("!")

  def stringNGeneratesStringsOfCorrectSize: Future[Boolean] =
    checkSample(Gen.stringN(10)(Gen.printableChar))(_.forall(_.length == 10))

  def stringNShrinksCharacters: Future[Boolean] =
    checkShrink(Gen.stringN(10)(Gen.printableChar))("!!!!!!!!!!")

  def suspendLazilyConstructsAGenerator: Future[Boolean] =
    unsafeRunToFuture {
      val reverseProp = testM("reverse") {
        check(genIntList) { as =>
          assert(as.reverse.reverse, equalTo(as))
        }
      }
      isSuccess(reverseProp)
    }

  def uniformGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.uniform)(_.forall(n => 0.0 <= n && n < 1.0))

  def uniformShrinksToZero: Future[Boolean] =
    checkShrink(Gen.uniform)(0.0)

  def unit: Future[Boolean] =
    checkSample(Gen.unit)(_.forall(_ => true))

  def vectorOfGeneratesSizesInRange: Future[Boolean] =
    checkSample(Gen.vectorOf(smallInt))(_.forall { as =>
      val n = as.length
      0 <= n && n <= 100
    })

  def vectorOfShrinksToEmptyVector: Future[Boolean] =
    checkShrink(Gen.vectorOf(smallInt))(Vector.empty)

  def vectorOf1GeneratesNonEmptyVectors: Future[Boolean] =
    checkSample(Gen.vectorOf1(smallInt), size = 0)(_.forall(_.nonEmpty))

  def vectorOf1ShrinksToSingletonVector: Future[Boolean] =
    checkShrink(Gen.vectorOf1(smallInt))(Vector(-10))

  def vectorOfNGeneratesVectorsOfCorrectSize: Future[Boolean] =
    checkSample(Gen.vectorOfN(10)(smallInt))(_.forall(_.length == 10))

  def vectorOfNShrinksElements: Future[Boolean] =
    checkShrink(Gen.vectorOfN(10)(smallInt))(Vector.fill(10)(-10))

  def weightedGeneratesWeightedDistribution: Future[Boolean] = {
    val weighted = Gen.weighted((Gen.const(true), 10), (Gen.const(false), 90))
    checkSample(weighted)(ps => ps.count(!_) > ps.count(identity))
  }

  def zipShrinksCorrectly: Future[Boolean] =
    checkShrink(three <*> three)((0, 0))

  def zipWithShrinksCorrectly: Future[Boolean] =
    checkShrink(smallInt.zipWith(smallInt)(_ + _))(-20)

  def testBogusReverseProperty: Future[Boolean] = {
    val gen = for {
      as <- Gen.int(0, 100).flatMap(Gen.listOfN(_)(Gen.anyInt))
      bs <- Gen.int(0, 100).flatMap(Gen.listOfN(_)(Gen.anyInt))
    } yield (as, bs)
    def test(a: (List[Int], List[Int])): TestResult = a match {
      case (as, bs) =>
        val p = (as ++ bs).reverse == (as.reverse ++ bs.reverse)
        if (p) assert((), Assertion.anything) else assert((as, bs), Assertion.nothing)
    }
    val property = checkSome(gen)(100)(test).map { result =>
      result.failures.fold(false) {
        case BoolAlgebra.Value(failureDetails) =>
          failureDetails.assertion.head.value.toString == "(List(0),List(1))" ||
            failureDetails.assertion.head.value.toString == "(List(1),List(0))" ||
            failureDetails.assertion.head.value.toString == "(List(0),List(-1))" ||
            failureDetails.assertion.head.value.toString == "(List(-1),List(0))"
        case _ => false
      }
    }
    unsafeRunToFuture(property)
  }

  def testShrinkingNonEmptyList: Future[Boolean] = {
    val gen                            = Gen.int(1, 100).flatMap(Gen.listOfN(_)(Gen.anyInt))
    def test(a: List[Int]): TestResult = assert(a, Assertion.nothing)
    val property = checkSome(gen)(100)(test).map { result =>
      result.failures.fold(false) {
        case BoolAlgebra.Value(failureDetails) =>
          failureDetails.assertion.head.value.toString == "List(0)"
        case _ => false
      }
    }
    unsafeRunToFuture(property)
  }

  def testBogusEvenProperty: Future[Boolean] = {
    val gen = Gen.int(0, 100)
    def test(n: Int): TestResult = {
      val p = n % 2 == 0
      if (p) assert((), Assertion.anything) else assert(n, Assertion.nothing)
    }
    val property = checkSome(gen)(100)(test).map { result =>
      result.failures.fold(false) {
        case BoolAlgebra.Value(failureDetails) =>
          failureDetails.assertion.head.value.toString == "1"
        case _ => false
      }
    }
    unsafeRunToFuture(property)
  }

  def testTakeWhileProperty: Future[Boolean] = {
    val ints                                      = Gen.listOf(Gen.int(-10, 10))
    val intBooleanFn: Gen[Random, Int => Boolean] = Gen.function(Gen.boolean)
    unsafeRunToFuture {
      val takeWhileProp = testM("takeWhile") {
        check(ints, intBooleanFn) { (as, f) =>
          assert(as.takeWhile(f).forall(f), isTrue)
        }
      }
      isSuccess(takeWhileProp)
    }
  }

  def testSwapProperty: Future[Boolean] = {
    val ints                                  = Gen.anyInt
    val genFn: Gen[Random, (Int, Int) => Int] = Gen.function2(Gen.anyInt)
    def swap[A, B, C](f: (A, B) => C): (B, A) => C =
      (b, a) => f(a, b)
    unsafeRunToFuture {
      val swapProp = testM("swap") {
        check(ints, ints, genFn) { (a, b, f) =>
          val g = swap(swap(f))
          assert(f(a, b), equalTo(g(a, b)))
        }
      }
      isSuccess(swapProp)
    }
  }
}
