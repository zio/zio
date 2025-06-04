package zio.internal

import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import scala.annotation.tailrec

/**
 * A thread-safe set implementation optimized for Scala Native that avoids the
 * treeification issues of ConcurrentHashMap. Uses a simple array-based approach
 * with locks for each bucket.
 */
private[zio] final class ConcurrentHashSet[A](initialCapacity: Int = 16) {
  private[this] val loadFactor = 0.75f
  private[this] val locks      = new Array[ReentrantLock](initialCapacity)
  private[this] val table      = new AtomicReferenceArray[Array[AnyRef]](initialCapacity)
  private[this] val size       = new AtomicInteger(0)
  private[this] val threshold  = (initialCapacity * loadFactor).toInt

  // Initialize locks and table
  (0 until initialCapacity).foreach { i =>
    locks(i) = new ReentrantLock()
    table.set(i, new Array[AnyRef](0))
  }

  def add(element: A): Boolean = {
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

  def remove(element: A): Boolean = {
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

  def contains(element: A): Boolean = {
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

  def size(): Int = size.get()

  def isEmpty: Boolean = size.get() == 0

  def clear(): Unit = {
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

  def iterator: Iterator[A] = new Iterator[A] {
    private[this] var currentIndex                 = 0
    private[this] var currentBucket: Array[AnyRef] = null
    private[this] var currentPos                   = 0

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

    def hasNext: Boolean = {
      findNext()
      currentBucket != null && currentPos < currentBucket.length
    }

    def next(): A = {
      if (!hasNext) throw new NoSuchElementException()
      val result = currentBucket(currentPos).asInstanceOf[A]
      currentPos += 1
      result
    }
  }
}
