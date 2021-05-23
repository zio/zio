package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.net._
import zio.random.Random
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object net extends NetInstances

trait NetInstances {
  private val _0to255 = Gen.int(0, 255)

  val portNumberGen: Gen[Random, PortNumber]                   = Gen.int(0, 656535).map(Refined.unsafeApply)
  val systemPortNumberGen: Gen[Random, SystemPortNumber]       = Gen.int(0, 1023).map(Refined.unsafeApply)
  val userPortNumberGen: Gen[Random, UserPortNumber]           = Gen.int(1024, 49151).map(Refined.unsafeApply)
  val dynamicPortNumberGen: Gen[Random, DynamicPortNumber]     = Gen.int(49152, 65535).map(Refined.unsafeApply)
  val nonSystemPortNumberGen: Gen[Random, NonSystemPortNumber] = Gen.int(1024, 65535).map(Refined.unsafeApply)
  val rfc1918ClassAPrivateGen: Gen[Random, Rfc1918ClassAPrivate] =
    Gen.zipN(_0to255, _0to255, _0to255)((a, b, c) => Refined.unsafeApply(s"10.$a.$b.$c"))
  val rfc1918ClassBPrivateGen: Gen[Random, Rfc1918ClassBPrivate] =
    Gen.zipN(Gen.int(16, 31), _0to255, _0to255)((a, b, c) => Refined.unsafeApply(s"172.$a.$b.$c"))
  val rfc1918ClassCPrivateGen: Gen[Random, Rfc1918ClassCPrivate] =
    Gen.zipN(_0to255, _0to255)((a, b) => Refined.unsafeApply(s"192.168.$a.$b"))
  val rfc1918PrivateGen: Gen[Random, Rfc1918Private] =
    Gen
      .oneOf(rfc1918ClassAPrivateGen, rfc1918ClassBPrivateGen, rfc1918ClassCPrivateGen)
      .map(v => Refined.unsafeApply(v.value))
  val rfc5737Testnet1Gen: Gen[Random, Rfc5737Testnet1] = _0to255.map(v => Refined.unsafeApply(s"192.0.2.$v"))
  val rfc5737Testnet2Gen: Gen[Random, Rfc5737Testnet2] = _0to255.map(v => Refined.unsafeApply(s"198.51.100.$v"))
  val rfc5737Testnet3Gen: Gen[Random, Rfc5737Testnet3] = _0to255.map(v => Refined.unsafeApply(s"203.0.113.$v"))
  val rfc5737TestnetGen: Gen[Random, Rfc5737Testnet] = Gen
    .oneOf(rfc5737Testnet1Gen, rfc5737Testnet2Gen, rfc5737Testnet3Gen)
    .map(v => Refined.unsafeApply(v.value))
  val rfc3927LocalLinkGen: Gen[Random, Rfc3927LocalLink] =
    Gen.zipN(_0to255, _0to255)((a, b) => Refined.unsafeApply(s"169.254.$a.$b"))
  val rfc2544BenchmarkGen: Gen[Random, Rfc2544Benchmark] =
    Gen.zipN(Gen.int(18, 19), _0to255, _0to255)((a, b, c) => Refined.unsafeApply(s"198.$a.$b.$c"))
  val privateNetworkGen: Gen[Random, PrivateNetwork] =
    Gen
      .oneOf(rfc1918PrivateGen, rfc5737TestnetGen, rfc3927LocalLinkGen, rfc2544BenchmarkGen)
      .map(v => Refined.unsafeApply(v.value))

  implicit val portNumberDeriveGen: DeriveGen[PortNumber]                   = DeriveGen.instance(portNumberGen)
  implicit val systemPortNumberDeriveGen: DeriveGen[SystemPortNumber]       = DeriveGen.instance(systemPortNumberGen)
  implicit val userPortNumberDeriveGen: DeriveGen[UserPortNumber]           = DeriveGen.instance(userPortNumberGen)
  implicit val dynamicPortNumberDeriveGen: DeriveGen[DynamicPortNumber]     = DeriveGen.instance(dynamicPortNumberGen)
  implicit val nonSystemPortNumberDeriveGen: DeriveGen[NonSystemPortNumber] = DeriveGen.instance(nonSystemPortNumberGen)
  implicit val rfc1918ClassAPrivateDeriveGen: DeriveGen[Rfc1918ClassAPrivate] =
    DeriveGen.instance(rfc1918ClassAPrivateGen)
  implicit val rfc1918ClassBPrivateDeriveGen: DeriveGen[Rfc1918ClassBPrivate] =
    DeriveGen.instance(rfc1918ClassBPrivateGen)
  implicit val rfc1918ClassCPrivateDeriveGen: DeriveGen[Rfc1918ClassCPrivate] =
    DeriveGen.instance(rfc1918ClassCPrivateGen)
  implicit val rfc1918PrivateDeriveGen: DeriveGen[Rfc1918Private] =
    DeriveGen.instance(rfc1918PrivateGen)
  implicit val rfc5737Testnet1DeriveGen: DeriveGen[Rfc5737Testnet1] =
    DeriveGen.instance(rfc5737Testnet1Gen)
  implicit val rfc5737Testnet2DeriveGen: DeriveGen[Rfc5737Testnet2] =
    DeriveGen.instance(rfc5737Testnet2Gen)
  implicit val rfc5737Testnet3DeriveGen: DeriveGen[Rfc5737Testnet3] =
    DeriveGen.instance(rfc5737Testnet3Gen)
  implicit val rfc5737TestnetDeriveGen: DeriveGen[Rfc5737Testnet] =
    DeriveGen.instance(rfc5737TestnetGen)
  implicit val rfc3927LocalLinkDeriveGen: DeriveGen[Rfc3927LocalLink] =
    DeriveGen.instance(rfc3927LocalLinkGen)
  implicit val rfc2544BenchmarkDeriveGen: DeriveGen[Rfc2544Benchmark] =
    DeriveGen.instance(rfc2544BenchmarkGen)
  implicit val privateNetworkDeriveGen: DeriveGen[PrivateNetwork] =
    DeriveGen.instance(privateNetworkGen)
}
