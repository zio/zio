package scalaz.zio.internal

import org.scalacheck.{ Arbitrary, Gen, Prop }
import org.specs2.{ ScalaCheck, Specification }

class StackBoolSpec extends Specification with ScalaCheck {
  def is =
    "StackBoolSpec".title ^ s2"""
        Size tracking                 $e0
        From/to list identity         $e1
        Push/pop example              $e2
        Peek/pop identity             $e3
    """

  import Arbitrary._

  private val generator: Gen[List[Boolean]] = boolListGen(0, 400)

  def e0 =
    Prop.forAll(generator) { list: List[Boolean] =>
      StackBool(list: _*).size must_== list.length
    }

  def e1 =
    Prop.forAll(generator) { list: List[Boolean] =>
      StackBool(list: _*).toList must_=== list
    }

  def e2 =
    Prop.forAll(generator) { list: List[Boolean] =>
      val stack = StackBool()

      list.foreach(stack.push(_))

      list.reverse.foldLeft(true must_=== true) {
        case (result, flag) =>
          result and (stack.popOrElse(!flag) must_=== flag)
      }
    }

  def e3 =
    Prop.forAll(generator) { list: List[Boolean] =>
      val stack = StackBool()

      list.foreach(stack.push(_))

      list.reverse.foldLeft(true must_=== true) {
        case (result, flag) =>
          val peeked = stack.peekOrElse(!flag)
          val popped = stack.popOrElse(!flag)

          result and (peeked must_=== popped)
      }
    }

  private def boolListGen(min: Int, max: Int) =
    for {
      size <- Gen.choose(min, max)
      g    <- Gen.listOfN(size, Arbitrary.arbitrary[Boolean])
    } yield g
}
