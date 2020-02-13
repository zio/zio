package zio.stream.experimental

import zio._
import zio.ZQueueSpecUtil.waitForSize
import zio.stream.experimental.ZStreamUtils.nPulls
import zio.stream.ChunkUtils.smallChunks
import zio.test.Assertion.{ equalTo, isFalse, isTrue }
import zio.test._

object ZStreamSpec extends ZIOBaseSpec {
  def spec = suite("ZStreamSpec")(
    suite("Combinators")(
      testM("map") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .map(_.toString)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right("1"), Left(Right(())), Left(Right(()))))))
      },
      testM("filter - keep elements that satisfy the predicate") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .filter(_ > 0)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
      },
      testM("filter - filter out elements that do not satisfy the predicate") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .filter(_ < 0)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Left(Right(())), Left(Right(())), Left(Right(()))))))
      },
      testM("Stream.buffer is safe to pull again") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .buffer(2)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
      }
    ),
    suite("Constructors")(
      suite("fromEffect")(
        testM("success") {
          ZStream
            .fromEffect(UIO.succeed(1))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
        },
        testM("failure") {
          ZStream
            .fromEffect(IO.fail("Ouch"))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))))
        }
      ),
      suite("managed")(
        testM("success") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(UIO.succeed(1))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(equalTo(List(Right(1), Left(Right(())), Left(Right(())))))
        },
        testM("acquisition failure") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(IO.fail("Ouch"))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isFalse) && assert(pulls)(
            equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))
          )
        },
        testM("inner failure") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(UIO.succeed(1))(_ => ref.set(true)) *> Managed.fail("Ouch"))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(
            equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))
          )
        }
      )
    ),
    suite("Destructors")(
      testM("ZStream.toQueue")(checkM(smallChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
        val s = ZStream.fromChunk(c)
        assertM(s.toQueue(1000).use { (queue: Queue[Take[Nothing, Unit, Int]]) =>
          waitForSize(queue, c.length + 1) *> queue.takeAll
        })(equalTo(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End(())))
      })
    )
  )
}
