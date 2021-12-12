package zio.internal.metrics

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.mutable

private[zio] sealed abstract class ConcurrentSetCount {

  def getCount(): Long

  def getCount(word: String): Long

  def observe(word: String): Unit

  def snapshot(): Chunk[(String, Long)]

}

private[zio] object ConcurrentSetCount {

  def manual(): ConcurrentSetCount =
    new ConcurrentSetCount {
      private[this] var count  = 0L
      private[this] val values = new mutable.HashMap[String, Long]

      def getCount(): Long = count.longValue()

      def getCount(word: String): Long =
        values.getOrElse(word, 0L)

      def observe(word: String): Unit = {
        count = count + 1L
        values.put(word, values.getOrElse(word, 0L) + 1L)
        ()
      }

      def snapshot(): Chunk[(String, Long)] =
        Chunk.fromIterable(values)
    }
}
