package zio.test.sbt

object TestMain {
  def main(args: Array[String]) =
    TestingSupport.run(ZTestFrameworkSpec.tests: _*)
}
