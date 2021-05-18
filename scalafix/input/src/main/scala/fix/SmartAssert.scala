/*
rule = SmartAssert
 */
package fix

import zio._
import zio.clock._
import zio.test._
import zio.test.Assertion._

object SmartAssert {
  val number = 0
  assert(number)(equalTo(0))

  val option = Some(10)
  assert(option)(isSome(equalTo(0)))

  val eitherOption = Right(Some(10))
  assert(eitherOption)(isRight(isSome(equalTo(0))))
}
