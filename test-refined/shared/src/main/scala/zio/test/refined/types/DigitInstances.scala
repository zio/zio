package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.all._
import zio.test.Gen
import zio.test.magnolia.DeriveGen
import zio.{Has, Random}

object digit extends DigitInstances

trait DigitInstances {
  private def hexStringsGen(n: Int): Gen[Has[Random], String] = Gen
    .oneOf(
      Gen.stringN(n)(Gen.upperHexChar),
      Gen.stringN(n)(Gen.lowerHexChar)
    )

  val md5Gen: Gen[Has[Random], MD5]       = hexStringsGen(32).map(Refined.unsafeApply)
  val sha1Gen: Gen[Has[Random], SHA1]     = hexStringsGen(40).map(Refined.unsafeApply)
  val sha224Gen: Gen[Has[Random], SHA224] = hexStringsGen(56).map(Refined.unsafeApply)
  val sha256Gen: Gen[Has[Random], SHA256] = hexStringsGen(64).map(Refined.unsafeApply)
  val sha384Gen: Gen[Has[Random], SHA384] = hexStringsGen(96).map(Refined.unsafeApply)
  val sha512Gen: Gen[Has[Random], SHA512] = hexStringsGen(128).map(Refined.unsafeApply)

  implicit val md5Arbitrary: DeriveGen[MD5]       = DeriveGen.instance(md5Gen)
  implicit val sha1Arbitrary: DeriveGen[SHA1]     = DeriveGen.instance(sha1Gen)
  implicit val sha224Arbitrary: DeriveGen[SHA224] = DeriveGen.instance(sha224Gen)
  implicit val sha256Arbitrary: DeriveGen[SHA256] = DeriveGen.instance(sha256Gen)
  implicit val sha384Arbitrary: DeriveGen[SHA384] = DeriveGen.instance(sha384Gen)
  implicit val sha512Arbitrary: DeriveGen[SHA512] = DeriveGen.instance(sha512Gen)
}
