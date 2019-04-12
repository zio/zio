package scalaz.zio.internal

import org.specs2.Specification

class StackBoolSpec extends Specification {
  def is =
    "StackBoolSpec".title ^ s2"""
        Size tracking                 $e0
        From/to list small identity   $e1
        From/to list large identity   $e2
        Small push/pop example        $e3
        Large push/pop example        $e4
        Peek/pop identity             $e5
    """

  def e0 = {
    val list = List.fill(100)(true)

    StackBool(list: _*).size must_== list.length
  }

  def e1 = {
    val list = (1 to 200).map(_ % 2 == 0).toList

    StackBool(list: _*).toList must_=== list
  }

  def e2 = {
    val list = (1 to 400).map(_ % 2 == 0).toList

    StackBool(list: _*).toList must_=== list
  }

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

  def e4 = {
    val stack = StackBool()

    val list = (1 to 400).map(_ % 2 == 0).toList

    list.foreach(stack.push(_))

    list.reverse.foldLeft(true must_=== true) {
      case (result, flag) =>
        result and (stack.popOrElse(!flag) must_=== flag)
    }
  }

  def e5 = {
    val stack = StackBool()

    val list = (1 to 400).map(_ % 2 == 0).toList

    list.foreach(stack.push(_))

    list.reverse.foldLeft(true must_=== true) {
      case (result, flag) =>
        val peeked = stack.peekOrElse(!flag)
        val popped = stack.popOrElse(!flag)

        result and (peeked must_=== popped)
    }
  }
}
