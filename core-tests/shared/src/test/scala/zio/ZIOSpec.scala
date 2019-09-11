package zio

class ZIOSpec extends BaseCrossPlatformSpec {
  def is = "ZIOSpec".title ^ s2"""
    ZIO.parallelErrors
      Returns a list of 2 errors $allFailures
      Returns a list of 1 error $oneFailure

  """

  def allFailures =
    for {
      f1     <- IO.fail("error1").fork
      f2     <- IO.fail("error2").fork
      errors <- f1.zip(f2).join.parallelErrors[String].flip
    } yield errors must_=== ::("error1", List("error2"))

  def oneFailure =
    for {
      f1     <- IO.fail("error1").fork
      f2     <- IO.succeed("success1").fork
      errors <- f1.zip(f2).join.parallelErrors[String].flip
    } yield errors must_=== ::("error1", List())
}
