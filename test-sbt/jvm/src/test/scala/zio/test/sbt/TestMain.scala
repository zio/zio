package zio.test.sbt

object TestMain {
  def main(args: Array[String]): Unit =
    TestingSupport.run(ZTestFrameworkSpec.tests: _*)
}
