package zio.internal

import zio._
import zio.test._

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.{IdentityHashMap => JIdentityHashMap}

object FiberMailboxSpec extends ZIOBaseSpec {

  def spec =
    suite("FiberMailboxSpec")(
      test("polls messages FIFO") {
        val mailbox = new FiberMailbox
        val first   = FiberMessage.Stateful(_ => ())
        val second  = FiberMessage.Stateful(_ => ())

        mailbox.add(first)
        mailbox.add(second)

        val firstPolled  = mailbox.poll()
        val secondPolled = mailbox.poll()
        val emptyPolled  = mailbox.poll()

        assertTrue(
          firstPolled eq first,
          secondPolled eq second,
          emptyPolled eq null,
          !mailbox.hasLinkedMessages,
          mailbox.isDefinitelyEmpty
        )
      },
      test("does not drop, duplicate, or reorder messages from the same producer") {
        val mailbox     = new FiberMailbox
        val producers   = 16
        val perProducer = 128
        val messages =
          Vector.tabulate(producers, perProducer) { (_, _) =>
            FiberMessage.Stateful(_ => ())
          }

        for {
          _ <- ZIO.foreachParDiscard(0 until producers) { producer =>
                 ZIO.foreachDiscard(0 until perProducer) { index =>
                   ZIO.succeed(mailbox.add(messages(producer)(index)))
                 }
               }
          polled <- ZIO.succeed {
                      val builder = List.newBuilder[FiberMessage]
                      var message = mailbox.poll()

                      while (message ne null) {
                        builder += message
                        message = mailbox.poll()
                      }

                      builder.result()
                    }
        } yield {
          val positions = new JIdentityHashMap[FiberMessage, Int]()
          polled.zipWithIndex.foreach { case (message, index) =>
            positions.put(message, index)
          }
          val ordered = messages.forall { producerMessages =>
            producerMessages.sliding(2).forall {
              case Vector(first, second) => positions.get(first) < positions.get(second)
              case _                     => true
            }
          }
          val expected = new JIdentityHashMap[FiberMessage, java.lang.Boolean]()
          messages.flatten.foreach(expected.put(_, java.lang.Boolean.TRUE))
          val found = new JIdentityHashMap[FiberMessage, java.lang.Boolean]()
          polled.foreach(found.put(_, java.lang.Boolean.TRUE))

          assertTrue(
            polled.size == producers * perProducer,
            found == expected,
            ordered,
            !mailbox.hasLinkedMessages,
            mailbox.isDefinitelyEmpty
          )
        }
      },
      test("supports concurrent producers and consumer") {
        val mailbox     = new FiberMailbox
        val producers   = 8
        val perProducer = 256
        val total       = producers * perProducer
        val received    = new ConcurrentLinkedQueue[FiberMessage]()
        val remaining   = new AtomicInteger(total)

        val produce =
          ZIO.foreachParDiscard(0 until producers) { _ =>
            ZIO.succeed {
              var index = 0
              while (index < perProducer) {
                mailbox.add(FiberMessage.Stateful(_ => ()))
                index += 1
              }
            }
          }

        def consume: UIO[Unit] =
          ZIO.suspendSucceed {
            if (remaining.get() == 0) ZIO.unit
            else {
              val message = mailbox.poll()
              if (message eq null) ZIO.yieldNow *> consume
              else {
                received.offer(message)
                remaining.decrementAndGet()
                consume
              }
            }
          }

        for {
          consumer <- consume.fork
          _        <- produce
          _        <- consumer.join
        } yield assertTrue(
          received.size() == total,
          remaining.get() == 0,
          !mailbox.hasLinkedMessages,
          mailbox.isDefinitelyEmpty
        )
      }
    )
}
