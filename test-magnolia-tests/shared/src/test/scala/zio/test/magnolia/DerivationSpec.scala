package zio.test.magnolia

import zio.random.Random
import zio.test.Sized

import zio.test._
import zio.test.GenUtils._
import zio.test.Assertion._

import zio.test.magnolia.Derivation._

object DerivationSpec extends DefaultRunnableSpec {

  final case class Person(name: String, age: Int)

  val genPerson: Gen[Random with Sized, Person] = Derivation.gen[Person]

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  val genColor: Gen[Random with Sized, Color] = Derivation.gen[Color]

  def spec = suite("DerivationSpec")(
    testM("case classes can be generated") {
      checkSample(genPerson)(isGreaterThan(1), _.distinct.length)
    },
    testM("sealed traits can be generated") {
      checkSample(genColor)(equalTo(3), _.distinct.length)
    },
    testM("shrinking works correctly") {
      checkShrink(genPerson)(Person("", 0))
    }
  )
}
