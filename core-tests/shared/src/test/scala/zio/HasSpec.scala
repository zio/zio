package zio

import zio.test._
import zio.test.Assertion._
//import zio.test.TestAspect._

object HasSpec extends ZIOBaseSpec {
  trait Animal 
  trait Dog extends Animal 

  def spec = suite("HasSpec")(
    zio.test.test("getting Any module from Has.Any always works") {
      assert(Has.any.get[Any])(anything)
    },
    zio.test.test("getting Any module from Has.Any always works") {
      val hasDog: Has[Dog] = Has(new Dog {})

      assert(hasDog.get[Animal])(anything)
    },
  )
}