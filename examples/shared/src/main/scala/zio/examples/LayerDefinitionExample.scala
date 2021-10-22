package zio.examples
import zio._

object LayerDefinitionExample extends ZIOAppDefault {
  trait Foo {
    def bar: UIO[Unit]
  }

  object Foo {
    val live: URDeps[Has[Console] with Has[String] with Has[Int], Has[Foo]] =
      (FooLive.apply _).toDeps

    case class FooLive(console: Console, string: String, int: Int) extends Foo {
      override def bar: UIO[Unit] = console.printLine(s"$string and $int").orDie
    }
  }

  override def run: ZIO[ZEnv with Has[ZIOAppArgs], Any, Any] = {

    val program: ZIO[Has[Foo], Nothing, Unit] = ZIO.serviceWith[Foo](_.bar)

    program
      .inject(
        Console.live,
        ZDeps.succeed("Hello"),
        ZDeps.succeed(3),
        Foo.live
      )
  }

}
