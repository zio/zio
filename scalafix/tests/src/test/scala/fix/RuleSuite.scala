package fix

import scalafix.testkit._
import org.scalatest.FunSuiteLike

class RuleSuite extends AbstractSemanticRuleSuite with FunSuiteLike {
  runAllTests()
}
