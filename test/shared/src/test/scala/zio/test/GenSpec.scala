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
    label(booleanGeneratesTrueAndFalse, "boolean generates true and false"),
    label(booleanShrinksToFalse, "boolean shrinks to false"),
    label(charGeneratesValuesInRange, "char generates values in range"),
    label(constGeneratesConstantValue, "const generates constant value"),
    label(doubleGeneratesValuesInRange, "double generates values in range"),
    label(filterFiltersValuesAccordingToPredicate, "filter filters values according to predicate"),
    label(fromIterableGeneratorsCompose, "finite generators compose"),
    label(intGeneratesValuesInRange, "int generates values in range"),
    label(intShrinksToBottomOfRange, "int shrinks to bottom of range"),
    label(listOfShrinksToSmallestLength, "listOf shrinks to smallest length"),
    label(noShrinkRemovesShrinkingLogic, "no shrink removes shrinking logic"),
    label(uniformGeneratesValuesInRange, "uniform generates values between 0 and 1"),
    label(unionCombinesTwoGenerators, "union combines two generators"),
    label(unit, "unit generates the constant unit value"),
    label(withShrinkAddsShrinkingLogic, "with shrink adds shrinking logic"),
    label(zipShrinksCorrectly, "zip shrinks correctly"),
    label(zipWithShrinksCorrectly, "zipWith shrinks correctly")
  )

  val smallInt = Gen.int(-10 to 10)

  def monadLeftIdentity: Future[Boolean] =
    checkEqual(smallInt.flatMap(a => Gen.const(a)), smallInt)

  def monadRightIdentity: Future[Boolean] = {
    val n                           = 10
    def f(n: Int): Gen[Random, Int] = Gen.int(-n to n)
    checkEqual(Gen.const(n).flatMap(f), f(n))
  }

  def monadAssociativity: Future[Boolean] = {
    val fa = Gen.int(0 to 2)
    def f(p: Int): Gen[Random, (Int, Int)] =
      Gen.const(p) <*> Gen.int(0 to 3)
    def g(p: (Int, Int)): Gen[Random, (Int, Int, Int)] =
      Gen.const(p).zipWith(Gen.int(0 to 5)) { case ((x, y), z) => (x, y, z) }
    checkEqual(fa.flatMap(f).flatMap(g), fa.flatMap(a => f(a).flatMap(g)))
  }

  def booleanGeneratesTrueAndFalse: Future[Boolean] =
    checkSample(Gen.boolean)(ps => ps.exists(identity) && ps.exists(!_))

  def booleanShrinksToFalse: Future[Boolean] =
    checkShrink(Gen.boolean)(false)

  def charGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.char(33 to 123))(_.forall(n => 33 <= n && n <= 123))

  def constGeneratesConstantValue: Future[Boolean] =
    checkSample(Gen.const("constant"))(_.forall(_ == "constant"))

  def doubleGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.double(5.0, 9.0))(_.forall(n => 5.0 <= n && n < 9.0))

  def filterFiltersValuesAccordingToPredicate: Future[Boolean] =
    checkSample(smallInt.filter(_ % 2 == 0))(_.forall(_ % 2 == 0))

  def fromIterableGeneratorsCompose: Future[Boolean] = {
    val exhaustive = Gen.fromIterable(1 to 6)
    val actual     = exhaustive.zipWith(exhaustive)(_ + _)
    val expected   = (1 to 6).flatMap(x => (1 to 6).map(y => x + y))
    checkFinite(actual)(_ == expected)
  }

  def intGeneratesValuesInRange: Future[Boolean] =
    checkSample(smallInt)(_.forall(n => -10 <= n && n <= 10))

  def intShrinksToBottomOfRange: Future[Boolean] =
    checkShrink(smallInt)(-10)

  def listOfShrinksToSmallestLength: Future[Boolean] =
    checkShrink(Gen.listOf(0 to 10)(Gen.boolean))(Nil)

  def noShrinkRemovesShrinkingLogic: Future[Boolean] =
    forAll(smallInt.noShrink.sample.flatMap(_.shrink).runCollect.map(_.isEmpty))

  def uniformGeneratesValuesInRange: Future[Boolean] =
    checkSample(Gen.uniform)(_.forall(n => 0.0 <= n && n < 1.0))

  def unionCombinesTwoGenerators: Future[Boolean] = {
    val as = Gen.fromIterable(2 to 3)
    val bs = Gen.fromIterable(5 to 7)
    val cs = Gen.union(as, bs)
    checkFinite(cs)(_ == List(2, 5, 3, 6, 7))
  }

  def unit: Future[Boolean] =
    checkSample(Gen.unit)(_.forall(_ => true))

  def withShrinkAddsShrinkingLogic: Future[Boolean] = {
    def shrink(bound: Int): Int => ZStream[Any, Nothing, Int] =
      n => if (n > bound) ZStream(n - 1) ++ shrink(bound)(n - 1) else ZStream.empty
    checkShrink(Gen.const(10).withShrink(shrink(0)))(0)
  }

  def zipShrinksCorrectly: Future[Boolean] =
    checkShrink(smallInt <*> smallInt)((-10, -10))

  def zipWithShrinksCorrectly: Future[Boolean] =
    checkShrink(smallInt.zipWith(smallInt)(_ + _))(-20)

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
    gen.sample.flatMap(sample => ZStream(sample.value) ++ sample.shrink).take(1000).runCollect

  def summarize[A](as: List[A]): List[(A, Int)] =
    as.groupBy(identity).mapValues(_.length).toList

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
}
