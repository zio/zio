package zio.test

import zio.test.Assertion._
import zio.test.mock._
import zio.{ Has, Ref, UIO, ZIO, ZLayer }

object SpySpec extends DefaultRunnableSpec {

  type Counter = Has[Counter.Service]

  object Counter {

    trait Service {
      def increment: UIO[Unit]
      def decrement: UIO[Unit]
      def get: UIO[Int]
      def reset: UIO[Unit]
    }

    final case class Live(counterState: Ref[Int]) extends Service {
      def increment: UIO[Unit] = counterState.update(_ + 1).unit
      def decrement: UIO[Unit] = counterState.update(_ - 1).unit
      def get: UIO[Int]        = counterState.get
      def reset: UIO[Unit]     = counterState.set(0)
    }

    val live: ZLayer.NoDeps[Nothing, Counter] =
      ZLayer.fromEffect(Ref.make(0).map(ref => Has(Live(ref))))
  }

  sealed trait Command[+A] extends Method[Counter.Service, Unit, A]

  object Command {
    case object Increment extends Command[Unit]
    case object Decrement extends Command[Unit]
    case object Get       extends Command[Int]
    case object Reset     extends Command[Unit]

    val commands = Gen.elements(Increment, Decrement, Get, Reset)
  }

  val spy: ZLayer[Counter, Nothing, Counter with Spy] =
    Spy.live[Int, Counter.Service](0) { (counter, state, result) =>
      new Counter.Service {
        def increment: UIO[Unit] =
          counter.increment *> state.update(_ + 1).unit
        def decrement: UIO[Unit] =
          counter.decrement *> state.update(_ - 1).unit
        def get: UIO[Int] =
          counter.get.flatMap { actual =>
            state.get.flatMap { expected =>
              result.set(assert(actual)(equalTo(expected))).as(actual)
            }
          }
        def reset: UIO[Unit] =
          counter.reset *> state.set(0)
      }
    }

  def spec = suite("SpySpec") {
    testM("counter example") {
      checkM((Gen.listOf(Command.commands))) { commands =>
        for {
          counter <- (Counter.live >>> spy).build.use(ZIO.succeed)
          _ <- ZIO.foreach(commands) {
                case Command.Increment => counter.get.increment
                case Command.Decrement => counter.get.decrement
                case Command.Get       => counter.get.get
                case Command.Reset     => counter.get.reset
              }
          result <- counter.get[Spy.Service].spy
        } yield result
      }
    }
  }
}
