package zio.examples
import zio._

object ServiceBuilderDefinitionExample extends ZIOAppDefault {
  trait Foo {
    def bar: UIO[Unit]
  }

  object Foo {
    val live: URServiceBuilder[Console with String with Int, Foo] =
      (FooLive.apply _).toServiceBuilder

    case class FooLive(console: Console, string: String, int: Int) extends Foo {
      override def bar: UIO[Unit] = console.printLine(s"$string and $int").orDie
    }
  }

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = {

    val program: ZIO[Foo, Nothing, Unit] = ZIO.serviceWith[Foo](_.bar)

    program
      .inject(
        Console.live,
        ZServiceBuilder.succeed("Hello"),
        ZServiceBuilder.succeed(3),
        Foo.live
      )
  }

}
