package zio

import zio.test._
import zio.test.Assertion._

object HasSpec extends ZIOBaseSpec {
  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def spec = suite("HasSpec")(
    zio.test.test("getting Any module from Has.Any always works") {
      assert(Has.any.get[Any])(anything)
    },
    zio.test.test("getting Animal from Dog works") {
      val hasDog: Has[Dog] = Has(new Dog {})

      assert(hasDog.get[Animal])(anything)
    },
    zio.test.test("getting Cat and Dog works") {
      val hasBoth = Has(new Dog {}).add[Cat](new Cat {})

      val dog = hasBoth.get[Dog]
      val cat = hasBoth.get[Cat]

      assert(dog)(anything) && assert(cat)(anything)
    }
  )
}
