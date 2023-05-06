package zio.internal

import zio.{UIO, Unsafe}
import zio.internal.Platform.newConcurrentWeakSet

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.mutable

object ConcurrentWeakHashSetSpec extends ConcurrentWeakHashSetAbstractSpec {
  override def performGC(): UIO[Unit] = zio.ZIO.succeed(
    (1 to 30).foreach(_ => System.gc())
  )

  override def createSet[V <: AnyRef](): mutable.Set[V] = Unsafe.unsafe(u => newConcurrentWeakSet[V]()(u))

  override def createConcurrentQueue[V](): java.util.Queue[V] = new ConcurrentLinkedQueue[V]()
}
