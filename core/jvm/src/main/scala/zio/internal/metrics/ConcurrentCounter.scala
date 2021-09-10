package zio.internal.metrics

import java.util.concurrent.atomic.DoubleAdder

private[zio] sealed abstract class ConcurrentCounter {
  def count: Double
  def increment(v: Double): (Double, Double)
}

private[zio] object ConcurrentCounter {
  def manual(): ConcurrentCounter = new ConcurrentCounter {
    private[this] val value: DoubleAdder = new DoubleAdder

    def count: Double =
      value.doubleValue()
    def increment(v: Double): (Double, Double) = {
      value.add(v)
      (value.sum(), v)
    }
  }
}
