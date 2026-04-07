/*
 * Lock-free unbounded MPMC queue.
 * Port of JCTools MpmcUnboundedXaddArrayQueue via Kyo (getkyo/kyo).
 * License: Apache 2.0
 *
 * Uses getAndIncrement (XADD) instead of CAS loops for producer slot
 * allocation. Linked chunks of arrays reduce per-element allocation
 * compared to ConcurrentLinkedQueue (one Node per offer).
 */
package zio.internal

import java.util.concurrent.atomic.{AtomicLong, AtomicLongArray, AtomicReference, AtomicReferenceArray}
import scala.annotation.tailrec
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] object UnboundedMpmcQueue {
  def apply[A <: AnyRef](chunkSize: Int): UnboundedMpmcQueue[A] =
    new UnboundedMpmcQueue[A](chunkSize)

  private final class Chunk(
    @volatile var index: Long,
    chunkCapacity: Int
  ) {
    val buffer: AtomicReferenceArray[AnyRef] = new AtomicReferenceArray[AnyRef](chunkCapacity)
    val prev: AtomicReference[Chunk]         = new AtomicReference[Chunk](null)
    val next: AtomicReference[Chunk]         = new AtomicReference[Chunk](null)
  }

  private def roundToPowerOfTwo(value: Int): Int =
    1 << (32 - Integer.numberOfLeadingZeros(value - 1))
}

