package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.all._
import zio.test.Gen
import zio.test.magnolia.DeriveGen

/**
 * Brings the instances of `DigitInstances` into scope: `import
 * zio.test.refined.types.digit._`.
 */
object digit extends DigitInstances

/**
 * Instances for the hash string aliases `MD5`, `SHA1`, `SHA224`, `SHA256`,
 * `SHA384` and `SHA512` of `eu.timepit.refined.types.all`. Each value is a
 * string of the digest length in hexadecimal characters, either all upper case
 * or all lower case.
 */
trait DigitInstances {
  private def hexStringsGen(n: Int): Gen[Any, String] = Gen
    .oneOf(
      Gen.stringN(n)(Gen.hexCharUpper),
      Gen.stringN(n)(Gen.hexCharLower)
    )

  /**
   * A generator of `MD5`: a string of 32 characters, either all from
   * `Gen.hexCharUpper` ('0' to '9' and 'A' to 'F') or all from
   * `Gen.hexCharLower` ('0' to '9' and 'a' to 'f'), chosen with equal
   * probability for each value.
   */
  val md5Gen: Gen[Any, MD5] = hexStringsGen(32).map(Refined.unsafeApply)

  /**
   * A generator of `SHA1`: a string of 40 characters, either all from
   * `Gen.hexCharUpper` ('0' to '9' and 'A' to 'F') or all from
   * `Gen.hexCharLower` ('0' to '9' and 'a' to 'f'), chosen with equal
   * probability for each value.
   */
  val sha1Gen: Gen[Any, SHA1] = hexStringsGen(40).map(Refined.unsafeApply)

  /**
   * A generator of `SHA224`: a string of 56 characters, either all from
   * `Gen.hexCharUpper` ('0' to '9' and 'A' to 'F') or all from
   * `Gen.hexCharLower` ('0' to '9' and 'a' to 'f'), chosen with equal
   * probability for each value.
   */
  val sha224Gen: Gen[Any, SHA224] = hexStringsGen(56).map(Refined.unsafeApply)

  /**
   * A generator of `SHA256`: a string of 64 characters, either all from
   * `Gen.hexCharUpper` ('0' to '9' and 'A' to 'F') or all from
   * `Gen.hexCharLower` ('0' to '9' and 'a' to 'f'), chosen with equal
   * probability for each value.
   */
  val sha256Gen: Gen[Any, SHA256] = hexStringsGen(64).map(Refined.unsafeApply)

  /**
   * A generator of `SHA384`: a string of 96 characters, either all from
   * `Gen.hexCharUpper` ('0' to '9' and 'A' to 'F') or all from
   * `Gen.hexCharLower` ('0' to '9' and 'a' to 'f'), chosen with equal
   * probability for each value.
   */
  val sha384Gen: Gen[Any, SHA384] = hexStringsGen(96).map(Refined.unsafeApply)

  /**
   * A generator of `SHA512`: a string of 128 characters, either all from
   * `Gen.hexCharUpper` ('0' to '9' and 'A' to 'F') or all from
   * `Gen.hexCharLower` ('0' to '9' and 'a' to 'f'), chosen with equal
   * probability for each value.
   */
  val sha512Gen: Gen[Any, SHA512] = hexStringsGen(128).map(Refined.unsafeApply)

  /**
   * The implicit `DeriveGen` for `MD5`; it wraps `md5Gen`.
   */
  implicit val md5Arbitrary: DeriveGen[MD5] = DeriveGen.instance(md5Gen)

  /**
   * The implicit `DeriveGen` for `SHA1`; it wraps `sha1Gen`.
   */
  implicit val sha1Arbitrary: DeriveGen[SHA1] = DeriveGen.instance(sha1Gen)

  /**
   * The implicit `DeriveGen` for `SHA224`; it wraps `sha224Gen`.
   */
  implicit val sha224Arbitrary: DeriveGen[SHA224] = DeriveGen.instance(sha224Gen)

  /**
   * The implicit `DeriveGen` for `SHA256`; it wraps `sha256Gen`.
   */
  implicit val sha256Arbitrary: DeriveGen[SHA256] = DeriveGen.instance(sha256Gen)

  /**
   * The implicit `DeriveGen` for `SHA384`; it wraps `sha384Gen`.
   */
  implicit val sha384Arbitrary: DeriveGen[SHA384] = DeriveGen.instance(sha384Gen)

  /**
   * The implicit `DeriveGen` for `SHA512`; it wraps `sha512Gen`.
   */
  implicit val sha512Arbitrary: DeriveGen[SHA512] = DeriveGen.instance(sha512Gen)
}
