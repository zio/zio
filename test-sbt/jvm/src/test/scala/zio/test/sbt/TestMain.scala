package zio.test.sbt

object TestMain {
  def main(args: Array[String]): Unit =
    TestingSupport.run(ZTestFrameworkSbtSpec.tests: _*)
}
