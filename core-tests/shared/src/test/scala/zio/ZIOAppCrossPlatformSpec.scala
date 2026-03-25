package zio

import zio.test._
import zio.test.TestAspect._

object ZIOAppCrossPlatformSpec extends ZIOBaseSpec {
  def spec =
    suite("ZIOAppCrossPlatformSpec")(
      test("invoke runs app to completion") {
        ZIOApp.fromZIO(ZIO.unit).invoke(Chunk.empty).as(assertTrue(true))
      },
      test("invoke propagates failure") {
        for {
          exit <- ZIOApp.fromZIO(ZIO.fail("err")).invoke(Chunk.empty).exit
        } yield assertTrue(exit.isFailure)
      },
      test("finalizers run when fiber is interrupted via invoke") {
        for {
          ref <- Ref.make(false)
          app = ZIOApp.fromZIO(
                  ZIO.scoped(
                    ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true)) *> ZIO.unit
                  )
                )
          _   <- app.invoke(Chunk.empty)
          ran <- ref.get
        } yield assertTrue(ran)
      },
      test("composed apps run all component logic") {
        for {
          ref <- Ref.make(2)
          app1 = ZIOApp.fromZIO(ref.update(_ + 3))
          app2 = ZIOApp.fromZIO(ref.update(_ - 5))
          _   <- (app1 <> app2).invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 0)
      },
      test("args are passed correctly") {
        for {
          ref <- Ref.make(Chunk.empty[String])
          app  = ZIOApp.fromZIO(ZIOAppArgs.getArgs.flatMap(ref.set))
          _   <- app.invoke(Chunk("a", "b", "c"))
          got <- ref.get
        } yield assertTrue(got == Chunk("a", "b", "c"))
      }
    ) @@ sequential
}
