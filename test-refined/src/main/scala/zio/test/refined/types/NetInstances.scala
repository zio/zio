package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.net._
import zio.test.Gen
import zio.test.magnolia.DeriveGen

/**
 * Brings the instances of `NetInstances` into scope: `import
 * zio.test.refined.types.net._`.
 */
object net extends NetInstances

/**
 * Instances for the port number and IPv4 address aliases of
 * `eu.timepit.refined.types.net`. The address generators produce four
 * dot-separated numbers.
 */
trait NetInstances {
  private val _0to255 = Gen.int(0, 255)

  /**
   * A generator of `PortNumber` built with `Gen.int(0, 656535)`: from 0 to
   * 656535, both inclusive. The upper bound is 656535, not 65535, so values
   * above 65535 do not satisfy `PortNumber`.
   */
  val portNumberGen: Gen[Any, PortNumber] = Gen.int(0, 656535).map(Refined.unsafeApply)

  /**
   * A generator of `SystemPortNumber` built with `Gen.int(0, 1023)`: from 0 to
   * 1023, both inclusive.
   */
  val systemPortNumberGen: Gen[Any, SystemPortNumber] = Gen.int(0, 1023).map(Refined.unsafeApply)

  /**
   * A generator of `UserPortNumber` built with `Gen.int(1024, 49151)`: from
   * 1024 to 49151, both inclusive.
   */
  val userPortNumberGen: Gen[Any, UserPortNumber] = Gen.int(1024, 49151).map(Refined.unsafeApply)

  /**
   * A generator of `DynamicPortNumber` built with `Gen.int(49152, 65535)`: from
   * 49152 to 65535, both inclusive.
   */
  val dynamicPortNumberGen: Gen[Any, DynamicPortNumber] = Gen.int(49152, 65535).map(Refined.unsafeApply)

  /**
   * A generator of `NonSystemPortNumber` built with `Gen.int(1024, 65535)`:
   * from 1024 to 65535, both inclusive.
   */
  val nonSystemPortNumberGen: Gen[Any, NonSystemPortNumber] = Gen.int(1024, 65535).map(Refined.unsafeApply)

  /**
   * A generator of `Rfc1918ClassAPrivate`: an address `10.a.b.c` with a, b and
   * c each from `Gen.int(0, 255)`, both inclusive.
   */
  val rfc1918ClassAPrivateGen: Gen[Any, Rfc1918ClassAPrivate] =
    (_0to255 <*> _0to255 <*> _0to255).map { case (a, b, c) => Refined.unsafeApply(s"10.$a.$b.$c") }

  /**
   * A generator of `Rfc1918ClassBPrivate`: an address `172.a.b.c` with a from
   * `Gen.int(16, 31)` and b and c each from `Gen.int(0, 255)`, all both
   * inclusive.
   */
  val rfc1918ClassBPrivateGen: Gen[Any, Rfc1918ClassBPrivate] =
    (Gen.int(16, 31) <*> _0to255 <*> _0to255).map { case (a, b, c) => Refined.unsafeApply(s"172.$a.$b.$c") }

  /**
   * A generator of `Rfc1918ClassCPrivate`: an address `192.168.a.b` with a and
   * b each from `Gen.int(0, 255)`, both inclusive.
   */
  val rfc1918ClassCPrivateGen: Gen[Any, Rfc1918ClassCPrivate] =
    (_0to255 <*> _0to255).map { case (a, b) => Refined.unsafeApply(s"192.168.$a.$b") }

  /**
   * A generator of `Rfc1918Private`: one of the class A, class B and class C
   * private addresses of `rfc1918ClassAPrivateGen`, `rfc1918ClassBPrivateGen`
   * and `rfc1918ClassCPrivateGen`, each chosen with equal probability.
   */
  val rfc1918PrivateGen: Gen[Any, Rfc1918Private] =
    Gen
      .oneOf(rfc1918ClassAPrivateGen, rfc1918ClassBPrivateGen, rfc1918ClassCPrivateGen)
      .map(v => Refined.unsafeApply(v.value))

