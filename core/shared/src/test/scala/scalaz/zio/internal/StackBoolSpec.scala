package scalaz.zio.internal

import org.scalacheck.{ Arbitrary, Gen }
import org.specs2.{ ScalaCheck, Specification }

class StackBoolSpec extends Specification with ScalaCheck {
  def is =
    "StackBoolSpec".title ^ s2"""
        Size tracking                 $e0
        From/to list small identity   $e1
        From/to list large identity   $e2
        Small push/pop example        $e3
        Large push/pop example        $e4
        Peek/pop identity             $e5
    """

  implicit val booleanGen: Gen[Boolean] = Arbitrary.arbitrary[Boolean]

  def e0 =
    prop { list: List[Boolean] =>
      StackBool(list: _*).size must_== list.length
    }.setGen(for {
      size <- Gen.choose(0, 100)
      g    <- Gen.listOfN(size, booleanGen)
    } yield g)

  def e1 =
    prop { list: List[Boolean] =>
      StackBool(list: _*).toList must_=== list
    }.setGen(for {
      size <- Gen.choose(0, 32)
      g    <- Gen.listOfN(size, booleanGen)
    } yield g)

  def e2 =
    prop { list: List[Boolean] =>
      StackBool(list: _*).toList must_=== list
    }.setGen(for {
      size <- Gen.choose(0, 400)
      g    <- Gen.listOfN(size, booleanGen)
    } yield g)

  def e3 = {
    val stack = StackBool()

    stack.push(true)
    stack.push(true)
    stack.push(false)

    val v1 = stack.popOrElse(true)
    val v2 = stack.popOrElse(false)
    val v3 = stack.popOrElse(false)

    (v1 must_=== false) and
      (v2 must_=== true) and
      (v3 must_=== true)
  }

  def e4 =
    prop { list: List[Boolean] =>
      val stack = StackBool()

      list.foreach(stack.push(_))

      list.reverse.foldLeft(true must_=== true) {
        case (result, flag) =>
          result and (stack.popOrElse(!flag) must_=== flag)
      }
    }.setGen(for {
      size <- Gen.choose(100, 400)
      g    <- Gen.listOfN(size, booleanGen)
    } yield g)

  def e5 =
    prop { list: List[Boolean] =>
      val stack = StackBool()

      list.foreach(stack.push(_))

      list.reverse.foldLeft(true must_=== true) {
        case (result, flag) =>
          val peeked = stack.peekOrElse(!flag)
          val popped = stack.popOrElse(!flag)

          result and (peeked must_=== popped)
      }
    }.setGen(for {
      size <- Gen.choose(100, 400)
      g    <- Gen.listOfN(size, booleanGen)
    } yield g)
}
