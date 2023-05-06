package zio.internal

import zio.internal.JVMConcurrentWeakSetAdapter.{StrongSetElement, WeakSetElement}

import java.lang.ref.{ReferenceQueue, WeakReference}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class JVMConcurrentWeakSetAdapter[V <: AnyRef](
  val underlying: java.util.Set[JVMConcurrentWeakSetAdapter.SetElement[V]]
) extends mutable.Set[V] {
  override def iterator: Iterator[V] =
    underlying.iterator.asScala.map(_.getValue).filterNot(_ == null)

  override def subtractOne(elem: V): this.type = {
    underlying.remove(new StrongSetElement(elem))
    this
  }

  override def clear(): Unit = underlying.clear()

  override def addOne(elem: V): this.type = {
    val holder = new WeakSetElement(elem, underlying)
    underlying.add(holder)
    this
  }

  override def knownSize: Int = underlying.size()

  override def contains(elem: V): Boolean =
    underlying.contains(new StrongSetElement(elem))
}

object JVMConcurrentWeakSetAdapter extends ConcurrentWeakSetAdapter {

  /**
   * SetElement that is kept in set (others are used only for comparisons)
   *
   * @param value
   *   Element that will be immediately converted to weak reference
   * @param skipList
   *   List to which value is added - we need reference to is since cleaner
   *   thread is global
   */
  private class WeakSetElement[V <: AnyRef](
    value: V,
    val skipList: java.util.Set[SetElement[V]]
  ) extends WeakReference[V](value, typedReferenceQueue())
      with SetElement[V] {
    val cachedHashCode: Int = value.hashCode()

    override def getValue: V = get

  }

  private val referenceQueue = new ReferenceQueue[AnyRef]()
  private def typedReferenceQueue[T <: AnyRef]() =
    referenceQueue.asInstanceOf[ReferenceQueue[T]]

  private val cleanerThread = new Thread(() =>
    while (!Thread.interrupted()) {
      referenceQueue.remove match {
        case value: WeakSetElement[?] =>
          value.skipList.remove(new NullSetElement(value.cachedHashCode))
        case value =>
          java.lang.System.err.println(s"Unexpected class in reference queue: $value")
      }
    }
  )

  cleanerThread.setName("ConcurrentWeakSetCleaner")
  cleanerThread.setDaemon(true)
  cleanerThread.start()

}
