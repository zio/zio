package zio.internal

import zio.internal.JSConcurrentWeakSetAdapter.{NullSetElement, SetElement, StrongSetElement, WeakSetElement}

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.scalajs.js.{FinalizationRegistry, WeakRef}

class JSConcurrentWeakSetAdapter[V <: AnyRef] extends mutable.Set[V] {

  // JS has one thread so we don't need concurrent collection
  private val underlying = new java.util.HashSet[SetElement[V]]()

  private val registry =
    new FinalizationRegistry[V, WeakSetElement[V], AnyRef](value => {
      underlying.remove(new NullSetElement(value.cachedHashCode))
    })

  override def knownSize: Int = underlying.size()

  def iterator(): Iterator[V] =
    underlying.iterator().asScala.map(_.getValue).filterNot(_ == null)

  override def subtractOne(elem: V): this.type = {
    underlying.remove(new StrongSetElement[V](elem))
    this
  }

  override def clear(): Unit =
    underlying.clear()

  override def addOne(v: V): this.type = {
    val holder = new WeakSetElement(v)
    registry.register(v, holder)
    underlying.add(holder)
    this
  }

  override def contains(elem: V): Boolean = underlying.contains(new StrongSetElement[V](elem))
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
