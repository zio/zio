package fix

import zio._
import zio.clock._
import zio.test._
import zio.test.Assertion._

object SmartAssert {
  val number = 0
  assert(number == 0)

  val option = Some(10)
  assert(option.get == 0)

  val eitherOption = Right(Some(10))
  assert(eitherOption.$asRight.get == 0)
}
