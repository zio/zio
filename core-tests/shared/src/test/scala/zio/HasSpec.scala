package zio

import zio.test._
import zio.test.Assertion._

object HasSpec extends ZIOBaseSpec {
  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def spec = suite("HasSpec")(
    zio.test.test("Get from Has[Any] must succeed") {
      assert(Has.any.get[Any])(anything)
    },
    zio.test.test("Access any supertype") {
      val dog = new Dog {}

      val hasDog: Has[Dog] = Has(dog)

      assert(hasDog.get[Animal])(equalTo(dog))
    },
    zio.test.test("Modules sharing common parent are independent") {
      val hasBoth = Has(new Dog {}).add[Cat](new Cat {})

      val dog = hasBoth.get[Dog]
      val cat = hasBoth.get[Cat]

      assert(dog)(anything) && assert(cat)(anything)
    },
    zio.test.test("Subtype can replace supertype") {
      val hasAnimal: Has[Animal] = Has(new Animal {})

      val dog = new Dog {}

      val hasDog = hasAnimal.add(dog)

      assert(hasDog.size)(equalTo(1)) &&
      assert(hasDog.get[Animal])(equalTo(dog))
    },
    zio.test.test("Supertype cannot replace subtype") {
      val dog = new Dog {}

      val hasDog: Has[Dog]       = Has(dog)
      val hasAnimal: Has[Animal] = (hasDog: Has[Any]).add(new Animal {})

      assert(hasAnimal.size)(equalTo(1)) &&
      assert(hasAnimal.get[Animal])(equalTo(dog))
    },
    zio.test.test("Update of supertype has no effect") {
      val dog    = new Dog    {}
      val animal = new Animal {}

      val hasDog: Has[Dog]  = Has(dog)
      val updated: Has[Dog] = hasDog.update[Animal](_ => animal)

      assert(updated.size)(equalTo(1)) &&
      assert(updated.get[Animal])(equalTo(dog))
    },
    zio.test.test("Siblings can be updated independently") {
      val dog1: Dog = new Dog { override val toString = "dog1" }
      val dog2: Dog = new Dog { override val toString = "dog2" }
      val cat1: Cat = new Cat { override val toString = "cat1" }
      val cat2: Cat = new Cat { override val toString = "cat2" }

      val whole: Has[Dog] with Has[Cat] = Has(dog1).add(cat1)

      val updated: Has[Dog] with Has[Cat] = whole.update[Dog](_ => dog2).update[Cat](_ => cat2)

      assert(updated.size)(equalTo(2)) &&
      assert(updated.get[Dog])(equalTo(dog2)) &&
      assert(updated.get[Cat])(equalTo(cat2))
    }
  )
}
