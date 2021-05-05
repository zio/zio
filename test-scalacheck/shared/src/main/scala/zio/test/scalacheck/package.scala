package zio.test

import org.scalacheck
import org.scalacheck.rng.Seed
import org.scalacheck.Gen.Parameters
import zio._
import zio.random.Random
import zio.test.Sized

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
  * val anyInt: Gen[Random with Sized, Int] =
  *   Arbitrary.arbitrary[Int].toGenZIO
  * }}}
  */
package object scalacheck {

  implicit final class ScalaCheckGenSyntax[A](private val self: scalacheck.Gen[A]) extends AnyVal {

    /**
      * Converts a legacy ScalaCheck `Gen` to a ZIO Test `Gen`.
      */
    def toGenZIO: Gen[Random with Sized, A] =
      Gen.fromEffect {
        for {
          long <- random.nextLong
          size <- Sized.size
          a    <- ZIO.succeed(self.pureApply(Parameters.default.withSize(size), Seed(long)))
        } yield a
      }
  }
}
