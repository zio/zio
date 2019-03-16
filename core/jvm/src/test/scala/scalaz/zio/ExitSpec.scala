package scalaz.zio

import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import Exit.Cause

class ExitSpec(implicit ee: ExecutionEnv) extends TestRuntime with ScalaCheck {
  import ArbitraryCause._

  def is = "ExitSpec".title ^ s2"""
    Cause
      `Cause#died` and `Cause#stripFailures` are consistent $e1
  """

  private def e1 = prop { c: Cause[String] =>
    if (c.died) c.stripFailures must beSome
    else c.stripFailures must beNone
  }
}
