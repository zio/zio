package zio.internal

import zio.internal.Platform.newConcurrentWeakSet
import zio.{UIO, Unsafe}

import java.util
import scala.collection.mutable
import scala.scalajs.js

object ConcurrentWeakHashSetSpec extends ConcurrentWeakHashSetAbstractSpec {
  override def performGC(): UIO[Unit] = zio.ZIO
    .succeed(
      js.Dynamic.global.gc()
    )
    .unit

  override def createSet[V <: AnyRef](): mutable.Set[V] = Unsafe.unsafe(u => newConcurrentWeakSet[V]()(u))

  override def createConcurrentQueue[V](): util.Queue[V] = new java.util.LinkedList[V]()
}
