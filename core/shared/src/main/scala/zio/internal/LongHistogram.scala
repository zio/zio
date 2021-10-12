package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic._

final class LongHistogram private (val granularity: Int, val maximum: Long) {
  val size: Int                = (maximum.toDouble / granularity.toDouble).ceil.toInt
  val counts: Array[LongAdder] = (0 to size).map(_ => new LongAdder()).toArray
  val width: Long              = maximum / size

  def add(sample: Long): Unit = {
    val index = (sample / width).toInt

    if (index >= size) counts(size).increment()
    else counts(index).increment()

    ()
  }

  def snapshot(): Seq[Long] = {
    val dest = Array.ofDim[Long](counts.length)
    var i    = 0
    while (i < dest.length) {
      dest(i) = counts(i).sum()
      i = i + 1
    }
    dest.toIndexedSeq
  }
}
object LongHistogram {
  def make(granularity: Int, maximum: Long): LongHistogram =
    new LongHistogram(granularity, maximum)
}
