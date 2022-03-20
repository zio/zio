package zio.test.sbt

import com.github.ghik.silencer.silent
import zio.test.{AbstractRunnableSpec, ZIOSpecAbstract}

sealed trait NewOrLegacySpec

case class NewSpecWrapper(ZIOSpec: ZIOSpecAbstract) extends NewOrLegacySpec
@silent("deprecated")
case class LegacySpecWrapper(abstractRunnableSpec: AbstractRunnableSpec) extends NewOrLegacySpec
