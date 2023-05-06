package zio.internal

trait ConcurrentWeakSetAdapter {

  /**
   * Trait of objects that interact with WeakSet
   */
  trait SetElement[V <: AnyRef] extends Comparable[SetElement[V]] {

    /**
     * Since we're using weak references, after GC we don't have access to
     * hashcode anymore - so we cache it
     */
    protected val cachedHashCode: Int

    /**
     * May return null
     */
    def getValue: V

    override def compareTo(o: SetElement[V]): Int =
      o.cachedHashCode.compareTo(cachedHashCode)

    override def equals(other: Any): Boolean = other match {
      case that: SetElement[?] =>
        getValue == that.getValue
      case _ => false
    }

    override def hashCode(): Int =
      cachedHashCode
  }

  /**
   * Set element that matches non-null objects with given hash code - used for
   * removing from user code and contains
   */
  class StrongSetElement[V <: AnyRef](private val v: V) extends SetElement[V] {
    override val cachedHashCode: Int = v.hashCode()

    override def getValue: V = v
  }

  /**
   * Set element that matches nulled objects with given hash code - used for
   * removing from finalizer
   */
  class NullSetElement[V <: AnyRef](protected override val cachedHashCode: Int) extends SetElement[V] {
    override def getValue: V = null.asInstanceOf[V]
  }

}
