package zio

import zio.test.Assertion._
import zio.test.TestAspect.{ exceptDotty, jvmOnly }
import zio.test._

object HasSpec extends ZIOBaseSpec {
  trait Animal
  trait Dog   extends Animal
  trait Cat   extends Animal
  trait Bunny extends Animal

  val dog1: Dog     = new Dog   { override val toString = "dog1"   }
  val dog2: Dog     = new Dog   { override val toString = "dog2"   }
  val cat1: Cat     = new Cat   { override val toString = "cat1"   }
  val cat2: Cat     = new Cat   { override val toString = "cat2"   }
  val bunny1: Bunny = new Bunny { override val toString = "bunny1" }

  def spec = suite("HasSpec")(
    zio.test.test("Access topmost supertype") {
      val hasDog: Has[Dog] = Has(dog1)

      assert(hasDog.get[Any])(anything) &&
      assert(hasDog.get[AnyRef])(anything)
    },
    zio.test.test("Access any supertype") {
      val hasDog: Has[Dog] = Has(dog1)

      assert(hasDog.get[Animal])(equalTo(dog1))
    },
    zio.test.test("Modules sharing common parent are independent") {
      val hasBoth = Has(dog1).add[Cat](cat1)

      val dog = hasBoth.get[Dog]
      val cat = hasBoth.get[Cat]

      assert(dog)(anything) && assert(cat)(anything)
    },
    zio.test.test("Siblings can be updated independently") {
      val whole: Has[Dog] with Has[Cat] = Has(dog1).add(cat1)

      val updated: Has[Dog] with Has[Cat] = whole.update[Dog](_ => dog2).update[Cat](_ => cat2)

      assert(updated.size)(equalTo(2)) &&
      assert(updated.get[Dog])(equalTo(dog2)) &&
      assert(updated.get[Cat])(equalTo(cat2))
    },
    zio.test.test("Upcast will delete what is not known about") {
      val whole: Has[Dog] with Has[Cat] = Has(dog1).add(cat1)

      assert(whole.size)(equalTo(2)) &&
      assert(whole.upcast[Dog].size)(equalTo(1)) &&
      assert(whole.upcast[Cat].size)(equalTo(1))
    },
    zio.test.test("Prune will delete what is not known about") {
      val whole: Has[Dog] with Has[Cat] = Has(dog1).add(cat1)

      assert(whole.size)(equalTo(2)) &&
      assert((whole: Has[Dog]).prune.size)(equalTo(1)) &&
      assert((whole: Has[Cat]).prune.size)(equalTo(1))
    } @@ exceptDotty @@ jvmOnly,
    zio.test.test("Union will prune what is not known about on RHS") {
      val unioned = Has(dog1) union ((Has(dog2).add(bunny1)): Has[Bunny])

      assert(unioned.get[Dog])(equalTo(dog1)) &&
      assert(unioned.size)(equalTo(2))
    } @@ exceptDotty @@ jvmOnly
  )
}
