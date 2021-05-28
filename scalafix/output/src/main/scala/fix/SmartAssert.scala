package fix

import zio._
import zio.clock._
import zio.test._
import zio.test.Assertion._

object SmartAssert {
  val number = 0
  assertTrue(number == 0)

  val option = Some(10)
  assertTrue(option.get == 0)

  val eitherOption = Right(Some(10))
  assertTrue(eitherOption.$asRight.get == 0)
}
