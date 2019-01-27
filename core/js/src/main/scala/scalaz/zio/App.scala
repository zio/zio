package scalaz.zio

trait App {

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program.
   */
  def run(args: List[String]): UIO[Unit]
}