  /**
   * A generator of `Rfc5737Testnet1`: an address `192.0.2.a` with a from
   * `Gen.int(0, 255)`, both inclusive.
   */
  val rfc5737Testnet1Gen: Gen[Any, Rfc5737Testnet1] = _0to255.map(v => Refined.unsafeApply(s"192.0.2.$v"))

  /**
   * A generator of `Rfc5737Testnet2`: an address `198.51.100.a` with a from
   * `Gen.int(0, 255)`, both inclusive.
   */
  val rfc5737Testnet2Gen: Gen[Any, Rfc5737Testnet2] = _0to255.map(v => Refined.unsafeApply(s"198.51.100.$v"))

  /**
   * A generator of `Rfc5737Testnet3`: an address `203.0.113.a` with a from
   * `Gen.int(0, 255)`, both inclusive.
   */
  val rfc5737Testnet3Gen: Gen[Any, Rfc5737Testnet3] = _0to255.map(v => Refined.unsafeApply(s"203.0.113.$v"))

  /**
   * A generator of `Rfc5737Testnet`: one of the addresses of
   * `rfc5737Testnet1Gen`, `rfc5737Testnet2Gen` and `rfc5737Testnet3Gen`, each
   * chosen with equal probability.
   */
  val rfc5737TestnetGen: Gen[Any, Rfc5737Testnet] = Gen
    .oneOf(rfc5737Testnet1Gen, rfc5737Testnet2Gen, rfc5737Testnet3Gen)
    .map(v => Refined.unsafeApply(v.value))

  /**
   * A generator of `Rfc3927LocalLink`: an address `169.254.a.b` with a and b
   * each from `Gen.int(0, 255)`, both inclusive.
   */
  val rfc3927LocalLinkGen: Gen[Any, Rfc3927LocalLink] =
    (_0to255 <*> _0to255).map { case (a, b) => Refined.unsafeApply(s"169.254.$a.$b") }

  /**
   * A generator of `Rfc2544Benchmark`: an address `198.a.b.c` with a from
   * `Gen.int(18, 19)` and b and c each from `Gen.int(0, 255)`, all both
   * inclusive.
   */
  val rfc2544BenchmarkGen: Gen[Any, Rfc2544Benchmark] =
    (Gen.int(18, 19) <*> _0to255 <*> _0to255).map { case (a, b, c) => Refined.unsafeApply(s"198.$a.$b.$c") }

  /**
   * A generator of `PrivateNetwork`: one of the addresses of
   * `rfc1918PrivateGen`, `rfc5737TestnetGen`, `rfc3927LocalLinkGen` and
   * `rfc2544BenchmarkGen`, each chosen with equal probability.
   */
  val privateNetworkGen: Gen[Any, PrivateNetwork] =
    Gen
      .oneOf(rfc1918PrivateGen, rfc5737TestnetGen, rfc3927LocalLinkGen, rfc2544BenchmarkGen)
      .map(v => Refined.unsafeApply(v.value))

  /**
   * The implicit `DeriveGen` for `PortNumber`; it wraps `portNumberGen`.
   */
  implicit val portNumberDeriveGen: DeriveGen[PortNumber] = DeriveGen.instance(portNumberGen)

  /**
   * The implicit `DeriveGen` for `SystemPortNumber`; it wraps
   * `systemPortNumberGen`.
   */
  implicit val systemPortNumberDeriveGen: DeriveGen[SystemPortNumber] = DeriveGen.instance(systemPortNumberGen)

  /**
   * The implicit `DeriveGen` for `UserPortNumber`; it wraps
   * `userPortNumberGen`.
   */
  implicit val userPortNumberDeriveGen: DeriveGen[UserPortNumber] = DeriveGen.instance(userPortNumberGen)

  /**
   * The implicit `DeriveGen` for `DynamicPortNumber`; it wraps
   * `dynamicPortNumberGen`.
   */
  implicit val dynamicPortNumberDeriveGen: DeriveGen[DynamicPortNumber] = DeriveGen.instance(dynamicPortNumberGen)

