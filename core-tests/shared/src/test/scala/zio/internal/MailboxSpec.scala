package zio.internal

import zio.ZIOBaseSpec
import zio.ZIO
import zio.test.Assertion._
import zio.test._

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
      )
    )
}
