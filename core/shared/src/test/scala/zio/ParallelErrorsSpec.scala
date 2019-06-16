package zio

class ParallelErrorsSpec extends BaseCrossPlatformSpec {
  def is = "ParallelErrorsSpec".title ^ s2"""
   Returns a list of 2 errors $allFailures

  """

  def allFailures =
    for {
      f1     <- IO.fail("error1").fork
      f2     <- IO.fail("error2").fork
      errors <- f1.zip(f2).join.parallelErrors.flip
    } yield errors.length must_=== 2
}
