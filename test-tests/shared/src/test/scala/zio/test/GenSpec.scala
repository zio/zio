package zio.test

import scala.math.Numeric.DoubleIsFractional
import zio.{ UIO, ZIO, ZLayer }
import zio.random.Random
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.{ check => Check, checkN => CheckN }
import zio.test.environment.TestRandom
import zio.test.TestAspect.scala2Only

object GenSpec extends ZIOBaseSpec {
  def spec = suite("GenSpec")(
    suite("integration tests")(
      testM("with bogus even property") {
        val gen = Gen.int(0, 100)

        def test(n: Int): TestResult = {
          val p = n % 2 == 0
          if (p) assert(())(Assertion.anything) else assert(n)(Assertion.nothing)
        }

        assertM(CheckN(100)(gen)(test).flatMap(result => {
          result.run.map(_.failures.fold(false) {
            case BoolAlgebra.Value(failureDetails) =>
              failureDetails.assertion.head.value.toString == "1"
            case _ => false
          })
        }))(isTrue)
      },
      testM("with bogus reverse property") {
        val gen = for {
          as <- Gen.int(0, 100).flatMap(Gen.listOfN(_)(Gen.anyInt))
          bs <- Gen.int(0, 100).flatMap(Gen.listOfN(_)(Gen.anyInt))
        } yield (as, bs)

        def test(a: (List[Int], List[Int])): TestResult = a match {
          case (as, bs) =>
            val p = (as ++ bs).reverse == (as.reverse ++ bs.reverse)
            if (p) assert(())(Assertion.anything) else assert((as, bs))(Assertion.nothing)
        }
        assertM(CheckN(100)(gen)(test).flatMap {
          result =>
            result.run.map(_.failures.fold(false) {
              case BoolAlgebra.Value(failureDetails) =>
                failureDetails.assertion.head.value.toString == "(List(0),List(1))" ||
                  failureDetails.assertion.head.value.toString == "(List(1),List(0))" ||
                  failureDetails.assertion.head.value.toString == "(List(0),List(-1))" ||
                  failureDetails.assertion.head.value.toString == "(List(-1),List(0))"
              case _ => false
            })
        })(isTrue)
      },
      testM("with randomly generated functions") {
        val ints                                      = Gen.listOf(Gen.int(-10, 10))
        val intBooleanFn: Gen[Random, Int => Boolean] = Gen.function(Gen.boolean)

        Check(ints, intBooleanFn) { (as, f) =>
          assert(as.takeWhile(f).forall(f))(isTrue)
        }
      },
      testM("with multiple parameter function generator") {
        val ints                                  = Gen.anyInt
        val genFn: Gen[Random, (Int, Int) => Int] = Gen.function2(Gen.anyInt)

        def swap[A, B, C](f: (A, B) => C): (B, A) => C =
          (b, a) => f(a, b)

        Check(ints, ints, genFn) { (a, b, f) =>
          val g = swap(swap(f))
          assert(f(a, b))(equalTo(g(a, b)))
        }
      },
      testM("with shrinking nonempty list") {
        val gen = Gen.int(1, 100).flatMap(Gen.listOfN(_)(Gen.anyInt))

        def test(a: List[Int]): TestResult = assert(a)(Assertion.nothing)

        assertM(CheckN(100)(gen)(test).flatMap(result => {
          result.run.map(_.failures.fold(false) {
            case BoolAlgebra.Value(failureDetails) =>
              failureDetails.assertion.head.value.toString == "List(0)"
            case _ => false
          })
        }))(isTrue)
      }
    ),
    suite("monad laws")(
      testM("monad left identity") {
        assertM(equal(smallInt.flatMap(a => Gen.const(a)), smallInt))(isTrue)
      },
      testM("monad right identity") {
        val n = 10

        def f(n: Int): Gen[Random, Int] = Gen.int(-n, n)

        assertM(equal(Gen.const(n).flatMap(f), f(n)))(isTrue)
      },
      testM("monad associativity") {
        val fa = Gen.int(0, 2)

        def f(p: Int): Gen[Random, (Int, Int)] =
          Gen.const(p) <*> Gen.int(0, 3)

        def g(p: (Int, Int)): Gen[Random, (Int, Int, Int)] =
          Gen.const(p).zipWith(Gen.int(0, 5)) { case ((x, y), z) => (x, y, z) }

        assertM(equal(fa.flatMap(f).flatMap(g), fa.flatMap(a => f(a).flatMap(g))))(isTrue)
      }
    ),
    suite("sample")(
      testM("alphaNumericChar generates numbers and letters") {
        checkSample(Gen.alphaNumericChar)(forall(isTrue), _.map(_.isLetterOrDigit))
      },
      testM("boolean generates true and false") {
        checkSample(Gen.boolean)(contains(true) && contains(false))
      },
      testM("byte generates values in range") {
        checkSample(Gen.byte(38, 38))(forall(equalTo(38.toByte)))
      },
      testM("char generates values in range") {
        checkSample(Gen.char(33, 123))(
          forall(isGreaterThanEqualTo(33) && isLessThanEqualTo(123)),
          _.map(_.toInt)
        )
      },
      testM("const generates constant value") {
        checkSample(Gen.const("constant"))(forall(equalTo("constant")))
      },
      testM("double generates values in range") {
        checkSample(Gen.double(5.0, 9.0))(forall(isGreaterThanEqualTo(5.0) && isLessThan(9.0)))
      },
      testM("exponential generates values between 0 and positive infinity") {
        checkSample(Gen.exponential)(forall(isGreaterThanEqualTo(0.0)))
      },
      testM("filter filters values according to predicate") {
        checkSample(smallInt.filter(_ % 2 == 0))(forall(equalTo(0)), _.map(_ % 2))
      },
      testM("function generates different functions") {
        val gen = for {
          f <- genStringIntFn
          g <- genStringIntFn
          s <- Gen.string(Gen.anyChar)
        } yield f(s) == g(s)
        checkSample(gen)(isTrue, _.exists(!_))
      },
      testM("function generates functions that are not constant") {
        val gen = for {
          f  <- genStringIntFn
          s1 <- Gen.string(Gen.anyChar)
          s2 <- Gen.string(Gen.anyChar)
        } yield f(s1) == f(s2)
        checkSample(gen)(isTrue, _.exists(!_))
      },
      testM("function generates referentially transparent functions") {
        val gen = for {
          f <- genStringIntFn
          s <- Gen.string(Gen.anyChar)
        } yield f(s) == f(s)
        checkSample(gen)(isTrue, _.forall(identity))
      },
      testM("int generates values in range") {
        checkSample(smallInt)(forall(isGreaterThanEqualTo(-10) && isLessThanEqualTo(10)))
      },
      testM("int is safe for ranges greater than Int.MaxValue") {
        val gen = Gen.int(0, Int.MaxValue)
        checkSample(gen)(forall(isGreaterThanEqualTo(0) && isLessThanEqualTo(Int.MaxValue)))
      },
      testM("large generates sizes in range") {
        val gen = Gen.large(Gen.listOfN(_)(Gen.int(-10, 10)))
        checkSample(gen)(forall(isLessThanEqualTo(100)), _.map(_.length))
      },
      testM("listOf generates sizes in range") {
        checkSample(Gen.listOf(smallInt))(
          forall(isGreaterThanEqualTo(0) && isLessThanEqualTo(100)),
          _.map(_.length)
        )
      },
      testM("listOf1 generates nonempty lists") {
        checkSample(Gen.listOf1(smallInt), size = 0)(forall(isNonEmpty))
      },
      testM("listOfN generates lists of correct size") {
        checkSample(Gen.listOfN(10)(smallInt))(forall(equalTo(10)), _.map(_.length))
      },
      testM("long generates values in range") {
        val min = -775050485969923566L
        val max = 2826409893363053690L
        checkSample(Gen.long(min, max))(forall(isGreaterThanEqualTo(min) && isLessThanEqualTo(max)))
      },
      testM("mapM maps an effectual function over a generator") {
        val gen = Gen.int(1, 6).mapM(n => ZIO.succeed(n + 6))
        checkSample(gen)(forall(Assertion.isGreaterThanEqualTo(7) && isLessThanEqualTo(12)))
      },
      testM("medium generates sizes in range") {
        val gen = Gen.medium(Gen.listOfN(_)(Gen.int(-10, 10)))
        checkSample(gen)(forall(isGreaterThanEqualTo(0) && isLessThanEqualTo(100)), _.map(_.length))
      },
      testM("none generates the constant empty value") {
        checkSample(Gen.none)(forall(isNone))
      },
      testM("optionOf generates optional values") {
        checkSample(Gen.option(smallInt))(exists(isNone) && exists(isSome(anything)))
      },
      testM("partialFunction generates partial functions") {
        val gen = for {
          f <- Gen.partialFunction[Random, String, Int](Gen.int(-10, 10))
          s <- Gen.string(Gen.anyChar)
        } yield f.lift(s)
        checkSample(gen)(exists(isNone) && exists(isSome(anything)))
      },
      testM("printableChar generates values in range") {
        checkSample(Gen.printableChar)(
          forall(isGreaterThanEqualTo(33) && isLessThanEqualTo(126)),
          _.map(_.toInt)
        )
      },
      testM("short generates values in range") {
        checkSample(Gen.short(5, 10))(forall(isGreaterThanEqualTo(5) && isLessThanEqualTo(10)), _.map(_.toInt))
      },
      testM("sized accesses size in environment") {
        checkSample(Gen.sized(Gen.const(_)), size = 50)(forall(equalTo(50)))
      },
      testM("small generates sizes in range") {
        val gen = Gen.small(Gen.listOfN(_)(Gen.int(-10, 10)))
        checkSample(gen)(forall(isGreaterThanEqualTo(0) && isLessThanEqualTo(100)), _.map(_.length))
      },
      testM("string generates sizes in range") {
        checkSample(Gen.string(Gen.printableChar))(
          forall(isGreaterThanEqualTo(0) && isLessThanEqualTo(100)),
          _.map(_.length)
        )
      },
      testM("string1 generates nonempty strings") {
        checkSample(Gen.string1(Gen.printableChar), size = 0)(forall(isFalse), _.map(_.isEmpty))
      },
      testM("stringN generates strings of correct size") {
        checkSample(Gen.stringN(10)(Gen.printableChar))(forall(equalTo(10)), _.map(_.length))
      },
      testM("uniform generates values between 0 and 1") {
        checkSample(Gen.uniform)(forall(isGreaterThanEqualTo(0.0) && isLessThanEqualTo(1.0)))
      },
      testM("unit generates the constant unit value") {
        checkSample(Gen.unit)(forall(equalTo(())))
      },
      testM("vectorOf generates sizes in range") {
        checkSample(Gen.vectorOf(smallInt))(
          forall(isGreaterThanEqualTo(0) && isLessThanEqualTo(100)),
          _.map(_.length)
        )
      },
      testM("vectorOf1 generates nonempty vectors") {
        checkSample(Gen.vectorOf1(smallInt), size = 0)(forall(isNonEmpty))
      },
      testM("vectorOfN generates vectors of correct size") {
        checkSample(Gen.vectorOfN(10)(smallInt))(forall(equalTo(10)), _.map(_.length))
      },
      testM("weighted generates weighted distribution") {
        val weighted = Gen.weighted((Gen.const(true), 10), (Gen.const(false), 90))
        checkSample(weighted)(isTrue, ps => ps.count(!_) > ps.count(identity))
      }
    ),
    suite("shrinks")(
      testM("alphaNumericChar shrinks to zero") {
        checkShrink(Gen.alphaNumericChar)('0')
      },
      testM("anyByte shrinks to zero") {
        checkShrink(Gen.anyByte)(0)
      },
      testM("anyChar shrinks to zero") {
        checkShrink(Gen.anyChar)(0)
      },
      testM("anyFloat shrinks to zero") {
        checkShrink(Gen.anyFloat)(0)
      },
      testM("anyInt shrinks to zero") {
        checkShrink(Gen.anyInt)(0)
      },
      testM("anyLong shrinks to zero") {
        checkShrink(Gen.anyLong)(0)
      },
      testM("anyShort shrinks to zero") {
        checkShrink(Gen.anyShort)(0)
      },
      testM("anyString shrinks to empty string") {
        checkShrink(Gen.anyString)("")
      },
      testM("anyUnicodeChar shrinks to zero") {
        checkShrink(Gen.anyUnicodeChar)(0)
      },
      testM("boolean shrinks to false") {
        checkShrink(Gen.boolean)(false)
      },
      testM("byte shrinks to bottom of range") {
        checkShrink(Gen.byte(38, 123))(38)
      },
      testM("char shrinks to bottom of range") {
        checkShrink(Gen.char(33, 123))(33)
      },
      testM("double shrinks to bottom of range") {
        checkShrink(Gen.double(5.0, 9.0))(5.0)
      },
      testM("either shrinks to left") {
        checkShrink(Gen.either(smallInt, smallInt))(Left(-10))
      },
      testM("exponential shrinks to zero") {
        checkShrink(Gen.exponential)(0.0)
      },
      testM("filter filters shrinks according to predicate") {
        checkShrink(Gen.int(1, 10).filter(_ % 2 == 0))(2)
      },
      testM("int shrinks to bottom of range") {
        checkShrink(smallInt)(-10)
      },
      testM("listOf shrinks to empty list") {
        checkShrink(Gen.listOf(smallInt))(Nil)
      },
      testM("listOf1 shrinks to singleton list") {
        checkShrink(Gen.listOf1(smallInt))(List(-10))
      },
      testM("listOfN shrinks elements") {
        checkShrink(Gen.listOfN(10)(smallInt))(List.fill(10)(-10))
      },
      testM("long shrinks to bottom of range") {
        val min = -8649088475068069159L
        val max = 7907688119669724678L
        checkShrink(Gen.long(min, max))(min)
      },
      testM("optionOf shrinks to None") {
        checkShrink(Gen.option(smallInt))(None)
      },
      testM("printableChar shrinks to bottom of range") {
        checkShrink(Gen.printableChar)('!')
      },
      testM("reShrink applies new shrinking logic") {
        val gen = Gen.int(0, 10).reshrink(Sample.shrinkIntegral(10))
        checkShrink(gen)(10)
      },
      testM("short shrinks to bottom of range") {
        checkShrink(Gen.short(5, 10))(5)
      },
      testM("some shrinks to smallest value") {
        checkShrink(Gen.some(smallInt))(Some(-10))
      },
      testM("string shrinks to empty string") {
        checkShrink(Gen.string(Gen.printableChar))("")
      },
      testM("string1 shrinks to single character") {
        checkShrink(Gen.string1(Gen.printableChar))("!")
      },
      testM("stringN shrinks characters") {
        checkShrink(Gen.stringN(10)(Gen.printableChar))("!!!!!!!!!!")
      },
      testM("uniform shrinks to zero") {
        checkShrink(Gen.uniform)(0.0)
      },
      testM("vectorOf shrinks to empty vector") {
        checkShrink(Gen.vectorOf(smallInt))(Vector.empty)
      },
      testM("vectorOf1 shrinks to singleton vector") {
        checkShrink(Gen.vectorOf1(smallInt))(Vector(-10))
      },
      testM("vectorOfN shrinks elements") {
        checkShrink(Gen.vectorOfN(10)(smallInt))(Vector.fill(10)(-10))
      },
      testM("zip shrinks correctly") {
        checkShrink(three <*> three)((0, 0))
      },
      testM("zipWith shrinks correctly") {
        checkShrink(smallInt.zipWith(smallInt)(_ + _))(-20)
      }
    ),
    suite("zipWith")(
      testM("left preservation") {
        checkM(deterministic, deterministic) { (a, b) =>
          for {
            left  <- sample(a.zip(b).map(_._1))
            right <- sample(a)
          } yield assert(left)(startsWith(right))
        }
      } @@ scala2Only,
      testM("right preservation") {
        checkM(deterministic, deterministic) { (a, b) =>
          for {
            left  <- sample(a.zip(b).map(_._2))
            right <- sample(b)
          } yield assert(left)(startsWith(right))
        }
      } @@ scala2Only,
      testM("shrinking") {
        checkM(random, random) { (a, b) =>
          for {
            left  <- shrink(a.zip(b))
            right <- shrink(a.cross(b))
          } yield assert(left)(equalTo(right))
        }
      },
      testM("shrink search") {
        val smallInt = Gen.int(0, 9)
        checkM(Gen.const(shrinkable.zip(shrinkable)), smallInt, smallInt) { (gen, m, n) =>
          for {
            result <- shrinkWith(gen) { case (x, y) => x < m && y < n }
          } yield assert(result.reverse.headOption)(isSome(equalTo((m, 0)) || equalTo((0, n))))
        }
      }
    ),
    testM("fromIterable constructs deterministic generators") {
      val expected   = List.range(1, 6).flatMap(x => List.range(1, 6).map(y => x + y))
      val exhaustive = Gen.fromIterable(1 until 6)
      val actual     = exhaustive.crossWith(exhaustive)(_ + _)
      checkFinite(actual)(equalTo(expected))
    } @@ scala2Only, //todo fix when #2232 is resolved
    testM("size can be modified locally") {
      val getSize = Gen.size.sample.map(_.value).runCollect.map(_.head)
      val result = for {
        x <- Sized.withSize(200)(getSize)
        y <- getSize
      } yield x == 2 * y
      assertM(provideSize(result)(100))(isTrue)
    },
    testM("suspend lazily constructs a generator") {
      check(genIntList) { as =>
        assert(as.reverse.reverse)(equalTo(as))
      }
    },
    testM("runCollect") {
      val domain = List.range(-10, 10)
      val gen    = Gen.fromIterable(domain)
      for {
        a <- gen.runCollect
        b <- gen.runCollect
      } yield assert(a)(equalTo(domain)) &&
        assert(b)(equalTo(domain))
    } @@ scala2Only,
    testM("runCollectN") {
      val gen = Gen.int(-10, 10)
      for {
        a <- gen.runCollectN(100)
        b <- gen.runCollectN(100)
      } yield assert(a)(not(equalTo(b))) &&
        assert(a)(hasSize(equalTo(100))) &&
        assert(b)(hasSize(equalTo(100)))
    },
    testM("runHead") {
      assertM(Gen.int(-10, 10).runHead)(isSome(isWithin(-10, 10)))
    }
  )

