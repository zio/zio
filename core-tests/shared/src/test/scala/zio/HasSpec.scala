package zio

import zio.test.Assertion._
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

  trait IList[+A]

  val dogs1: IList[Dog]      = new IList[Dog]   { override val toString = "dogs1"    }
  val dogs2: IList[Dog]      = new IList[Dog]   { override val toString = "dogs2"    }
  val cats1: IList[Cat]      = new IList[Cat]   { override val toString = "cats1"    }
  val cats2: IList[Cat]      = new IList[Cat]   { override val toString = "cats2"    }
  val bunnies1: IList[Bunny] = new IList[Bunny] { override val toString = "animals1" }

  trait PetHotel[-A]

  val animalHotel1: PetHotel[Animal] = new PetHotel[Animal] { override val toString = "animalHotel1" }
  val dogHotel1: PetHotel[Dog]       = new PetHotel[Dog]    { override val toString = "dogHotel1"    }
  val dogHotel2: PetHotel[Dog]       = new PetHotel[Dog]    { override val toString = "dogHotel2"    }
  val catHotel1: PetHotel[Cat]       = new PetHotel[Cat]    { override val toString = "catHotel1"    }
  val catHotel2: PetHotel[Cat]       = new PetHotel[Cat]    { override val toString = "catHotel2"    }
  val bunnyHotel1: PetHotel[Bunny]   = new PetHotel[Bunny]  { override val toString = "bunnyHotel1"  }

  def spec = suite("HasSpec")(
    suite("monomorphic types")(
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
      zio.test.test("Prune will delete what is not known about") {
        val whole: Has[Dog] with Has[Cat] = Has(dog1).add(cat1)

        assert(whole.size)(equalTo(2)) &&
        assert((whole: Has[Dog]).prune.size)(equalTo(1)) &&
        assert((whole: Has[Cat]).prune.size)(equalTo(1))
      },
      zio.test.test("Union will prune what is not known about on RHS") {
        val unioned = Has(dog1) union ((Has(dog2).add(bunny1)): Has[Bunny])

        assert(unioned.get[Dog])(equalTo(dog1)) &&
        assert(unioned.size)(equalTo(2))
      }
    ),
    suite("covariant types")(
      zio.test.test("Modules sharing common parent are independent") {
        val hasBoth = Has(dogs1).add[IList[Cat]](cats1)

        val dogs = hasBoth.get[IList[Dog]]
        val cats = hasBoth.get[IList[Cat]]

        assert(dogs)(anything) && assert(cats)(anything)
      },
      zio.test.test("Siblings can be updated independently") {
        val whole: Has[IList[Dog]] with Has[IList[Cat]] = Has(dogs1).add(cats1)

        val updated: Has[IList[Dog]] with Has[IList[Cat]] =
          whole.update[IList[Dog]](_ => dogs2).update[IList[Cat]](_ => cats2)

        assert(updated.size)(equalTo(2)) &&
        assert(updated.get[IList[Dog]])(equalTo(dogs2)) &&
        assert(updated.get[IList[Cat]])(equalTo(cats2))
      },
      zio.test.test("Prune will delete what is not known about") {
        val whole: Has[IList[Dog]] with Has[IList[Cat]] = Has(dogs1).add(cats1)

        assert(whole.size)(equalTo(2)) &&
        assert((whole: Has[IList[Dog]]).prune.size)(equalTo(1)) &&
        assert((whole: Has[IList[Cat]]).prune.size)(equalTo(1))
      },
      zio.test.test("Union will prune what is not known about on RHS") {
        val unioned = Has(dogs1) union ((Has(dogs2).add(bunnies1)): Has[IList[Bunny]])

        assert(unioned.get[IList[Dog]])(equalTo(dogs1)) &&
        assert(unioned.size)(equalTo(2))
      }
    ),
    suite("contravariant types")(
      zio.test.test("Modules sharing common parent are independent") {
        val hasBoth = Has(dogHotel1).add[PetHotel[Cat]](catHotel1)

        val dogHotel = hasBoth.get[PetHotel[Dog]]
        val catHotel = hasBoth.get[PetHotel[Cat]]

        assert(dogHotel)(anything) && assert(catHotel)(anything)
      },
      zio.test.test("Siblings can be updated independently") {
        val whole: Has[PetHotel[Dog]] with Has[PetHotel[Cat]] = Has(dogHotel1).add(catHotel1)

        val updated: Has[PetHotel[Dog]] with Has[PetHotel[Cat]] =
          whole.update[PetHotel[Dog]](_ => dogHotel2).update[PetHotel[Cat]](_ => catHotel2)

        assert(updated.size)(equalTo(2)) &&
        assert(updated.get[PetHotel[Dog]])(equalTo(dogHotel2)) &&
        assert(updated.get[PetHotel[Cat]])(equalTo(catHotel2))
      },
      zio.test.test("Prune will delete what is not known about") {
        val whole: Has[PetHotel[Dog]] with Has[PetHotel[Cat]] = Has(dogHotel1).add(catHotel1)

        assert(whole.size)(equalTo(2)) &&
        assert((whole: Has[PetHotel[Dog]]).prune.size)(equalTo(1)) &&
        assert((whole: Has[PetHotel[Cat]]).prune.size)(equalTo(1))
      },
      zio.test.test("Union will prune what is not known about on RHS") {
        val unioned = Has(dogHotel1) union ((Has(dogHotel2).add(bunnyHotel1)): Has[PetHotel[Bunny]])

        assert(unioned.get[PetHotel[Dog]])(equalTo(dogHotel1)) &&
        assert(unioned.size)(equalTo(2))
      }
    )
  )
}
