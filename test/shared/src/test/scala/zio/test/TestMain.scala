package zio.test

import zio.test.mock._

object TestMain {

  def main(args: Array[String]): Unit = {
    ClockSpec.run()
    ConsoleSpec.run()
    DefaultTestReporterSpec.run()
    EnvironmentSpec.run()
    RandomSpec.run()
    SchedulerSpec.run()
    SystemSpec.run()
    PredicateSpec.run()
  }
}
