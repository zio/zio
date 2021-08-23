package zio

/**
 * The entry point for a purely-functional application on the JVM.
 *
 * {{{
 * import zio.ZIOApp
 * import zio.console._
 *
 * object MyApp extends ZIOApp {
 *
 *   final def run =
 *     for {
 *       _    <- putStrLn("Hello! What is your name?")
 *       name <- getStrLn
 *       _    <- putStrLn("Hello, " + name + "! Fancy seeing you here.")
 *     } yield ()
 * }
 * }}}
 */
trait ZIOApp extends App {
  def run: ZIO[ZEnv, Any, Any]

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    run.exitCode
}
