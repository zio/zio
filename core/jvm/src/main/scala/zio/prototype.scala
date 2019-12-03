package zio

import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.system.System
import zio.random.Random

object prototype {

  type Lens[S, A] = (S => A, A => S => S)

  // All environmental effects extend this trait:
  trait EnvironmentalEffect[Environment] {

    def modify[A, B](lens: Lens[Environment, A])(f: A => (B, A)): UIO[B]

    final def update[A](lens: Lens[Environment, A])(f: A => A): UIO[Unit] =
      modify(lens)(a => ((), f(a)))

    final def use[A, B](lens: Lens[Environment, A])(f: A => B): UIO[B] =
      modify(lens)(a => (f(a), a))

    final def useM[A, B](lens: Lens[Environment, A])(f: A => UIO[B]): UIO[B] =
      modify(lens)(a => (f(a), a)).flatten
  }

  object EnvironmentalEffect {

    def modify[R <: EnvironmentalEffect[Environment], Environment, A, B](
      lens: Lens[Environment, A]
    )(f: A => (B, A)): ZIO[R, Nothing, B] =
      ZIO.accessM[R](_.modify(lens)(f))

    def update[R <: EnvironmentalEffect[Environment], Environment, A](
      lens: Lens[Environment, A]
    )(f: A => A): ZIO[R, Nothing, Unit] =
      ZIO.accessM[R](_.update(lens)(f))
  }

  trait Logging {
    def logging: Logging.Service
  }

  object Logging {
    trait Service {
      def log(line: String): UIO[Unit]
    }
  }

  trait LoggingEffect[R] extends EnvironmentalEffect[R] {
    def logging: Lens[R, Logging.Service]
  }

  object LoggingEffect {

    trait Live extends LoggingEffect[ZEnv with Logging] {

      val environment: Ref[ZEnv with Logging]

      def logging: Lens[ZEnv with Logging, Logging.Service] =
        (
          s => s.logging,
          a =>
            s =>
              new Clock with Console with System with Random with Blocking with Logging {
                val clock    = s.clock
                val console  = s.console
                val system   = s.system
                val random   = s.random
                val blocking = s.blocking
                val logging  = a
              }
        )

      def modify[A, B](lens: Lens[ZEnv with Logging, A])(f: A => (B, A)): UIO[B] =
        environment.get.flatMap { r =>
          val a       = lens._1(r)
          val (b, a1) = f(a)
          environment.set(lens._2(a1)(r)) *> ZIO.succeed(b)
        }
    }

    def make: ZIO[ZEnv, Nothing, LoggingEffect[ZEnv with Logging]] =
      ZIO.accessM[ZEnv] { r =>
        val env: ZEnv with Logging = new Clock with Console with System with Random with Blocking with Logging {
          val clock    = r.clock
          val console  = r.console
          val system   = r.system
          val random   = r.random
          val blocking = r.blocking
          val logging = new Logging.Service {
            def log(line: String): UIO[Unit] = console.putStrLn(line)
          }
        }
        Ref.make(env).map { ref =>
          new LoggingEffect.Live {
            val environment: Ref[ZEnv with Logging] = ref
          }
        }
      }

    def log(line: String): ZIO[LoggingEffect[ZEnv with Logging], Nothing, Unit] =
      ZIO.accessM[LoggingEffect[ZEnv with Logging]](service => service.useM(service.logging)(_.log(line)))

    def addPrefix[R <: Logging](prefix: String): ZIO[LoggingEffect[R], Nothing, Unit] =
      ZIO.accessM[LoggingEffect[R]] { logging =>
        EnvironmentalEffect.update(logging.logging)(
          service =>
            new Logging.Service {
              def log(line: String): UIO[Unit] = service.log(prefix + line)
            }
        )
      }
  }
}

object Example extends App {
  import prototype._

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    myAppLogic.provideSomeM(LoggingEffect.make).as(0)

  val myAppLogic =
    for {
      _ <- LoggingEffect.log("All clear.")
      _ <- LoggingEffect.addPrefix[ZEnv with Logging]("Warning: ")
      _ <- LoggingEffect.log("Missiles detected!")
    } yield ()
}
