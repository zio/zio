// Vendored from series/2.x @ 5bcf1dc21244c2995cf8a6de49556beb683015cf for reproducibility of the
// JMH comparison in ZIO #8861. Not part of the runtime; lives under the `legacy` package in the
// benchmarks module only.
package zio.internal.legacy

import java.util.{Collections, WeakHashMap, Set => JSet}

private[zio] final class SyncWeakHashSet[A <: AnyRef] {
  private[this] val inner: JSet[A] =
    Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap[A, java.lang.Boolean]()))

  def add(a: A): Unit                 = { val _ = inner.add(a) }
  def remove(a: A): Unit              = { val _ = inner.remove(a) }
  def iterator: java.util.Iterator[A] = inner.iterator()
  def isEmpty: Boolean                = inner.isEmpty
  def size: Int                       = inner.size
}
