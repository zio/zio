package zio.internal

import zio.internal.ConcurrentWeakHashSet.Ref

import java.lang.ref.WeakReference
import scala.collection.mutable
import java.util.concurrent.locks.ReentrantLock
import java.lang.ref.ReferenceQueue
import java.util.concurrent.atomic.AtomicInteger

/*
 * This class is heavily inspired by the implementation of 'ConcurrentReferenceHashMap' ('spring-framework', Apache License, Version 2.0.) & `ConcurrentHashMap`
 */

object ConcurrentWeakHashSet {

  private[internal] val MaxConcurrencyLevel: Int     = 1 << 16
  private[internal] val MaxSegmentSize: Int          = 1 << 30
  private[internal] val DefaultInitialCapacity: Int  = 16
  private[internal] val DefaultLoadFactor: Float     = 0.75f
  private[internal] val DefaultConcurrencyLevel: Int = 16

  protected class Ref[V](
    val hash: Int,
    element: V,
    queue: ReferenceQueue[V],
    var nextRef: Ref[V]
  ) extends WeakReference[V](element, queue)

  protected object UpdateOptions extends Enumeration {
    type UpdateOption = Value
    val RestructureBefore, RestructureAfter, SkipIfEmpty, Resize = Value
  }

  protected object UpdateResults extends Enumeration {
    type UpdateResult = Value
    val None, AddElement, RemoveElement = Value
  }

  protected abstract class UpdateOperation[V](val options: UpdateOptions.UpdateOption*) {
    def execute(oldRef: Ref[V]): UpdateResults.UpdateResult
  }

}