private[zio] final class UnboundedMpmcQueue[A <: AnyRef] private (chunkSize: Int) {
  import UnboundedMpmcQueue._

  private[this] val chunkCapacity = roundToPowerOfTwo(Math.max(8, chunkSize))
  private[this] val chunkMask     = chunkCapacity - 1
  private[this] val chunkShift    = Integer.numberOfTrailingZeros(chunkCapacity)
  private[this] val initialChunk  = new Chunk(0L, chunkCapacity)

  private[this] val producerIndex      = new AtomicLong(0L)
  private[this] val consumerIndex      = new AtomicLong(0L)
  private[this] val producerChunk      = new AtomicReference[Chunk](initialChunk)
  private[this] val producerChunkIndex = new AtomicLong(0L)
  private[this] val consumerChunk      = new AtomicReference[Chunk](initialChunk)

  def size(): Int = {
    @tailrec def loop(after: Long): Int = {
      val before   = after
      val pIndex   = producerIndex.get()
      val newAfter = consumerIndex.get()
      if (before == newAfter) {
        val size = pIndex - newAfter
        Math.max(0L, size).toInt
      } else loop(newAfter)
    }
    loop(consumerIndex.get())
  }

  def offer(a: A): Unit = {
    val pIdx          = producerIndex.getAndIncrement()
    val piChunkOffset = (pIdx & chunkMask).toInt
    val piChunkIndex  = pIdx >> chunkShift

    var pChunk = producerChunk.get()
    if (pChunk.index != piChunkIndex)
      pChunk = producerChunkForIndex(pChunk, piChunkIndex)

    pChunk.buffer.lazySet(piChunkOffset, a)
  }

  @tailrec
  private def producerChunkForIndex(startChunk: Chunk, requiredChunkIndex: Long): Chunk = {
    val cc                = if (startChunk == null) producerChunk.get() else startChunk
    val currentChunkIndex = cc.index
    val jumpBackward      = currentChunkIndex - requiredChunkIndex
    if (jumpBackward >= 0) {
      @tailrec def walkBack(chunk: Chunk, i: Long): Chunk =
        if (i >= jumpBackward) chunk
        else {
          val p = chunk.prev.get()
          if (p == null) null else walkBack(p, i + 1)
        }
      val found = walkBack(cc, 0L)
      if (found != null) found
      else producerChunkForIndex(null, requiredChunkIndex)
    } else {
      if (producerChunkIndex.get() == currentChunkIndex) {
        val appended = appendNextChunks(cc, currentChunkIndex, (-jumpBackward).toInt)
        if (appended != null) appended
        else producerChunkForIndex(null, requiredChunkIndex)
      } else producerChunkForIndex(null, requiredChunkIndex)
    }
  }

  private[this] val ROTATION = Long.MinValue

  private def appendNextChunks(currentChunk: Chunk, currentChunkIndex: Long, chunksToAppend: Int): Chunk = {
    if (!producerChunkIndex.compareAndSet(currentChunkIndex, ROTATION))
      return null

    @tailrec def appendLoop(chunk: Chunk, i: Int): Chunk =
      if (i >= chunksToAppend) chunk
      else {
        val newChunkIndex = currentChunkIndex + i + 1
        val newChunk      = new Chunk(newChunkIndex, chunkCapacity)
        newChunk.prev.lazySet(chunk)
        chunk.next.lazySet(newChunk)
        producerChunk.set(newChunk)
        appendLoop(newChunk, i + 1)
      }
    val result = appendLoop(currentChunk, 0)
    producerChunkIndex.set(currentChunkIndex + chunksToAppend)
    result
  }

  /** Poll the head element, or return null if the queue is empty. */
  def poll(): A = {
    @tailrec def loop(pIndex: Long): A = {
      val cIdx          = consumerIndex.get()
      val ciChunkOffset = (cIdx & chunkMask).toInt
      val ciChunkIndex  = cIdx >> chunkShift
      val cChunk        = consumerChunk.get()
      val ccChunkIndex  = cChunk.index

      if (ciChunkOffset == 0 && cIdx != 0) {
        // First element of new chunk — check element exists before CAS
        if (ciChunkIndex - ccChunkIndex != 1) loop(pIndex)
        else {
          val next = cChunk.next.get()
          if (next == null) {
            if (cIdx >= pIndex) {
              val newPIndex = producerIndex.get()
              if (cIdx == newPIndex) null.asInstanceOf[A]
              else loop(newPIndex)
            } else loop(pIndex)
          } else {
            val e = next.buffer.get(ciChunkOffset)
            if ((e ne null) && consumerIndex.compareAndSet(cIdx, cIdx + 1)) {
              cChunk.next.lazySet(null)
              next.prev.lazySet(null)
              consumerChunk.lazySet(next)
              next.buffer.lazySet(ciChunkOffset, null)
              e.asInstanceOf[A]
            } else loop(pIndex)
          }
        }
      } else if (ccChunkIndex > ciChunkIndex) {
        loop(pIndex)
      } else if (ccChunkIndex == ciChunkIndex) {
        val e = cChunk.buffer.get(ciChunkOffset)
        if (e ne null) {
          if (consumerIndex.compareAndSet(cIdx, cIdx + 1)) {
            cChunk.buffer.lazySet(ciChunkOffset, null)
            e.asInstanceOf[A]
          } else loop(pIndex)
        } else {
          if (cIdx >= pIndex) {
            val newPIndex = producerIndex.get()
            if (cIdx == newPIndex) null.asInstanceOf[A]
            else loop(newPIndex)
          } else loop(pIndex)
        }
      } else loop(pIndex)
    }
    loop(-1L)
  }

  /** Peek at the head element without removing, or return null if empty. */
  def peek(): A = {
    @tailrec def loop(pIndex: Long): A = {
      val cIdx          = consumerIndex.get()
      val ciChunkOffset = (cIdx & chunkMask).toInt
      val ciChunkIndex  = cIdx >> chunkShift
      val cChunk        = consumerChunk.get()
      val ccChunkIndex  = cChunk.index

      if (ccChunkIndex == ciChunkIndex) {
        val e = cChunk.buffer.get(ciChunkOffset)
        if ((e ne null) && consumerIndex.get() == cIdx) e.asInstanceOf[A]
        else {
          if (cIdx >= pIndex) {
            val newPIndex = producerIndex.get()
            if (cIdx >= newPIndex) null.asInstanceOf[A]
            else loop(newPIndex)
          } else loop(pIndex)
        }
      } else if (ccChunkIndex < ciChunkIndex) {
        @tailrec def walkForward(chunk: Chunk, ci: Long): Chunk =
          if (ci >= ciChunkIndex) chunk
          else {
            val nextChunk = chunk.next.get()
            if (nextChunk == null) null else walkForward(nextChunk, ci + 1)
          }
        val targetChunk = walkForward(cChunk, ccChunkIndex)
        if (targetChunk == null || consumerIndex.get() != cIdx) loop(pIndex)
        else {
          val e = targetChunk.buffer.get(ciChunkOffset)
          if (e ne null) e.asInstanceOf[A]
          else loop(pIndex)
        }
      } else {
        if (cIdx >= pIndex) {
          val newPIndex = producerIndex.get()
          if (cIdx >= newPIndex) null.asInstanceOf[A]
          else loop(newPIndex)
        } else if (consumerIndex.get() != cIdx) loop(pIndex)
        else loop(pIndex)
      }
    }
    loop(-1L)
  }
}
