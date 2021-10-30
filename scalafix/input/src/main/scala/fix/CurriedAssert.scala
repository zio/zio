/*
rule = CurriedAssert
 */
package fix

import zio._
import zio.clock._
import zio.test._
import zio.test.Assertion._

object CurriedAssert {
  assertM(nanoTime, equalTo(0))
  assert(Right(Some(3)), isRight(isSome(isGreaterThan(4))))
}
