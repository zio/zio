package zio.internal

import scala.collection.immutable.Queue

object ScalaQueueCompat {

  implicit final class ScalaQueueTypeOps[A](private val dummy: Queue.type) extends AnyVal {

    /**
     * Code adapted from Scala 2.13 Queue object to provide compatibility in
     * Scala 2.12
     */
    def from[A](list: List[A]): Queue[A] = if (list.isEmpty) Queue.empty else Queue(list: _*)
  }

}
