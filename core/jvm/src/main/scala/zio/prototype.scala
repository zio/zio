package zio

import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.random.Random
import zio.system.System

object prototype {

  type Lens[S, A] = (S => A, A => S => S)

  trait EnvironmentalEffect {

    type Environment

    def locallyM[R, R1, E, A, B](lens: Lens[Environment, A])(f: A => ZIO[R, E, A])(
      zio: ZIO[R1, E, B]
    ): ZIO[R with R1, E, B]

    def use[R, E, A, B](lens: Lens[Environment, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B]

    final def locally[R, E, A, B](lens: Lens[Environment, A])(f: A => A)(zio: ZIO[R, E, B]): ZIO[R, E, B] =
      locallyM(lens)(f andThen ZIO.succeed)(zio)
  }

  trait Logging extends EnvironmentalEffect {
    def logging: Lens[Environment, Logging.Service]
  }

  object Logging {

    trait Service {
      def log(line: String): UIO[Unit]
    }

    object Service {
      final def fromConsole(console: Console.Service[Any]): Logging.Service =
        new Logging.Service {
          def log(line: String): UIO[Unit] =
            console.putStrLn(line)
        }
    }

    final def log(line: String): ZIO[Logging, Nothing, Unit] =
      ZIO.accessM[Logging] { logging =>
        logging.use[Any, Nothing, Logging.Service, Unit](logging.logging) { service =>
          service.log(line)
        }
      }

    final def addPrefix[R <: Logging, E, A](prefix: String)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
      ZIO.accessM[R] { logging =>
        logging.locally(logging.logging) { service =>
          new Logging.Service {
            def log(line: String): UIO[Unit] = service.log(prefix + line)
          }
        }(zio)
      }
  }

  object Environment {

    final case class Live(liveState: FiberRef[Data]) extends Logging {

      type Environment = Data

      final def locallyM[R, R1, E, A, B](
        lens: Lens[Environment, A]
      )(f: A => ZIO[R, E, A])(zio: ZIO[R1, E, B]): ZIO[R with R1, E, B] =
        for {
          data <- liveState.get
          a    <- f(lens._1(data))
          b    <- liveState.locally(lens._2(a)(data))(zio)
        } yield b

      final def logging: Lens[Environment, Logging.Service] =
        (s => s.logging, a => s => s.copy(logging = a))

      final def use[R, E, A, B](lens: Lens[Environment, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B] =
        liveState.get.flatMap(data => f(lens._1(data)))
    }

    object Live {

      final val make: ZIO[ZEnv, Nothing, Live] =
        ZIO.accessM[ZEnv] { env =>
          val data = Environment.Data(
            clock = env.clock,
            console = env.console,
            random = env.random,
            system = env.system,
            blocking = env.blocking,
            logging = Logging.Service.fromConsole(env.console)
          )
          FiberRef.make(data).map(Live(_))
        }
    }

    final case class Data(
      clock: Clock.Service[Any],
      console: Console.Service[Any],
      random: Random.Service[Any],
      system: System.Service[Any],
      blocking: Blocking.Service[Any],
      logging: Logging.Service
    )
  }
}

object Example extends App {
  import prototype._

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    myAppLogic.provideSomeM(Environment.Live.make).as(0)

  val myAppLogic =
    for {
      _ <- Logging.log("All clear.")
      _ <- Logging.addPrefix("Warning: ")(Logging.log("Missiles detected!"))
      _ <- Logging.log("Crisis averted.")
    } yield ()
}
