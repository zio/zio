package zio

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/**
 * https://arxiv.org/pdf/2010.14189.pdf
 * * the Jiffy paper describes an unbounded queue which is useful but we also want a bounded backpressuring queue.
 * So we'll have to think how to layer that mechanism on the Jiffy queue if we use it
 * interesting operations we should support beyond poll/offer:
 *  offerChunk,
 *  pollUpTo(n: Int) (polls up to N elements out of the queue),
 *  pollEverything (grabs everything in the queue)
 * these blog posts are essential tribal knowledge for writing efficient concurrent data structures on the JVM:
 *  http://psy-lob-saw.blogspot.com/2013/05/know-thy-java-object-memory-layout.html,
 *  https://mechanical-sympathy.blogspot.com/2011/08/false-sharing-java-7.html,
 *  https://mechanical-sympathy.blogspot.com/2011/07/false-sharing.html.
 * I need to find some good resources on memory barriers on the JVM because those are important too
 * the JCTools implementations of MPSC bounded/unbounded queues are also interesting
 * https://github.com/JCTools/JCTools/tree/master/jctools-core/src/main/java/org/jctools/queues
 * we need to design a good set of benchmarks to work with. probably the most interesting benchmark is N fibers offering M elements and one fiber reading everything
 */
class MPSCQueue[A] {

  val BUFFER_SIZE = 100

  // FIXME I would prefer something nicer but this is used as AtomicInteger
  object State {
    val EMPTY = 0
    val SET = 1
    val HANDLED = 2
  }

  class Node(
    var data: A
  ){
    // isSet has three values: empty, set and handled.
    val isSet: AtomicInteger = new AtomicInteger(State.EMPTY)
  }

  class BufferList (
     val positionInQueue: Short,
     var prev: BufferList
  ) {
     var head: Short = 0
     val currBuffer: Array[Node] = Array.fill(BUFFER_SIZE)(new Node(null.asInstanceOf[A]))
     val next: AtomicReference[BufferList] = new AtomicReference(null)
  }

  var headOfQueue: BufferList = ???
  var tailOfQueue: AtomicReference[BufferList] = ???
  val tail: AtomicInteger = new AtomicInteger(0)
  var takePromise: Promise[Nothing, A] = null

  val short_one: Short = 1
  def inc(short: Short): Short =
    (short + 1).toShort


  /**
   * Places one value in the queue.
   */
  def offer(a: A): UIO[Boolean] =
  // FIXME this Promise was not in paper
    ZIO.whenM(
      if(takePromise != null)
        takePromise.succeed(a) as false
      else ZIO.succeed(true)
    )(

    UIO {
    val location = tail.incrementAndGet()
    var isLastBuffer = false
    var tempTail = tailOfQueue.get()

    //  while the location is in an unallocated buffer do
    //  allocate a new buffer and try to adding it to the queue with a CAS on tailOfQueue
    var numElements = BUFFER_SIZE * headOfQueue.positionInQueue
    while(location > numElements) {
      //location is in the next buffer
      if(tempTail.next.get() == null) {
        //buffer not yet exist in the queue
        val newArr = new BufferList(inc(tempTail.positionInQueue), tempTail)
        if(tempTail.next.compareAndSet(null, newArr)) {
          tailOfQueue.compareAndSet(tempTail, newArr)
        }
      }

      tempTail = tailOfQueue.get()
      numElements = BUFFER_SIZE * headOfQueue.positionInQueue
    }

    //calculating the amount of item in the queue - the current buffer
    // this was there
//    var prevSize = BUFFER_SIZE * (tempTail.positionInQueue - 1)
      // but this might be faster
    var prevSize = numElements - BUFFER_SIZE
    while(location != prevSize) {
      //location is in a previous buffer from the buffer pointed by tail
      tempTail = tempTail.prev
      // this was there
//      prevSize = BUFFER_SIZE * (tempTail.positionInQueue - 1)
      // but this might be faster
      prevSize = prevSize - BUFFER_SIZE
      isLastBuffer = false
    }
    // location is in this buffer
    val n = tempTail.currBuffer(location - prevSize)
    if(n.isSet.get() == State.EMPTY) {
      n.data = a
      n.isSet.set(State.SET)
      // FIXME index
//      if(index == 1 && isLastBuffer) {
      if(isLastBuffer) {
        //allocating a new buffer and adding it to the queue
        val newArr = new BufferList(inc(tempTail.positionInQueue), tempTail)
        tempTail.next.compareAndSet(null, newArr)
      }
      true
    }
    else
      false
  }) as true