  def alwaysShrinksTo[R, A](gen: Gen[R, A])(a: A): ZIO[R, Nothing, TestResult] = {
    val shrinks = if (TestPlatform.isJS) 1 else 100
    ZIO.collectAll(List.fill(shrinks)(shrinksTo(gen))).map(assert(_)(forall(equalTo(a))))
  }

  def checkFinite[A, B](
    gen: Gen[Random, A]
  )(assertion: Assertion[B], f: List[A] => B = (a: List[A]) => a): ZIO[Random, Nothing, TestResult] =
    assertM(gen.sample.map(_.value).runCollect.map(f))(assertion)

  def checkSample[A, B](
    gen: Gen[Random with Sized, A],
    size: Int = 100
  )(assertion: Assertion[B], f: List[A] => B = (a: List[A]) => a): ZIO[Random, Nothing, TestResult] =
    assertM(provideSize(sample100(gen).map(f))(size))(assertion)

  def checkShrink[A](gen: Gen[Random with Sized, A])(a: A): ZIO[Random, Nothing, TestResult] =
    provideSize(alwaysShrinksTo(gen)(a: A))(100)

  val deterministic: Gen[Random with Sized, Gen[Any, Int]] =
    Gen.listOf1(Gen.int(-10, 10)).map(as => Gen.fromIterable(as))