  /**
   * The implicit `DeriveGen` for `NonSystemPortNumber`; it wraps
   * `nonSystemPortNumberGen`.
   */
  implicit val nonSystemPortNumberDeriveGen: DeriveGen[NonSystemPortNumber] = DeriveGen.instance(nonSystemPortNumberGen)

  /**
   * The implicit `DeriveGen` for `Rfc1918ClassAPrivate`; it wraps
   * `rfc1918ClassAPrivateGen`.
   */
  implicit val rfc1918ClassAPrivateDeriveGen: DeriveGen[Rfc1918ClassAPrivate] =
    DeriveGen.instance(rfc1918ClassAPrivateGen)

  /**
   * The implicit `DeriveGen` for `Rfc1918ClassBPrivate`; it wraps
   * `rfc1918ClassBPrivateGen`.
   */
  implicit val rfc1918ClassBPrivateDeriveGen: DeriveGen[Rfc1918ClassBPrivate] =
    DeriveGen.instance(rfc1918ClassBPrivateGen)

  /**
   * The implicit `DeriveGen` for `Rfc1918ClassCPrivate`; it wraps
   * `rfc1918ClassCPrivateGen`.
   */
  implicit val rfc1918ClassCPrivateDeriveGen: DeriveGen[Rfc1918ClassCPrivate] =
    DeriveGen.instance(rfc1918ClassCPrivateGen)

  /**
   * The implicit `DeriveGen` for `Rfc1918Private`; it wraps
   * `rfc1918PrivateGen`.
   */
  implicit val rfc1918PrivateDeriveGen: DeriveGen[Rfc1918Private] =
    DeriveGen.instance(rfc1918PrivateGen)

  /**
   * The implicit `DeriveGen` for `Rfc5737Testnet1`; it wraps
   * `rfc5737Testnet1Gen`.
   */
  implicit val rfc5737Testnet1DeriveGen: DeriveGen[Rfc5737Testnet1] =
    DeriveGen.instance(rfc5737Testnet1Gen)

  /**
   * The implicit `DeriveGen` for `Rfc5737Testnet2`; it wraps
   * `rfc5737Testnet2Gen`.
   */
  implicit val rfc5737Testnet2DeriveGen: DeriveGen[Rfc5737Testnet2] =
    DeriveGen.instance(rfc5737Testnet2Gen)

  /**
   * The implicit `DeriveGen` for `Rfc5737Testnet3`; it wraps
   * `rfc5737Testnet3Gen`.
   */
  implicit val rfc5737Testnet3DeriveGen: DeriveGen[Rfc5737Testnet3] =
    DeriveGen.instance(rfc5737Testnet3Gen)

  /**
   * The implicit `DeriveGen` for `Rfc5737Testnet`; it wraps
   * `rfc5737TestnetGen`.
   */
  implicit val rfc5737TestnetDeriveGen: DeriveGen[Rfc5737Testnet] =
    DeriveGen.instance(rfc5737TestnetGen)

  /**
   * The implicit `DeriveGen` for `Rfc3927LocalLink`; it wraps
   * `rfc3927LocalLinkGen`.
   */
  implicit val rfc3927LocalLinkDeriveGen: DeriveGen[Rfc3927LocalLink] =
    DeriveGen.instance(rfc3927LocalLinkGen)

  /**
   * The implicit `DeriveGen` for `Rfc2544Benchmark`; it wraps
   * `rfc2544BenchmarkGen`.
   */
  implicit val rfc2544BenchmarkDeriveGen: DeriveGen[Rfc2544Benchmark] =
    DeriveGen.instance(rfc2544BenchmarkGen)

  /**
   * The implicit `DeriveGen` for `PrivateNetwork`; it wraps
   * `privateNetworkGen`.
   */
  implicit val privateNetworkDeriveGen: DeriveGen[PrivateNetwork] =
    DeriveGen.instance(privateNetworkGen)
}
