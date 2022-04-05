package zio.test.sbt

import zio.test.ZIOSpecAbstract

// TODO rm once RunnableSpec is gone
sealed trait NewOrLegacySpec

case class NewSpecWrapper(ZIOSpec: ZIOSpecAbstract)                      extends NewOrLegacySpec
