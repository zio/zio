package zio.internal.metrics

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

private[zio] sealed abstract class ConcurrentSetCount {

  def getCount(): Long

  def observe(word: String): Unit

  def snapshot(): Chunk[(String, Long)]

}

private[zio] object ConcurrentSetCount {

  def manual(): ConcurrentSetCount =
    new ConcurrentSetCount {
      private[this] val count  = new LongAdder
      private[this] val values = new ConcurrentHashMap[String, LongAdder]

      def getCount(): Long = count.longValue()

      def observe(word: String): Unit = {
        count.increment()
        var slot = values.get(word)
        if (slot eq null) {
          val cnt = new LongAdder
          values.putIfAbsent(word, cnt)
          slot = values.get(word)
        }
        slot match {
          case la: LongAdder =>
            la.increment()
          case _ =>
        }
      }

      def snapshot(): Chunk[(String, Long)] = {
        val builder = ChunkBuilder.make[(String, Long)]()
        val it      = values.entrySet().iterator()
        while (it.hasNext()) {
          val e = it.next()
          builder += e.getKey() -> e.getValue().longValue()
        }

        builder.result()
      }
    }
}