  def equal[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] =
    equalSample(left, right).zipWith(equalShrink(left, right))(_ && _)

  def equalShrink[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] = {
    val testRandom = TestRandom.default.build
    for {
      leftShrinks  <- ZIO.collectAll(List.fill(100)(shrinks(left))).provideManaged(testRandom)
      rightShrinks <- ZIO.collectAll(List.fill(100)(shrinks(right))).provideManaged(testRandom)
    } yield leftShrinks == rightShrinks
  }

  def equalSample[A](left: Gen[Random, A], right: Gen[Random, A]): UIO[Boolean] = {
    val testRandom = TestRandom.default.build
    for {
      leftSample  <- sample100(left).provideManaged(testRandom)
      rightSample <- sample100(right).provideManaged(testRandom)
    } yield leftSample == rightSample
  }

  val genIntList: Gen[Random, List[Int]] = Gen.oneOf(
    Gen.const(List.empty),
    for {
      tail <- Gen.suspend(genIntList)
      head <- Gen.int(-10, 10)
    } yield head :: tail
  )

  val genStringIntFn: Gen[Random, String => Int] = Gen.function(Gen.int(-10, 10))

  def provideSize[A](zio: ZIO[Random with Sized, Nothing, A])(n: Int): ZIO[Random, Nothing, A] =
    zio.provideSomeManaged {
      (ZLayer.service[Random.Service] ++ Sized.live(n)).value
    }

