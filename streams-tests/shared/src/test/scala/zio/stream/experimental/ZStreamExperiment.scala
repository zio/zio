package zio.stream.experimental

import zio._
import zio.test.Assertion._
import zio.test._

object ZStreamExperiment extends ZIOBaseSpec {

  import ZStreamGen._

  def spec =
    suite("ZStreamExperiment")(
      suite("Combinators")(
        suite("flatMap")(
          testM("deep flatMap stack safety") {
            def fib(n: Int): ZStream[Any, Nothing, Int] =
              if (n <= 1) ZStream(n)
              else
                fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => ZStream(a + b)))

            val stream   = fib(20)
            val expected = 6765

            assertM(stream.runCollect)(equalTo(Chunk(expected)))
          } @@ TestAspect.jvmOnly, // Too slow on Scala.js
          testM("inner finalizers") {
            for {
              effects <- Ref.make(List[Int]())
              push    = (i: Int) => effects.update(i :: _)
              latch   <- Promise.make[Nothing, Unit]
              fiber <- ZStream(
                        ZStream.bracket(push(1))(_ => push(1)),
                        ZStream.fromEffect(push(2)),
                        ZStream.bracket(push(3))(_ => push(3)) *> ZStream.fromEffect(
                          latch.succeed(()) *> ZIO.never
                        )
                      ).flatMap(identity).runDrain.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- effects.get
            } yield assert(result)(equalTo(List(3, 3, 2, 1, 1)))
          },
          testM("finalizer ordering") {
            for {
              effects <- Ref.make(List[String]())
              push    = (i: String) => effects.update(i :: _)
              stream = for {
                _ <- ZStream.bracket(push("open1"))(_ => push("close1"))
                _ <- ZStream(1, 2)
                      .tap(_ => push("use2"))
                      .ensuring(push("close2"))
                _ <- ZStream.bracket(push("open3"))(_ => push("close3"))
                _ <- ZStream(3, 4)
                      .tap(_ => push("use4"))
                      .ensuring(push("close4"))
              } yield ()
              _      <- stream.runDrain
              result <- effects.get
            } yield assert(result.reverse)(
              equalTo(
                List(
                  "open1",
                  "use2",
                  "open3",
                  "use4",
                  "use4",
                  "close4",
                  "close3",
                  "use2",
                  "open3",
                  "use4",
                  "use4",
                  "close4",
                  "close3",
                  "close2",
                  "close1"
                )
              )
            )
          },
          testM("exit signal") {
            for {
              ref <- Ref.make(false)
              inner = ZStream
                .bracketExit(UIO.unit)((_, e) =>
                  e match {
                    case Exit.Failure(_) => ref.set(true)
                    case Exit.Success(_) => UIO.unit
                  }
                )
                .flatMap(_ => ZStream.fail("Ouch"))
              _   <- ZStream(()).flatMap(_ => inner).runDrain.either.unit
              fin <- ref.get
            } yield assert(fin)(isTrue)
          },
          testM("finalizers are registered in the proper order") {
            for {
              fins <- Ref.make(List[Int]())
              s = ZStream.finalizer(fins.update(1 :: _)) *>
                ZStream.finalizer(fins.update(2 :: _))
              _      <- s.process.withEarlyRelease.use(_._2)
              result <- fins.get
            } yield assert(result)(equalTo(List(1, 2)))
          },
          testM("early release finalizer concatenation is preserved") {
            for {
              fins <- Ref.make(List[Int]())
              s = ZStream.finalizer(fins.update(1 :: _)) *>
                ZStream.finalizer(fins.update(2 :: _))
              result <- s.process.withEarlyRelease.use {
                         case (release, pull) =>
                           pull *> release *> fins.get
                       }
            } yield assert(result)(equalTo(List(1, 2)))
          }
        )
      ),
      suite("Constructors")(
        testM("fromChunk")(checkM(tinyChunkOf(Gen.anyInt))(c => assertM(ZStream.fromChunk(c).runCollect)(equalTo(c))))
      )
    )
}
