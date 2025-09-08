package zio

import zio.test._

object ZIOAppArgsSpec extends ZIOSpecDefault {
  def spec =
    suite("ZIOAppArgsSpec")(
      test("an app should receive command-line arguments") {
        for {
          args <- ZIOAppArgs.getArgs
        } yield assertTrue(args == Chunk("hello", "world"))
      }.provideLayer(
        ZLayer.succeed(ZIOAppArgs(Chunk("hello", "world")))
      ),
      test("an app should receive no command-line arguments") {
        for {
          args <- ZIOAppArgs.getArgs
        } yield assertTrue(args.isEmpty)
      }.provideLayer(
        ZLayer.succeed(ZIOAppArgs(Chunk.empty))
      )
    )
}
