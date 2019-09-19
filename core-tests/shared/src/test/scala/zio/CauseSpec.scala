package zio

import org.specs2.{ ScalaCheck, Specification }

class CauseSpec extends Specification with ScalaCheck {
  import Cause._
  import ArbitraryCause._

  def is = "CauseSpec".title ^ s2"""
    Cause
      `Cause#died` and `Cause#stripFailures` are consistent $e1
      `Cause.equals` is symmetric $e2
      `Cause.equals` and `Cause.hashCode` satisfy the contract $e3
      `Cause#untraced` removes all traces $e8
    Then 
      `Then.equals` satisfies associativity $e4
      `Then.equals` satisfies distributivity $e5
    Both
      `Both.equals` satisfies associativity $e6
      `Both.equals` satisfies commutativity $e7
    Meta
      `Meta` is excluded from equals $e9
      `Meta` is excluded from hashCode $e10
      """

  private def e1 = prop { c: Cause[String] =>
    if (c.died) c.stripFailures must beSome
    else c.stripFailures must beNone
  }

  private def e2 = prop { (a: Cause[String], b: Cause[String]) =>
    (a == b) must_== (b == a)
  }

  private def e3 =
    prop { (a: Cause[String], b: Cause[String]) =>
      (a == b) ==> (a.hashCode must_== (b.hashCode))
    }.set(minTestsOk = 10, maxDiscardRatio = 99.0f)

  private def e4 = prop { (a: Cause[String], b: Cause[String], c: Cause[String]) =>
    (Then(Then(a, b), c) must_== Then(a, Then(b, c))) &&
    (Then(a, Then(b, c)) must_== Then(Then(a, b), c))
  }

  private def e5 = prop { (a: Cause[String], b: Cause[String], c: Cause[String]) =>
    (Then(a, Both(b, c)) must_== Both(Then(a, b), Then(a, c))) &&
    (Then(Both(a, b), c) must_== Both(Then(a, c), Then(b, c)))
  }

  private def e6 = prop { (a: Cause[String], b: Cause[String], c: Cause[String]) =>
    (Both(Both(a, b), c) must_== Both(a, Both(b, c))) &&
    (Both(Both(a, b), c) must_== Both(a, Both(b, c)))
  }

  private def e7 = prop { (a: Cause[String], b: Cause[String]) =>
    Both(a, b) must_== Both(b, a)
  }

  private def e8 = prop { (c: Cause[String]) =>
    c.untraced.traces.headOption must beNone
  }

  private def e9 = prop { (c: Cause[String]) =>
    (Cause.stackless(c) must_== c) &&
    (c must_== Cause.stackless(c))
  }

  private def e10 = prop { (c: Cause[String]) =>
    Cause.stackless(c).hashCode must_== c.hashCode
  }
}
