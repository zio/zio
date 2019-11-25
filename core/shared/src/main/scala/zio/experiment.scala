package zio

object experiment {

  trait ZService[S] {
    def decorated[R, E, A](f: S => S)(zio: ZIO[R, E, A]): ZIO[R, E, A]
  }

  trait Logger {
    val logger: Logger.Service
  }

  object Logger {

    trait Service extends ZService[Record] {
      def log(line: String): UIO[Unit]
    }

    object Service {

      final def make: UIO[Service] =
        FiberRef.make(Record(line => UIO(println(line)))).map { fiberRef =>
          new Service {
            def log(line: String): UIO[Unit] =
              fiberRef.get.flatMap(record => record.log(line))
            def decorated[R, E, A](f: Record => Record)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
              fiberRef.get.flatMap(record => fiberRef.locally(f(record))(zio))
          }
        }
    }

    final def log(line: String): ZIO[Logger, Nothing, Unit] =
      ZIO.accessM(_.logger.log(line))

    final def make: UIO[Logger] =
      Service.make.map { service =>
        new Logger {
          val logger = service
        }
      }

    final case class Record(log: String => UIO[Unit])
  }

  def decorated[R]: DecoratedPartiallyApplied[R] = new DecoratedPartiallyApplied[R]

  class DecoratedPartiallyApplied[R] {
    def apply[R1 <: R, E, S, A](proj: R => ZService[S])(f: S => S)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
      ZIO.environment[R].flatMap(r => proj(r).decorated(f)(zio))
  }
}

object Example extends App {
  import experiment._
  import experiment.Logger._

  def logOutput[R, E, A](zio: ZIO[R, E, A]): ZIO[R with Logger, E, A] =
    for {
      a <- zio
      _ <- Logger.log(a.toString)
    } yield a

  def logOutputWarning[R, E, A](zio: ZIO[R, E, A]): ZIO[R with Logger, E, A] =
    decorated[Logger](_.logger)(record => Record(line => record.log(Console.YELLOW + line + Console.RESET)))(
      logOutput(zio)
    )

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    myAppLogic.provideM(Logger.make).as(0)

  val myAppLogic =
    for {
      _ <- logOutput(ZIO.succeed("All clear."))
      _ <- logOutputWarning(ZIO.succeed("Missiles detected!"))
      _ <- logOutput(ZIO.succeed("False alarm."))
    } yield ()

}