class ConcurrentWeakHashSet[V](
  initialCapacity: Int = ConcurrentWeakHashSet.DefaultInitialCapacity,
  loadFactor: Float = ConcurrentWeakHashSet.DefaultLoadFactor,
  concurrencyLevel: Int = ConcurrentWeakHashSet.DefaultConcurrencyLevel
) extends Iterable[V] { self =>

  private val shift                    = calculateShift(concurrencyLevel, ConcurrentWeakHashSet.MaxConcurrencyLevel)
  private var segments: Array[Segment] = Array()

  {
    val size                     = 1 << shift
    val roundedUpSegmentCapacity = ((initialCapacity + size - 1L) / size).toInt
    val initialSize              = 1 << calculateShift(roundedUpSegmentCapacity, ConcurrentWeakHashSet.MaxSegmentSize)
    val segments                 = new Array[Segment](size)
    val resizeThreshold          = (initialSize * loadFactor).toInt
    segments.indices.foreach(i => segments(i) = new Segment(initialSize, resizeThreshold))
    this.segments = segments
  }

  override def isEmpty: Boolean =
    segments.forall(segment => segment.size() == 0)

  override def size(): Int =
    segments.map(segment => segment.size()).sum

  def gc(): Unit =
    segments.foreach(segment => segment.restructureIfNecessary(false))

  def add(element: V): Boolean =
    update(
      element,
      new ConcurrentWeakHashSet.UpdateOperation[V](
        ConcurrentWeakHashSet.UpdateOptions.RestructureBefore,
        ConcurrentWeakHashSet.UpdateOptions.Resize
      ) {
        override def execute(oldRef: Ref[V]): ConcurrentWeakHashSet.UpdateResults.UpdateResult =
          if (oldRef == null || oldRef.get() != element)
            ConcurrentWeakHashSet.UpdateResults.AddElement
          else
            ConcurrentWeakHashSet.UpdateResults.None
      }
    )

  def remove(element: V): Boolean =
    update(
      element,
      new ConcurrentWeakHashSet.UpdateOperation[V](
        ConcurrentWeakHashSet.UpdateOptions.RestructureAfter,
        ConcurrentWeakHashSet.UpdateOptions.SkipIfEmpty
      ) {
        override def execute(oldRef: Ref[V]): ConcurrentWeakHashSet.UpdateResults.UpdateResult =
          if (oldRef != null && oldRef.get() == element)
            ConcurrentWeakHashSet.UpdateResults.RemoveElement
          else
            ConcurrentWeakHashSet.UpdateResults.None
      }
    )

  override def iterator: Iterator[V] =
    new Iterator[V] {
      private var segmentIndex: Int   = 0
      private var referenceIndex: Int = 0

      private var references: Array[Ref[V]] = _
      private var reference: Ref[V]         = _
      private var nextValue: V              = null.asInstanceOf[V]
      private var lastValue: V              = null.asInstanceOf[V]

      // Init
      moveToNextSegment()

      override def hasNext: Boolean = {
        moveToNextIfNecessary()
        nextValue != null
      }

      override def next(): V = {
        moveToNextIfNecessary()
        if (this.nextValue == null) throw new NoSuchElementException()
        this.lastValue = this.nextValue
        this.nextValue = null.asInstanceOf[V]
        this.lastValue
      }

      private def moveToNextIfNecessary(): Unit =
        while (this.nextValue == null) {
          moveToNextReference()
          if (this.reference == null) return
          this.nextValue = this.reference.get()
        }

      private def moveToNextReference(): Unit = {
        if (this.reference != null) {
          this.reference = this.reference.nextRef
        }
        while (this.reference == null && this.references != null) {
          if (this.referenceIndex >= this.references.length) {
            moveToNextSegment()
            this.referenceIndex = 0
          } else {
            this.reference = this.references(this.referenceIndex)
            this.referenceIndex += 1
          }
        }
      }

      private def moveToNextSegment(): Unit = {
        this.reference = null
        this.references = null
        if (this.segmentIndex < self.segments.length) {
          this.references = self.segments(this.segmentIndex).references
          this.segmentIndex += 1
        }
      }
    }

  private def update(value: V, update: ConcurrentWeakHashSet.UpdateOperation[V]): Boolean = {
    val hash = this.getHash(value)
    this.getSegment(hash).update(hash, value, update)
  }

  private def calculateShift(minimumValue: Int, maximumValue: Int): Int = {
    var shift = 0
    var value = 1
    while (value < minimumValue && value < maximumValue) {
      value <<= 1
      shift += 1
    }
    shift
  }

  /* Enhanced hashing same as in standard ConcurrentHashMap (Wang/Jenkins algorithm) */
  private def getHash(value: V): Int = {
    var hash = if (value != null) value.hashCode() else 0
    hash += (hash << 15) ^ 0xffffcd7d
    hash ^= (hash >>> 10)
    hash += (hash << 3)
    hash ^= (hash >>> 6)
    hash += (hash << 2) + (hash << 14)
    hash ^= (hash >>> 16)
    hash
  }

  private def getSegment(hash: Int): Segment =
    this.segments((hash >>> (32 - this.shift)) & (this.segments.length - 1))

  private class Segment(initialSize: Int, var resizeThreshold: Int) extends ReentrantLock {

    private val queue        = new ReferenceQueue[V]()
    private val counter      = new AtomicInteger(0)
    @volatile var references = new Array[Ref[V]](initialSize)

    def getReference(value: V, hash: Int): Ref[V] = {
      if (this.counter.get() == 0) return null
      val localReferences = this.references // read volatile
      val index           = getIndex(localReferences, hash)
      val head            = localReferences(index)
      findInChain(head, value, hash)
    }

    private def getIndex(refs: Array[_], hash: Int): Int =
      hash & (refs.length - 1)

    private def findInChain(headReference: Ref[V], element: V, hash: Int): Ref[V] = {
      var currentReference = headReference
      while (currentReference != null) {
        if (currentReference.hash == hash && currentReference.get() == element) {
          return currentReference
        }
        currentReference = currentReference.nextRef
      }
      null
    }

    def update[T](hash: Int, element: V, update: ConcurrentWeakHashSet.UpdateOperation[V]): Boolean = {
      val resize = update.options.contains(ConcurrentWeakHashSet.UpdateOptions.Resize)
      if (update.options.contains(ConcurrentWeakHashSet.UpdateOptions.RestructureBefore)) {
        restructureIfNecessary(resize)
      }
      val skipIfEmpty = update.options.contains(ConcurrentWeakHashSet.UpdateOptions.SkipIfEmpty)
      if (skipIfEmpty && this.counter.get() == 0) {
        return update.execute(null) != ConcurrentWeakHashSet.UpdateResults.None
      }
      lock()
      try {
        val index     = getIndex(this.references, hash)
        val head      = this.references(index)
        val storedRef = findInChain(head, element, hash)
        update.execute(storedRef) match {
          case ConcurrentWeakHashSet.UpdateResults.None =>
            false
          case ConcurrentWeakHashSet.UpdateResults.AddElement =>
            val newRef = new Ref[V](hash, element, this.queue, head)
            this.references(index) = newRef
            this.counter.incrementAndGet()
            true
          case ConcurrentWeakHashSet.UpdateResults.RemoveElement =>
            var previousRef = null.asInstanceOf[Ref[V]]
            var currentRef  = storedRef
            while (currentRef != null) {
              if (currentRef.get() == element) {
                this.counter.decrementAndGet()
                if (previousRef == null) {
                  this.references(index) = currentRef.nextRef // start chain with next ref
                } else {
                  previousRef.nextRef = currentRef.nextRef // skip current ref
                }
                currentRef = null
              } else {
                previousRef = currentRef
                currentRef = currentRef.nextRef
              }
            }
            true
        }
      } finally {
        unlock()
        if (update.options.contains(ConcurrentWeakHashSet.UpdateOptions.RestructureAfter)) {
          restructureIfNecessary(resize)
        }
      }
    }

    def restructureIfNecessary(allowResize: Boolean): Unit = {
      val currentCount = this.counter.get()
      val needsResize  = allowResize && (currentCount > 0 && currentCount >= this.resizeThreshold)
      val ref          = this.queue.poll().asInstanceOf[Ref[V]]
      if (ref != null || needsResize) {
        this.restructure(allowResize, ref)
      }
    }

    private def restructure(allowResize: Boolean, firstRef: Ref[V]): Unit = {
      lock()
      try {
        val toPurge =
          if (firstRef != null) {
            val refs       = mutable.Set[Ref[V]]()
            var refToPurge = firstRef
            while (refToPurge != null) {
              refs.add(refToPurge)
              refToPurge = this.queue.poll().asInstanceOf[Ref[V]]
            }
            refs
          } else Set[Ref[V]]()

        val countAfterRestructure = this.counter.get() - toPurge.size
        val needsResize           = (countAfterRestructure > 0 && countAfterRestructure >= this.resizeThreshold)
        var restructureSize       = this.references.length
        val resizing              = allowResize && needsResize && restructureSize < ConcurrentWeakHashSet.MaxSegmentSize

        if (resizing) {
          restructureSize = restructureSize << 1
        }

        val restructured = if (resizing) new Array[Ref[V]](restructureSize) else this.references

        for (idx <- this.references.indices) {
          var currentRef = this.references(idx)
          if (!resizing) {
            restructured(idx) = null
          }
          while (currentRef != null) {
            if (!toPurge.contains(currentRef)) {
              val currentRefValue = currentRef.get()
              if (currentRefValue != null) {
                val currentRefIndex = getIndex(restructured, currentRef.hash)
                val previousRef     = restructured(currentRefIndex)
                restructured(currentRefIndex) = new Ref(currentRef.hash, currentRefValue, this.queue, previousRef)
              }
            }
            currentRef = currentRef.nextRef
          }
        }

        if (resizing) {
          this.references = restructured
          this.resizeThreshold = (restructured.length * loadFactor).toInt
        }

        this.counter.set(0.max(countAfterRestructure))
      } finally {
        unlock()
      }
    }

    def size(): Int =
      this.counter.get()

  }

}
