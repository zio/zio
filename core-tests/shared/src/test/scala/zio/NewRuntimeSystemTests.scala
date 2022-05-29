package zio

object NewRuntimeSystemTests {
  implicit class RunSyntax[A](
    task: Task[A]
  ) {
    def run(): A = Runtime.default.unsafeRun(task)
  }

  def test(name: String)(task: Task[Any]): Unit = {
    print(s"$name...")
    try {
      task.run()

      println("OK")
    } catch {
      case e: AssertionError =>
        println("FAILED")
        e.printStackTrace()
    }
  }

  def helloWorld() = test("Hello World") {
    for {
      _ <- Console.printLine("Hello World!")
    } yield assert(true)
  }

  def main(args: Array[String]): Unit =
    helloWorld()
}
