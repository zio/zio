package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.LongAdder
import scala.annotation.tailrec

final class FiniteHistogram[A]() {
  private val concurrentMap = new java.util.concurrent.ConcurrentHashMap[A, LongAdder]()

  @tailrec
  def add(sample: A): Unit = {
    val adder = concurrentMap.get(sample)

    if (adder == null) {
      concurrentMap.putIfAbsent(sample, new LongAdder())
      add(sample)
    } else {
      adder.increment()
    }
  }

  def snapshot(): Map[A, Long] = {
    val map = scala.collection.mutable.Map.empty[A, Long]

    val iterator = concurrentMap.entrySet().iterator()

    while (iterator.hasNext()) {
      val entry = iterator.next()

      map.update(entry.getKey(), entry.getValue().sum())
    }

    map.toMap
  }
}
object FiniteHistogram {
  def make[A](): FiniteHistogram[A] = new FiniteHistogram[A]()
}