  val random: Gen[Any, Gen[Random, Int]] =
    Gen.const(Gen.int(-10, 10))

  def shrinks[R, A](gen: Gen[R, A]): ZIO[R, Nothing, List[A]] =
    gen.sample.forever.take(1).flatMap(_.shrinkSearch(_ => true)).take(1000).runCollect

  def shrinksTo[R, A](gen: Gen[R, A]): ZIO[R, Nothing, A] =
    shrinks(gen).map(_.reverse.head)

  val smallInt = Gen.int(-10, 10)

  def sample[R, A](gen: Gen[R, A]): ZIO[R, Nothing, List[A]] =
    gen.sample.map(_.value).runCollect

  def sample100[R, A](gen: Gen[R, A]): ZIO[R, Nothing, List[A]] =
    gen.sample.map(_.value).forever.take(100).runCollect

  def shrink[R, A](gen: Gen[R, A]): ZIO[R, Nothing, A] =
    gen.sample.take(1).flatMap(_.shrinkSearch(_ => true)).take(1000).runLast.map(_.get)

  val shrinkable: Gen[Random, Int] =
    Gen.fromRandomSample(_.nextInt(90).map(_ + 10).map(Sample.shrinkIntegral(0)))

  def shrinkWith[R, A](gen: Gen[R, A])(f: A => Boolean): ZIO[R, Nothing, List[A]] =
    gen.sample.take(1).flatMap(_.shrinkSearch(!f(_))).take(1000).filter(!f(_)).runCollect

  val three = Gen(ZStream(Sample.unfold[Any, Int, Int](3) { n =>
    if (n == 0) (n, ZStream.empty)
    else (n, ZStream(n - 1))
  }))
}