  def take: UIO[A] = UIO {
    var n = headOfQueue.currBuffer(headOfQueue.head)
    // find first non-handled item
    while (n.isSet.get() == State.HANDLED) {
      headOfQueue.head = inc(headOfQueue.head)
      val res = moveToNextBuffer() // if at end of buffer, skip to next one
      if (res == null) {
        // reached end of queue and it is empty
        // FIXME what now? we would need to retrigger after someone offers an element
        // return false
      }
      n = headOfQueue.currBuffer(headOfQueue.head) // n points to the beginning of the queue
      // check if the queue is empty
      if ((headOfQueue == tailOfQueue.get()) && (headOfQueue.head == tail.get() % BUFFER_SIZE)) {
        // FIXME what now?
        // return false
      }
      if (n.isSet.get() == State.SET) { // if the first element is set, dequeue and return it
        headOfQueue.head = inc(headOfQueue.head)
        moveToNextBuffer()
        //FIXME here we should return data
        data = n.data
        return true
      }
      if (n.isSet.get() == State.EMPTY) { // otherwise, scan and search for a set element
        var tempHeadOfQueue = headOfQueue
        var tempHead = headOfQueue.head
        var tempN = tempHeadOfQueue.currBuffer(tempHead)
        var res = Scan(tempHeadOfQueue, tempHead, tempN)
        if (res == null) {
          return false; // if none was found, we return empty
        }
        //here tempN == set (if we reached the end of the queue we already returned false)
        Rescan(headOfQueue, tempHeadOfQueue, tempHead, tempN) // perform the rescan
        // tempN now points to the first set element – remove tempN
        //FIXME here we should return data
        data = tempN.data
        tempN.isSet.set(State.HANDLED)
        if (tempHeadOfQueue == headOfQueue && tempHead == head) { //tempN ==n then
          headOfQueue.head = inc(headOfQueue.head)
          moveToNextBuffer()
        }
        return true
      }
    }
  }

  /**
   * Folding a fully handled buffer in the middle of the queue
   */
  def fold(tempHeadOfQueue: BufferList, tempHead: Int, flag_moveToNewBuffer: Boolean, flag_bufferAllHandeld: Boolean) = {
    if(tempHeadOfQueue == tailOfQueue.get() {
      return false // the queue is empty – we reached the tail of the queue
    }
    val next = tempHeadOfQueue.next.get()
    val prev = tempHeadOfQueue.prev
    if(next == null) {
      return false // we do not have where to move
    }
    // shortcut this buffer and delete it
    next.prev = prev
    prev.next.set(next)
//    delete[] tempHeadOfQueue→currbuffer
//    garbageList.addLast(tempHeadOfQueue)
    tempHeadOfQueue = next
    tempHead = tempHeadOfQueue.head
    flag_bufferAllHandeld = true
    flag_moveToNewBuffer = true
    return true
  }

  /**
   * helper function to advance to the next buffer
   */
  private def moveToNextBuffer(): Boolean = {
    if (headOfQueue.head >= BUFFER_SIZE) {
      if (headOfQueue == tailOfQueue.get()) {
        false
      }
      var next = headOfQueue.next.get()
      if (next == null) {
        false
      }
      // FIXME what is garbageList
      var g: BufferList = garbageList.getFirst()
      while (g.positionInQueue != next.positionInQueue) {
        garbageList.popFirst()
        //FIXME remove references to g
        //        delete g
        g = garbageList.getFirst()
      }
      // FIXME delete headOfQueue
      headOfQueue = next
    }
    true
  }

  /**
   * Scan the queue from n searching for a set element – return false on failure
   */
  def scan(_tempHeadOfQueue: BufferList, _tempHead: Int, tempN: Node) = {
    var tempHeadOfQueue = _tempHeadOfQueue
    var tempHead = _tempHead
    var flag_moveToNewBuffer = false
    var flag_bufferAllHandeld = true
    while(tempN.isSet.get() != State.SET) {
    tempHead = tempHead + 1
      if(tempN.isSet.get() != State.HANDLED) {
        flag_bufferAllHandeld = false
      }
      if(tempHead >= BUFFER_SIZE) { // we reach the end of the buffer – move to the next
        if(flag_bufferAllHandeld && flag_moveToNewBuffer) { // fold fully handled buffers
          val res = fold(tempHeadOfQueue, tempHead, flag_moveToNewBuffer, flag_bufferAllHandeld)
          if(!res) {
            return false
          }
        } else {
          // there is an empty element in the buffer, so we can’t delete it; move to the next buffer
          val next = tempHeadOfQueue.next.get()
          if(next == null) {
            return false // we do not have where to move
          }
          tempHeadOfQueue = next
          tempHead = tempHeadOfQueue.head
          flag_bufferAllHandeld = true
          flag_moveToNewBuffer = true

        }
      }
    }
  }

  /**
   * Rescan to find an element between n and tempN that changed from empty to set
   */
  def rescan(headOfQueue: BufferList, _tempHeadOfQueue: BufferList, _tempHead: Int, _tempN: Node): Unit = {
    var tempHeadOfQueue = _tempHeadOfQueue
    var tempHead = _tempHead
    var tempN = _tempN
    // we need to scan until one place before tempN
    var scanHeadOfQueue = headOfQueue
    var scanHead = scanHeadOfQueue.head
    //FIXME what is this --, OR, ==?
    // scanHeadOfQueue != tempHeadOfQueue —— scanHead ¡ (tempHead-1)
    while(scanHeadOfQueue != tempHeadOfQueue || scanHead != (tempHead-1)) {
      if(scanHead >= BUFFER_SIZE) { // at the end of a buffer, skip to the next one
        scanHeadOfQueue = scanHeadOfQueue.next.get()
        scanHead = scanHeadOfQueue.head
      }
      val scanN = scanHeadOfQueue.currBuffer(scanHead)
      // there is a closer element to n that is set – mark it and restart the loop from n
      if(scanN.isSet.get() == State.SET) {
        tempHead = scanHead
        tempHeadOfQueue = scanHeadOfQueue
        tempN = scanN
        scanHeadOfQueue = headOfQueue
        scanHead = scanHeadOfQueue.head
      }

      scanHead = inc(scanHead)
    }

  }

}

object MPSCQueue {

  def make[A]: UIO[MPSCQueue[A]] = UIO(new MPSCQueue())
}
