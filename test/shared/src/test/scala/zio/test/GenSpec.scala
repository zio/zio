package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.{ DefaultRuntime, Managed, UIO, ZIO }
import zio.random.Random
import zio.stream.ZStream
import zio.test.mock.MockRandom
import zio.test.TestUtils.label

object GenSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(monadLeftIdentity, "monad left identity"),
    label(monadRightIdentity, "monad right identity"),
    label(monadAssociativity, "monad associativity"),
    label(anyByteShrinksToZero, "anyByte shrinks to zero"),
    label(anyCharShrinksToZero, "anyChar shrinks to zero"),
    label(anyFloatShrinksToZero, "anyFloat shrinks to zero"),
    label(anyIntShrinksToZero, "anyInt shrinks to zero"),
    label(anyLongShrinksToZero, "anyLong shrinks to zero"),
    label(anyShortShrinksToZero, "anyShort shrinks to zero"),
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
    label(filterFiltersValuesAccordingToPredicate, "filter filters values according to predicate"),
    label(fromIterableConstructsDeterministicGenerators, "fromIterable constructs deterministic generators"),
    label(intGeneratesValuesInRange, "int generates values in range"),
    label(intShrinksToBottomOfRange, "int shrinks to bottom of range"),
    label(listOfShrinksToSmallestLength, "listOf shrinks to smallest length"),
    label(none, "none generates the constant empty value"),
    label(optionOfGeneratesOptionalValues, "optionOf generates optional values"),
    label(optionOfShrinksToNone, "optionOf shrinks to None"),
    label(printableCharGeneratesValuesInRange, "printableChar generates values in range"),
    label(printableCharShrinksToBottomOfRange, "printableChar shrinks to bottom of range"),
    label(shortGeneratesValuesInRange, "short generates values in range"),
    label(shortShrinksToBottomOfRange, "short shrinks to bottom of range"),
    label(someShrinksToSmallestValue, "some shrinks to smallest value"),
    label(stringShrinksToSmallestString, "string shrinks to smallest string"),
    label(uniformGeneratesValuesInRange, "uniform generates values between 0 and 1"),
    label(uniformShrinksToZero, "uniform shrinks to zero"),
    label(unit, "unit generates the constant unit value"),
    label(vectorOfShrinksToSmallestLength, "vectorOf shrinks to smallest length"),
    label(zipShrinksCorrectly, "zip shrinks correctly"),
    label(zipWithShrinksCorrectly, "zipWith shrinks correctly"),
    label(testBogusReverseProperty, "integration test with bogus reverse property"),
    label(testShrinkingNonEmptyList, "integration test with shrinking nonempty list"),
    label(testBogusEvenProperty, "integration test with bogus even property")
  )

  val smallInt = Gen.int(-10, 10)
  val three = Gen(ZStream(Sample.unfold[Any, Int, Int](3) { n =>
    if (n == 0) (n, ZStream.empty)
    else (n, ZStream(n - 1))
  }))

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

  def filterFiltersValuesAccordingToPredicate: Future[Boolean] =
    checkSample(smallInt.filter(_ % 2 == 0))(_.forall(_ % 2 == 0))

  def fromIterableConstructsDeterministicGenerators: Future[Boolean] = {
    val exhaustive = Gen.fromIterable(1 to 6)
    val actual     = exhaustive.zipWith(exhaustive)(_ + _)
    val expected   = (1 to 6).flatMap(x => (1 to 6).map(y => x + y))
    checkFinite(actual)(_ == expected)
  }

  def intGeneratesValuesInRange: Future[Boolean] =
    checkSample(smallInt)(_.forall(n => -10 <= n && n <= 10))

  def intShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(smallInt)(-10)

  def listOfShrinksToSmallestLength: Future[Boolean] = {
    val gen = Gen.sized(0, 100)(Gen.listOf(smallInt))
    val io  = shrinks(gen).map(_.reverse.head == List.empty)
    unsafeRunToFuture(io)
  }

  def none: Future[Boolean] =
    checkSample(Gen.none)(_.forall(_ == None))

  def optionOfGeneratesOptionalValues: Future[Boolean] =
    checkSample(Gen.option(smallInt))(as => as.exists(_.isEmpty) && as.exists(_.nonEmpty))

  def optionOfShrinksToNone: Future[Boolean] =
    checkShrink(Gen.option(smallInt))(None)

  def printableCharGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.printableChar)(_.forall(c => 33 <= c && c <= 126))

  def printableCharShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(Gen.printableChar)('!')

  def shortGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.short(5, 10))(_.forall(n => 5 <= n && n <= 10))

  def shortShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(Gen.short(5, 10))(5)

  def someShrinksToSmallestValue: Future[Boolean] =
    checkShrink(Gen.some(smallInt))(Some(-10))

  def stringShrinksToSmallestString: Future[Boolean] = {
    val gen = Gen.sized(3, 3)(Gen.string(Gen.char(65, 90)))
    val io  = shrinks(gen).map(_.reverse.head == "AAA")
    unsafeRunToFuture(io)
  }

  def uniformGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.uniform)(_.forall(n => 0.0 <= n && n < 1.0))

  def uniformShrinksToZero: Future[Boolean] =
    checkShrink(Gen.uniform)(0.0)

  def unit: Future[Boolean] =
    checkSample(Gen.unit)(_.forall(_ => true))

  def vectorOfShrinksToSmallestLength: Future[Boolean] = {
    val gen = Gen.sized(2, 64)(Gen.vectorOf(Gen.uniform))
    val io  = shrinks(gen).map(_.reverse.head == Vector(0.0, 0.0))
    unsafeRunToFuture(io)
  }

  def zipShrinksCorrectly: Future[Boolean] =
    checkShrink(three <*> three)((0, 0))

  def zipWithShrinksCorrectly: Future[Boolean] =
    checkShrink(smallInt.zipWith(smallInt)(_ + _))(-20)

  def testBogusReverseProperty: Future[Boolean] = {
    val gen = for {
      as <- Gen.sized(0, 100)(Gen.listOf(Gen.anyInt))
      bs <- Gen.sized(0, 100)(Gen.listOf(Gen.anyInt))
    } yield (as, bs)
    val predicate = Predicate.predicate[(List[Int], List[Int])]("") {
      case (as, bs) =>
        val p = (as ++ bs).reverse == (as.reverse ++ bs.reverse)
        if (p) Assertion.success else Assertion.Failure(())
    }
    val test = checkSome(100)(gen)(predicate).map {
      case Assertion.Failure(FailureDetails.Predicate(fragment, _)) =>
        fragment.value.toString == "(List(0),List(1))" ||
          fragment.value.toString == "(List(1),List(0))" ||
          fragment.value.toString == "(List(0),List(-1))" ||
          fragment.value.toString == "(List(-1),List(0))"
      case _ => false
    }
    unsafeRunToFuture(test)
  }

  def testShrinkingNonEmptyList: Future[Boolean] = {
    val gen       = Gen.sized(1, 100)(Gen.listOf(Gen.anyInt))
    val predicate = Predicate.predicate[List[Int]]("")(_ => Assertion.Failure(()))
    val test = checkSome(100)(gen)(predicate).map {
      case Assertion.Failure(FailureDetails.Predicate(fragment, _)) =>
        fragment.value.toString == "List(0)"
      case _ => false
    }
    unsafeRunToFuture(test)
  }

  def testBogusEvenProperty: Future[Boolean] = {
    val gen = Gen.int(0, 100)
    val predicate = Predicate.predicate[Int]("") { n =>
      val p = n % 2 == 0
      if (p) Assertion.Success else Assertion.Failure(())
    }
    val test = checkSome(100)(gen)(predicate).map {
      case Assertion.Failure(FailureDetails.Predicate(fragment, _)) =>
        fragment.value.toString == "1"
      case _ => false
    }
    unsafeRunToFuture(test)
  }

  def checkEqual[A](left: Gen[Random, A], right: Gen[Random, A]): Future[Boolean] =
    unsafeRunToFuture(equal(left, right))

  def checkSample[A](gen: Gen[Random, A])(f: List[A] => Boolean): Future[Boolean] =
    unsafeRunToFuture(sample(gen).map(f))

  def checkFinite[A](gen: Gen[Random, A])(f: List[A] => Boolean): Future[Boolean] =
    unsafeRunToFuture(gen.sample.map(_.value).runCollect.map(f))

  def checkShrink[A](gen: Gen[Random, A])(a: A): Future[Boolean] =
    unsafeRunToFuture(alwaysShrinksTo(gen)(a: A))

  def sample[A](gen: Gen[Random, A]): ZIO[Random, Nothing, List[A]] =
    gen.sample.map(_.value).forever.take(100).runCollect

  def alwaysShrinksTo[A](gen: Gen[Random, A])(a: A): ZIO[Random, Nothing, Boolean] =
    ZIO.collectAll(List.fill(100)(shrinksTo(gen))).map(_.forall(_ == a))

  def shrinksTo[A](gen: Gen[Random, A]): ZIO[Random, Nothing, A] =
    shrinks(gen).map(_.reverse.head)

  def shrinks[A](gen: Gen[Random, A]): ZIO[Random, Nothing, List[A]] =
    gen.sample.take(1).flatMap(_.shrinkSearch(_ => true)).take(100).runCollect

  def equal[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] =
    equalSample(left, right).zipWith(equalShrink(left, right))(_ && _)

  def forAll[E <: Throwable, A](zio: ZIO[Random, E, Boolean]): Future[Boolean] =
    unsafeRunToFuture(ZIO.collectAll(List.fill(100)(zio)).map(_.forall(identity)))

  def equalSample[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] = {
    val mockRandom = Managed.fromEffect(MockRandom.make(MockRandom.DefaultData))
    for {
      leftSample  <- sample(left).provideManaged(mockRandom)
      rightSample <- sample(right).provideManaged(mockRandom)
    } yield leftSample == rightSample
  }

  def equalShrink[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] = {
    val mockRandom = Managed.fromEffect(MockRandom.make(MockRandom.DefaultData))
    for {
      leftShrinks  <- ZIO.collectAll(List.fill(100)(shrinks(left))).provideManaged(mockRandom)
      rightShrinks <- ZIO.collectAll(List.fill(100)(shrinks(right))).provideManaged(mockRandom)
    } yield leftShrinks == rightShrinks
  }

  def showTree[R, A](sample: Sample[R, A], offset: Int = 0): ZIO[R, Nothing, String] = {
    val head = " " * offset + sample.value + "\n"
    val tail = sample.shrink.mapM(showTree(_, offset + 2)).runCollect.map(_.mkString("\n"))
    tail.map(head + _)
  }
}
