package zio.internal

import com.github.ghik.silencer.silent
import zio.internal.JVMConcurrentWeakSetAdapter.{StrongSetElement, WeakSetElement}

import java.lang.ref.{ReferenceQueue, WeakReference}

class JVMConcurrentWeakSetAdapter[V <: AnyRef](
  val underlying: java.util.Set[JVMConcurrentWeakSetAdapter.SetElement[V]]
) extends zio.MutableSetCompat[V] {
  override def iterator: Iterator[V] = {
    import collection.JavaConverters._
    underlying.iterator.asScala.map(_.getValue).filterNot(_ == null): @silent("JavaConverters")
  }

  override def clear(): Unit = underlying.clear()

  override def sizeCompat(): Int = underlying.size()

  override def contains(elem: V): Boolean =
    underlying.contains(new StrongSetElement(elem))

  override def addCompat(elem: V): Unit =
    underlying.add(new WeakSetElement(elem, underlying))

  override def removeCompat(elem: V): Unit =
    underlying.remove(new StrongSetElement(elem))

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
