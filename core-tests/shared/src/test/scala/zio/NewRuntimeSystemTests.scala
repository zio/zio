package zio

object NewRuntimeSystemTests {
  implicit class RunSyntax[A](
    task: Task[A]
  ) {
    def run(): A = Runtime.default.unsafeRun(task)
  }

  def test(name: String)(task: Task[Any]): Unit = {
    println("*****************************************")
    print(s"$name...")
    try {
      task.run()

      println("OK")
    } catch {
      case e: AssertionError =>
        println("FAILED")
        e.printStackTrace()
      case t: Throwable =>
        println("CATASTROPHIC")
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

  def race() =
    testN(100)("race") {
      ZIO.unit.race(ZIO.unit)
    }

  def main(args: Array[String]): Unit =
    helloWorld()
  // fib()
  // race()
}
