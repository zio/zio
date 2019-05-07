package scalaz.zio

import org.scalacheck._

trait GenIO {

  /**
   * Given a generator for `A`, produces a generator for `IO[E, A]` using the `IO.point` constructor.
   */
  def genSyncSuccess[E, A: Arbitrary]: Gen[BIO[E, A]] = Arbitrary.arbitrary[A].map(IO.succeedLazy[A](_))

  /**
   * Given a generator for `A`, produces a generator for `IO[E, A]` using the `IO.async` constructor.
   */
  def genAsyncSuccess[E, A: Arbitrary]: Gen[BIO[E, A]] =
    Arbitrary.arbitrary[A].map(a => IO.effectAsync[Any, E, A](k => k(IO.succeed(a))))

  /**
   * Randomly uses either `genSyncSuccess` or `genAsyncSuccess` with equal probability.
   */
  def genSuccess[E, A: Arbitrary]: Gen[BIO[E, A]] = Gen.oneOf(genSyncSuccess[E, A], genAsyncSuccess[E, A])

  /**
   * Given a generator for `E`, produces a generator for `IO[E, A]` using the `IO.fail` constructor.
   */
  def genSyncFailure[E: Arbitrary, A]: Gen[BIO[E, A]] = Arbitrary.arbitrary[E].map(IO.fail[E])

  /**
   * Given a generator for `E`, produces a generator for `IO[E, A]` using the `IO.async` constructor.
   */
  def genAsyncFailure[E: Arbitrary, A]: Gen[BIO[E, A]] =
    Arbitrary.arbitrary[E].map(err => IO.effectAsync[Any, E, A](k => k(IO.fail(err))))

  /**
   * Randomly uses either `genSyncFailure` or `genAsyncFailure` with equal probability.
   */
  def genFailure[E: Arbitrary, A]: Gen[BIO[E, A]] = Gen.oneOf(genSyncFailure[E, A], genAsyncFailure[E, A])

  /**
   * Randomly uses either `genSuccess` or `genFailure` with equal probability.
   */
  def genIO[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Gen[BIO[E, A]] =
    Gen.oneOf(genSuccess[E, A], genFailure[E, A])

  /**
   * Given a generator for `IO[E, A]`, produces a sized generator for `IO[E, A]` which represents a transformation,
   * by using some random combination of the methods `map`, `flatMap`, `mapError`, and any other method that does not change
   * the success/failure of the value, but may change the value itself.
   */
  def genLikeTrans[E: Arbitrary: Cogen, A: Arbitrary: Cogen](gen: Gen[BIO[E, A]]): Gen[BIO[E, A]] = {
    val functions: BIO[E, A] => Gen[BIO[E, A]] = io =>
      Gen.oneOf(
        genOfFlatMaps[E, A](io)(genSuccess[E, A]),
        genOfMaps[E, A](io),
        genOfRace[E, A](io),
        genOfParallel[E, A](io)(genSuccess[E, A]),
        genOfMapErrors[E, A](io)
    )
    gen.flatMap(io => genTransformations(functions)(io))
  }

  /**
   * Given a generator for `IO[E, A]`, produces a sized generator for `IO[E, A]` which represents a transformation,
   * by using methods that can have no effect on the resulting value (e.g. `map(identity)`, `io.race(never)`, `io.par(io2).map(_._1)`).
   */
  def genIdentityTrans[E: Arbitrary: Cogen, A: Arbitrary: Cogen](gen: Gen[BIO[E, A]]): Gen[BIO[E, A]] = {
    val functions: BIO[E, A] => Gen[BIO[E, A]] = io =>
      Gen.oneOf(
        genOfIdentityFlatMaps[E, A](io),
        genOfIdentityMaps[E, A](io),
        genOfIdentityMapErrors[E, A](io),
        genOfRace[E, A](io),
        genOfParallel[E, A](io)(genSuccess[E, A])
    )
    gen.flatMap(io => genTransformations(functions)(io))
  }

  private def genTransformations[E: Arbitrary: Cogen, A: Arbitrary: Cogen](
    functionGen: BIO[E, A] => Gen[BIO[E, A]]
  )(io: BIO[E, A]): Gen[BIO[E, A]] =
    Gen.sized { size =>
      def append1(n: Int, io: BIO[E, A]): Gen[BIO[E, A]] =
        if (n <= 0) io
        else
          (for {
            updatedIO <- functionGen(io)
          } yield updatedIO).flatMap(append1(n - 1, _))
      append1(size, io)
    }

  private def genOfMaps[E, A: Arbitrary: Cogen](io: BIO[E, A]): Gen[BIO[E, A]] =
    Arbitrary.arbitrary[A => A].map(f => io.map(f))

  private def genOfIdentityMaps[E, A](io: BIO[E, A]): Gen[BIO[E, A]] = Gen.const(io.map(identity))

  private def genOfMapErrors[E: Arbitrary: Cogen, A](io: BIO[E, A]): Gen[BIO[E, A]] =
    Arbitrary.arbitrary[E => E].map(f => io.mapError(f))

  private def genOfIdentityMapErrors[E, A](io: BIO[E, A]): Gen[BIO[E, A]] =
    Gen.const(io.mapError(identity))

  private def genOfFlatMaps[E, A: Cogen](io: BIO[E, A])(
    gen: Gen[BIO[E, A]]
  ): Gen[BIO[E, A]] =
    gen.map(nextIO => io.flatMap(_ => nextIO))

  private def genOfIdentityFlatMaps[E, A](io: BIO[E, A]): Gen[BIO[E, A]] =
    Gen.const(io.flatMap(a => IO.succeedLazy(a)))

  private def genOfRace[E, A](io: BIO[E, A]): Gen[BIO[E, A]] =
    Gen.const(io.race(IO.never))

  private def genOfParallel[E, A](io: BIO[E, A])(gen: Gen[BIO[E, A]]): Gen[BIO[E, A]] =
    gen.map(parIo => io.zipPar(parIo).map(_._1))

}
