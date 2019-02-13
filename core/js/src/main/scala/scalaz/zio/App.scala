package scalaz.zio

trait App extends RTS {

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program.
   */
  def run(args: List[String]): UIO[Unit]

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit =
    unsafeRunAsync(run(args0.toList))(_.getOrElse(_ => ()))
}
