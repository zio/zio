package zio.internal

import com.github.ghik.silencer.silent
import zio.internal.JSConcurrentWeakSetAdapter.{NullSetElement, SetElement, StrongSetElement, WeakSetElement}

import scala.scalajs.js.{FinalizationRegistry, WeakRef}

class JSConcurrentWeakSetAdapter[V <: AnyRef] extends zio.MutableSetCompat[V] {

  // JS has one thread so we don't need concurrent collection
  private val underlying = new java.util.HashSet[SetElement[V]]()

  private val registry =
    new FinalizationRegistry[V, WeakSetElement[V], AnyRef](value => {
      underlying.remove(new NullSetElement(value.cachedHashCode))
    })

  override def sizeCompat(): Int = underlying.size()

  def iterator(): Iterator[V] = {
    import collection.JavaConverters._
    underlying.iterator().asScala.map(_.getValue).filterNot(_ == null): @silent("JavaConverters")
  }

  override def clear(): Unit =
    underlying.clear()

  override def contains(elem: V): Boolean = underlying.contains(new StrongSetElement[V](elem))

  override def addCompat(elem: V): Unit = {
    val holder = new WeakSetElement(elem)
    registry.register(elem, holder)
    underlying.add(holder)
  }

  override def removeCompat(elem: V): Unit =
    underlying.remove(new StrongSetElement[V](elem))

}

object JSConcurrentWeakSetAdapter extends ConcurrentWeakSetAdapter {

  /**
   * SetElement that is kept in set (others are used only for comparisons)
   *
   * @param value
   *   Element that will be immediately converted to weak reference
   */
  private class WeakSetElement[V <: AnyRef](
    value: V
  ) extends SetElement[V] {
    private val weakRef     = new WeakRef(value)
    val cachedHashCode: Int = value.hashCode()

    override def getValue: V = weakRef.deref[V]().getOrElse(null).asInstanceOf[V]
  }

}
