package scalaz.zio

import org.specs2.ScalaCheck

class RepeatSpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "RepeatSpec".title ^ s2"""
   Repeat on success according to a provided strategy
      for a negatime number of time does nothing $repeatNeg
      for 0 time does nothing $repeat0
      for 1 time does the action one time $repeat1
      for a given number of times $repeatN
    """

  val repeat: Int => IO[Nothing, Int] = (n: Int) => for {
      ref <- Ref(0)
      s   <- ref.update(_ + 1).repeat(Schedule.recurs(n))
    } yield s

  /*
   * A repeat with a negative number of times should not execute the action at all
   */
  def repeatNeg = {
    val repeated = unsafeRun(repeat(-5))
    repeated must_=== 0
  }

  /*
   * A repeat with 0 number of times should not execute the action at all
   */
  def repeat0 = {
    val n = 0
    val repeated = unsafeRun(repeat(n))
    repeated must_=== n
  }


  def repeat1 = {
    val n = 1
    val repeated = unsafeRun(repeat(n))
    repeated must_=== n
  }

  def repeatN = {
    val n = 42
    val repeated = unsafeRun(repeat(n))
    repeated must_=== n
  }
}
