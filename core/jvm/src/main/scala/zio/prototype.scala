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

    def modify[A, B](lens: Lens[Environment, A])(f: A => (B, A)): UIO[B]

    final def get[A](lens: Lens[Environment, A]): UIO[A] =
      modify(lens)(a => (a, a))

    final def set[A](lens: Lens[Environment, A])(a: A): UIO[Unit] =
      modify(lens)(_ => ((), a))

    final def update[A](lens: Lens[Environment, A])(f: A => A): UIO[Unit] =
      modify(lens)(a => ((), f(a)))
  }

  trait Logging {
    def logging: Logging.Service
  }

  object Logging {

    trait Service {
      def log(line: String): UIO[Unit]
    }

    def fromConsole(console: Console): Logging =
      new Logging {
        override val logging: Service = new Service {
          def log(line: String): UIO[Unit] =
            console.console.putStrLn(line)
        }
      }
  }

  trait LoggingEffect extends EnvironmentalEffect {
    def logging: Lens[Environment, Logging.Service]
  }

  object LoggingEffect {

    trait Live extends LoggingEffect {

      type Environment = ZEnv with Logging

      val environment: Ref[ZEnv with Logging]

      def logging: Lens[Environment, Logging.Service] =
        (
          _.logging,
          logging0 =>
            env =>
              new Clock with Console with Random with System with Blocking with Logging {
                val clock    = env.clock
                val console  = env.console
                val random   = env.random
                val system   = env.system
                val blocking = env.blocking
                val logging  = logging0
              }
        )

      def modify[A, B](lens: Lens[Environment, A])(f: A => (B, A)): UIO[B] =
        environment.modify { old =>
          val a0     = lens._1(old)
          val (b, a) = f(a0)
          val env    = lens._2(a)(old)
          (b, env)
        }
    }

    object Live {
      val make: ZIO[ZEnv, Nothing, LoggingEffect] =
        ZIO.accessM[ZEnv] { old =>
          val env: ZEnv with Logging =
            new Clock with Console with Random with System with Blocking with Logging {
              val clock    = old.clock
              val console  = old.console
              val random   = old.random
              val system   = old.system
              val blocking = old.blocking
              val logging  = Logging.fromConsole(old).logging
            }
          Ref.make(env).map { ref =>
            new Live {
              val environment = ref
            }
          }
        }
    }

    def log(line: String): ZIO[LoggingEffect, Nothing, Unit] =
      ZIO.accessM[LoggingEffect] { logging =>
        logging.get(logging.logging).flatMap[Any, Nothing, Unit](_.log(line))
      }

    def addPrefix(prefix: String): ZIO[LoggingEffect, Nothing, Unit] =
      ZIO.accessM[LoggingEffect] { logging =>
        logging.update(logging.logging) { service =>
          new Logging.Service {
            def log(line: String): UIO[Unit] = service.log(prefix + line)
          }
        }
      }
  }
}

object Example extends App {
  import prototype._

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    myAppLogic.provideSomeM(LoggingEffect.Live.make).as(0)

  val myAppLogic =
    for {
      _ <- LoggingEffect.log("All clear.")
      _ <- LoggingEffect.addPrefix("Warning: ")
      _ <- LoggingEffect.log("Missiles detected!")
    } yield ()
}
