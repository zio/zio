package zio.test

import org.scalacheck.rng.Seed
import org.scalacheck.Gen.Parameters
import zio._

/**
 * Provides functionality for converting legacy ScalaCheck generators to ZIO
 * Test generators to support upgrading to ZIO Test without having to
 * reimplement existing generators. To use it import this module and then call
 * `toGenZIO` on any existing ScalaCheck generator. For example:
 *
 * {{{
 * import org.scalacheck.Arbitrary
 *
 * import zio._
 * import zio.test._
 * import zio.test.scalacheck._
 *
 * val anyInt: Gen[Any, Int] =
 *   Arbitrary.arbitrary[Int].toGenZIO
 * }}}
 */
package object scalacheck {

  implicit final class ScalaCheckGenSyntax[A](private val self: org.scalacheck.Gen[A]) extends AnyVal {

    /**
     * Converts a legacy ScalaCheck `Gen` to a ZIO Test `Gen`.
     */
    def toGenZIO: Gen[Any, A] =
      Gen.fromZIO {
        for {
          long <- Random.nextLong
          size <- Sized.size
          a    <- ZIO.succeed(self.pureApply(Parameters.default.withSize(size), Seed(long)))
        } yield a
      }
  }
}
