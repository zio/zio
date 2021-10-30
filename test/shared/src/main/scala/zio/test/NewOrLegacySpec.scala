package zio.test

sealed trait NewOrLegacySpec

case class NewSpecWrapper(ZIOSpec: ZIOSpecAbstract)                      extends NewOrLegacySpec
case class LegacySpecWrapper(abstractRunnableSpec: AbstractRunnableSpec) extends NewOrLegacySpec
