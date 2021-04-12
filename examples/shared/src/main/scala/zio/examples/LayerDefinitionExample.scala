package zio.examples
import zio._

object LayerDefinitionExample extends App {
  trait Foo {
    def bar: UIO[Unit]
  }

  object Foo {
    val live: URLayer[Has[Console] with Has[String] with Has[Int], Has[Foo]] =
      (FooLive.apply _).toLayer

    case class FooLive(console: Console, string: String, int: Int) extends Foo {
      override def bar: UIO[Unit] = console.printLine(s"$string and $int")
    }
  }

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val liveLayer: ULayer[Has[Foo]] =
      (Console.live ++ ZLayer.succeed("Hello") ++ ZLayer.succeed(3)) >>> Foo.live

    ZIO
      .accessM[Has[Foo]](_.get.bar)
      .provideLayer(liveLayer)
      .exitCode
  }
}
