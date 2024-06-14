package zio.internal

import zio.ZIOBaseSpec
import zio.ZIO
import zio.test.Assertion._
import zio.test._
import zio.Chunk

object MailboxSpec extends ZIOBaseSpec {

  def spec =
    suite("Mailbox")(
      test("preserves elements")(
        check(Gen.chunkOf1(Gen.uuid)) { expected =>
          val q = new Mailbox[AnyRef]
          for {
            consumer <- ZIO
                          .succeed(q.poll())
                          .repeatWhile(_ == null)
                          .replicateZIO(expected.length)
                          .fork
            produce = (a: AnyRef) => ZIO.succeedBlocking(q.add(a))
            _ <-
              ZIO
                .withParallelism(expected.length)(
                  ZIO.foreachParDiscard(expected)(produce)
                )
                .fork
            actual <- consumer.join
          } yield assert(actual)(hasSameElements(expected))
        }
      ),
      test("preserves insertion order with a single producer")(
        check(Gen.chunkOf1(Gen.uuid)) { expected =>
          val q = new Mailbox[AnyRef]
          for {
            consumer <- ZIO
                          .succeed(q.poll())
                          .repeatWhile(_ == null)
                          .replicateZIO(expected.length)
                          .fork
            _      <- ZIO.succeedBlocking(expected.foreach(q.add)).fork
            actual <- consumer.join
          } yield assert(actual)(Assertion.equalTo(expected.toChunk))
        }
      ),
      test("prepend + add reorders elements and does not affect subsequent operations") {
        val expected = Chunk("a", "b")
        val last     = "c"
        val q        = new Mailbox[String]
        assertZIO(for {
          _      <- ZIO.succeed(q.prepend(expected(0)))
          _      <- ZIO.succeedBlocking(q.add(expected(1)))
          actual <- ZIO.succeed(Chunk.fill(2)(q.poll()))
          _      <- ZIO.succeedBlocking(q.add(last))
          next   <- ZIO.succeed(q.poll())
        } yield (actual, next))(equalTo((expected, last)))
      },
      test("add + prepend reorders elements and does not affect subsequent operations") {
        val expected = Chunk("a", "b")
        val last     = "c"
        val q        = new Mailbox[String]
        assertZIO(for {
          _      <- ZIO.succeedBlocking(q.add(expected(1)))
          _      <- ZIO.succeed(q.prepend(expected(0)))
          actual <- ZIO.succeed(Chunk.fill(2)(q.poll()))
          _      <- ZIO.succeedBlocking(q.add(last))
          next   <- ZIO.succeed(q.poll())
        } yield (actual, next))(equalTo((expected, last)))
      },
      test("prepend2 + add reorders elements and does not affect subsequent operations") {
        val expected = Chunk("", "a", "b")
        val last     = "c"
        val q        = new Mailbox[String]
        assertZIO(for {
          _      <- ZIO.succeed(q.prepend2(expected(0), expected(1)))
          _      <- ZIO.succeedBlocking(q.add(expected(2)))
          actual <- ZIO.succeed(Chunk.fill(3)(q.poll()))
          _      <- ZIO.succeedBlocking(q.add(last))
          next   <- ZIO.succeed(q.poll())
        } yield (actual, next))(equalTo((expected, last)))
      },
      test("add + prepend2 reorders elements and does not affect subsequent operations") {
        val expected = Chunk("", "a", "b")
        val last     = "c"
        val q        = new Mailbox[String]
        assertZIO(for {
          _      <- ZIO.succeedBlocking(q.add(expected(2)))
          _      <- ZIO.succeed(q.prepend2(expected(0), expected(1)))
          actual <- ZIO.succeed(Chunk.fill(3)(q.poll()))
          _      <- ZIO.succeedBlocking(q.add(last))
          next   <- ZIO.succeed(q.poll())
        } yield (actual, next))(equalTo((expected, last)))
      }
    )
}
