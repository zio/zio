package zio

class ParallelErrorsSpec extends BaseCrossPlatformSpec {
  def is = "ParallelErrorsSpec".title ^ s2"""
   Returns a list of 2 errors $allFailures 
   Returns a list of 1 error out of 2 events $oneFailure
   Returns an emty list out of 2 events $noFailure

  """

  def allFailures =
    for {
      f1     <- IO.fail("error1").fork
      f2     <- IO.fail("error2").fork
      errors <- f1.zip(f2).join.parallelErrors[String].flip
    } yield errors must_=== ::("error1" , List("error2"))

  def oneFailure =
    for {
      f1     <- IO.fail("error1").fork
      f2     <- IO.succeed("success1").fork
      errors <- f1.zip(f2).join.parallelErrors[String].flip
    } yield errors must_=== ::("error1", List())

  def noFailure =
    for {
      f1     <- IO.succeed("success1").fork
      f2     <- IO.succeed("success2").fork
      errors <- f1.zip(f2).join.parallelErrors[String].flip
    } yield errors.length must_=== 0
}
