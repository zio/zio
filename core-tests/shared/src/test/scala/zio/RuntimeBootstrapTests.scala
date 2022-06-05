package zio

object RuntimeBootstrapTests {
  implicit class RunSyntax[A](
    task: Task[A]
  ) {
    def run(): A = Runtime.default.unsafeRun(task)
  }

  def test(name: String)(task: Task[Any]): Unit = {
    print(s" - $name...")
    try {
      task.run()

      println("...OK")
    } catch {
      case e: java.lang.AssertionError =>
        println("...FAILED")
        e.printStackTrace()
      case t: Throwable =>
        println("...CATASTROPHIC")
        t.printStackTrace()
    }
  }

  def testN(n: Int)(name: String)(task: Task[Any]): Unit =
    (1 to n).foreach(_ => test(name)(task))

  def helloWorld() =
    test("Hello World") {
      for {
        _ <- Console.print("Hello World!")
      } yield assert(true)
    }

  def fib() = {
    def fibAcc(n: Int): Task[Int] =
      if (n <= 1)
        ZIO.succeed(n)
      else
        for {
          a <- fibAcc(n - 1)
          b <- fibAcc(n - 2)
        } yield a + b

    test("fib") {
      for {
        result <- fibAcc(10)
      } yield assert(result == 55)
    }
  }

  def runtimeFlags() =
    test("runtimeFlags") {
      ZIO.succeed {
        import RuntimeFlag._

        val flags =
          RuntimeFlags(Interruption, CurrentFiber)

        assert(flags.enabled(Interruption))
        assert(flags.enabled(CurrentFiber))
        assert(flags.disabled(FiberRoots))
        assert(flags.disabled(OpLog))
        assert(flags.disabled(OpSupervision))
        assert(flags.disabled(RuntimeMetrics))

        assert(RuntimeFlags.enable(Interruption)(RuntimeFlags.none).interruption)
      }
    }

  def race() =
    testN(100)("race") {
      ZIO.unit.race(ZIO.unit)
    }

  def iteration() =
    test("iteration") {
      ZIO.iterate(0)(_ < 100)(index => ZIO.succeed(index + 1)).map(value => assert(value == 100))
    }

  def main(args: Array[String]): Unit = {
    runtimeFlags()
    helloWorld()
    fib()
    iteration()
    //race()
  }
}
