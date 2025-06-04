package zio.internal

import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import scala.annotation.tailrec
import java.util.{Set => JSet, Collection => JCollection}
import java.util.Iterator
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.{Consumer, Predicate, Function}
import java.util.stream.Stream

/**
 * A thread-safe set implementation optimized for Scala Native that avoids the
 * treeification issues of ConcurrentHashMap. Uses a simple array-based approach
 * with locks for each bucket.
 */
private[zio] final class ConcurrentHashSet[A](initialCapacity: Int = 16) extends JSet[A] {
  private[this] val loadFactor = 0.75f
  private[this] var locks      = new Array[ReentrantLock](initialCapacity)
  private[this] var table      = new AtomicReferenceArray[Array[AnyRef]](initialCapacity)
  private[this] val size       = new AtomicInteger(0)
  private[this] var threshold  = (initialCapacity * loadFactor).toInt

  // Initialize locks and table
  (0 until initialCapacity).foreach { i =>
    locks(i) = new ReentrantLock()
    table.set(i, new Array[AnyRef](0))
  }

  override def add(element: A): Boolean = {
    if (element == null) return false

    val hash  = element.hashCode()
    val index = (hash & 0x7fffffff) % table.length()
    val lock  = locks(index)

    lock.lock()
    try {
      var bucket = table.get(index)
      if (bucket == null) {
        bucket = new Array[AnyRef](0)
        table.set(index, bucket)
      }

      // Check if element already exists
      var i = 0
      while (i < bucket.length) {
        if (bucket(i) == element) return false
        i += 1
      }

      // Add element
      val newBucket = new Array[AnyRef](bucket.length + 1)
      System.arraycopy(bucket, 0, newBucket, 0, bucket.length)
      newBucket(bucket.length) = element.asInstanceOf[AnyRef]
      table.set(index, newBucket)

      val newSize = size.incrementAndGet()
      if (newSize > threshold) {
        resize()
      }
      true
    } finally {
      lock.unlock()
    }
  }

  override def remove(element: Any): Boolean = {
    if (element == null) return false

    val hash  = element.hashCode()
    val index = (hash & 0x7fffffff) % table.length()
    val lock  = locks(index)

    lock.lock()
    try {
      val bucket = table.get(index)
      if (bucket == null) return false

      var i = 0
      while (i < bucket.length) {
        if (bucket(i) == element) {
          val newBucket = new Array[AnyRef](bucket.length - 1)
          System.arraycopy(bucket, 0, newBucket, 0, i)
          System.arraycopy(bucket, i + 1, newBucket, i, bucket.length - i - 1)
          table.set(index, newBucket)
          size.decrementAndGet()
          return true
        }
        i += 1
      }
      false
    } finally {
      lock.unlock()
    }
  }

  override def contains(element: Any): Boolean = {
    if (element == null) return false

    val hash  = element.hashCode()
    val index = (hash & 0x7fffffff) % table.length()
    val lock  = locks(index)

    lock.lock()
    try {
      val bucket = table.get(index)
      if (bucket == null) return false

      var i = 0
      while (i < bucket.length) {
        if (bucket(i) == element) return true
        i += 1
      }
      false
    } finally {
      lock.unlock()
    }
  }

  override def size(): Int = size.get()

  override def isEmpty: Boolean = size.get() == 0

  override def clear(): Unit = {
    var i = 0
    while (i < table.length()) {
      val lock = locks(i)
      lock.lock()
      try {
        table.set(i, new Array[AnyRef](0))
      } finally {
        lock.unlock()
      }
      i += 1
    }
    size.set(0)
  }

  override def addAll(c: JCollection[_ <: A]): Boolean = {
    var modified = false
    val it       = c.iterator()
    while (it.hasNext) {
      if (add(it.next())) modified = true
    }
    modified
  }

  override def containsAll(c: JCollection[_]): Boolean = {
    val it = c.iterator()
    while (it.hasNext) {
      if (!contains(it.next())) return false
    }
    true
  }

  override def removeAll(c: JCollection[_]): Boolean = {
    var modified = false
    val it       = c.iterator()
    while (it.hasNext) {
      if (remove(it.next())) modified = true
    }
    modified
  }

  override def retainAll(c: JCollection[_]): Boolean = {
    var modified = false
    val it       = iterator()
    while (it.hasNext) {
      val e = it.next()
      if (!c.contains(e)) {
        it.remove()
        modified = true
      }
    }
    modified
  }

  override def toArray(): Array[AnyRef] = {
    val result = new Array[AnyRef](size())
    var i      = 0
    val it     = iterator()
    while (it.hasNext) {
      result(i) = it.next().asInstanceOf[AnyRef]
      i += 1
    }
    result
  }

  override def toArray[T](a: Array[T]): Array[T] = {
    val size = size()
    val result =
      if (a.length >= size) a
      else java.lang.reflect.Array.newInstance(a.getClass.getComponentType, size).asInstanceOf[Array[T]]
    var i  = 0
    val it = iterator()
    while (it.hasNext) {
      result(i) = it.next().asInstanceOf[T]
      i += 1
    }
    if (i < result.length) result(i) = null.asInstanceOf[T]
    result
  }

  override def iterator(): Iterator[A] = new Iterator[A] {
    private[this] var currentIndex                 = 0
    private[this] var currentBucket: Array[AnyRef] = null
    private[this] var currentPos                   = 0
    private[this] var lastReturned: A              = _

    @tailrec
    def findNext(): Unit = {
      if (currentBucket != null && currentPos < currentBucket.length) {
        // Already have a bucket with elements
        return
      }

      while (currentIndex < table.length()) {
        val lock = locks(currentIndex)
        lock.lock()
        try {
          val bucket = table.get(currentIndex)
          if (bucket != null && bucket.length > 0) {
            currentBucket = bucket
            currentPos = 0
            return
          }
        } finally {
          lock.unlock()
        }
        currentIndex += 1
      }
      currentBucket = null
    }

    override def hasNext: Boolean = {
      findNext()
      currentBucket != null && currentPos < currentBucket.length
    }

    override def next(): A = {
      if (!hasNext) throw new NoSuchElementException()
      lastReturned = currentBucket(currentPos).asInstanceOf[A]
      currentPos += 1
      lastReturned
    }

    override def remove(): Unit = {
      if (lastReturned == null) throw new IllegalStateException()
      ConcurrentHashSet.this.remove(lastReturned)
      lastReturned = null.asInstanceOf[A]
    }
  }

  override def spliterator(): Spliterator[A] = Spliterators.spliterator(iterator(), size(), Spliterator.DISTINCT)

  override def forEach(action: Consumer[_ >: A]): Unit = {
    val it = iterator()
    while (it.hasNext) {
      action.accept(it.next())
    }
  }

  override def removeIf(filter: Predicate[_ >: A]): Boolean = {
    var removed = false
    val it      = iterator()
    while (it.hasNext) {
      val e = it.next()
      if (filter.test(e)) {
        it.remove()
        removed = true
      }
    }
    removed
  }

  override def stream(): Stream[A] = java.util.stream.StreamSupport.stream(spliterator(), false)

  override def parallelStream(): Stream[A] = java.util.stream.StreamSupport.stream(spliterator(), true)

  private def resize(): Unit = {
    val oldTable  = table
    val oldLength = oldTable.length()
    val newLength = oldLength * 2
    val newTable  = new AtomicReferenceArray[Array[AnyRef]](newLength)
    val newLocks  = new Array[ReentrantLock](newLength)

    // Initialize new locks
    (0 until newLength).foreach { i =>
      newLocks(i) = new ReentrantLock()
    }

    // Rehash all elements
    var i = 0
    while (i < oldLength) {
      val lock = locks(i)
      lock.lock()
      try {
        val bucket = oldTable.get(i)
        if (bucket != null) {
          var j = 0
          while (j < bucket.length) {
            val element  = bucket(j)
            val hash     = element.hashCode()
            val newIndex = (hash & 0x7fffffff) % newLength
            val newLock  = newLocks(newIndex)

            newLock.lock()
            try {
              var newBucket = newTable.get(newIndex)
              if (newBucket == null) {
                newBucket = new Array[AnyRef](0)
              }
              val newerBucket = new Array[AnyRef](newBucket.length + 1)
              System.arraycopy(newBucket, 0, newerBucket, 0, newBucket.length)
              newerBucket(newBucket.length) = element
              newTable.set(newIndex, newerBucket)
            } finally {
              newLock.unlock()
            }
            j += 1
          }
        }
      } finally {
        lock.unlock()
      }
      i += 1
    }

    // Update instance variables
    table = newTable
    locks = newLocks
    threshold = (newLength * loadFactor).toInt
  }
}
