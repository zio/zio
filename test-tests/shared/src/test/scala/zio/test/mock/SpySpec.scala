package zio.test.mock

import zio.test.Assertion._
import zio.test._
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

  sealed trait Command[+A] extends Method[Counter.Service, Any, A]

  object Command {
    case object Increment extends Command[Unit]
    case object Decrement extends Command[Unit]
    case object Get       extends Command[Int]
    case object Reset     extends Command[Unit]

    val commands = Gen.elements(Increment, Decrement, Get, Reset)
  }

  implicit val spyableCounter: Spyable[Counter.Service] =
    new Spyable[Counter.Service] {
      def environment(mock: Mock): Has[Counter.Service] =
        Has {
          new Counter.Service {
            def increment: UIO[Unit] = mock(Command.Increment)
            def decrement: UIO[Unit] = mock(Command.Decrement)
            def get: UIO[Int]        = mock(Command.Get)
            def reset: UIO[Unit]     = mock(Command.Reset)
          }
        }
      def mock(environment: Has[Counter.Service]): Mock =
        new Mock {
          def invoke[R0, E0, A0, M0, I0](method: Method[M0, I0, A0], input: I0): ZIO[R0, E0, A0] =
            method match {
              case Command.Increment => environment.get.increment
              case Command.Decrement => environment.get.decrement
              case Command.Get       => environment.get.get
              case Command.Reset     => environment.get.reset
            }
        }
    }

  val spy: ZLayer[Counter, Nothing, Counter with Spy] =
    Spy.live[Int, Counter.Service](0) {
      case (state, result, Invocation(method, _, output)) =>
        method match {
          case Command.Increment => state.update(_ + 1).unit
          case Command.Decrement => state.update(_ - 1).unit
          case Command.Get =>
            state.get.flatMap { expected =>
              result.update(_ && assert(output)(equalTo(expected))).unit
            }
          case Command.Reset => state.set(0)
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
