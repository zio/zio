package zio.internal

import zio._
import zio.test._

import java.util.concurrent.atomic.AtomicReference

object FiberMailboxSpecJVM extends ZIOBaseSpec {

  def spec =
    suite("FiberMailboxSpecJVM")(
      test("links messages before add returns") {
        ZIO.succeed {
          val mailbox = new FiberMailbox
          val message = FiberMessage.Stateful(_ => ())

          mailbox.add(message)

          assertTrue(
            mailbox.hasLinkedMessages,
            !mailbox.isDefinitelyEmpty,
            mailbox.poll() eq message,
            mailbox.isDefinitelyEmpty
          )
        }
      },
      test("tracks in-flight producers separately from linked messages") {
        ZIO.succeed {
          val mailbox = new FiberMailbox
          val message = FiberMessage.Stateful(_ => ())

          val nodeClass         = Class.forName("zio.internal.FiberMailbox$Node")
          val nodeConstructor   = nodeClass.getDeclaredConstructor(classOf[FiberMessage])
          val producerNodeField = classOf[FiberMailbox].getDeclaredField("producerNode")
          val consumerNodeField = classOf[FiberMailbox].getDeclaredField("consumerNode")

          nodeConstructor.setAccessible(true)
          producerNodeField.setAccessible(true)
          consumerNodeField.setAccessible(true)

          val producerNode = nodeConstructor.newInstance(message).asInstanceOf[AnyRef]
          val consumerNode = consumerNodeField.get(mailbox).asInstanceOf[AtomicReference[AnyRef]]

          producerNodeField.set(mailbox, producerNode)

          val polledBeforeLink          = mailbox.poll()
          val hasLinkedBeforeLink       = mailbox.hasLinkedMessages
          val definitelyEmptyBeforeLink = mailbox.isDefinitelyEmpty

          consumerNode.set(producerNode)

          val polledAfterLink = mailbox.poll()

          assertTrue(
            polledBeforeLink eq null,
            !hasLinkedBeforeLink,
            !definitelyEmptyBeforeLink,
            polledAfterLink eq message,
            !mailbox.hasLinkedMessages,
            mailbox.isDefinitelyEmpty
          )
        }
      }
    )
}
