package zio.examples
import zio._

object ProviderDefinitionExample extends ZIOAppDefault {
  trait Foo {
    def bar: UIO[Unit]
  }

  object Foo {
    val live: URProvider[Console with String with Int, Foo] =
      (FooLive.apply _).toProvider

    case class FooLive(console: Console, string: String, int: Int) extends Foo {
      override def bar: UIO[Unit] = console.printLine(s"$string and $int").orDie
    }
  }

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = {

    val program: ZIO[Foo, Nothing, Unit] = ZIO.serviceWith[Foo](_.bar)

    program
      .inject(
        Console.live,
        ZProvider.succeed("Hello"),
        ZProvider.succeed(3),
        Foo.live
      )
  }

}
