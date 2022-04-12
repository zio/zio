package zio

import zio.Experimental._
import zio.test._
import zio.test.Assertion._
import zio.Random
import zio.test.Gen._

import scala.annotation.experimental
import scala.language.experimental.saferExceptions

object InlineScopeTestSpec extends ZIOBaseSpec {

  def spec = 
    suite("Inline Scope Spec")(
        test("Inline scope shows errors") {
            assertTrue(true)
        }
    )
    
}