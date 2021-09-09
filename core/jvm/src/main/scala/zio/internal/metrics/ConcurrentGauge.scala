package zio.internal.metrics

import java.util.concurrent.atomic.AtomicReference

private[zio] sealed abstract class ConcurrentGauge {
  def get: Double
  def set(v: Double): (Double, Double)
  def adjust(v: Double): (Double, Double)
}

private[zio] object ConcurrentGauge {
  def manual(startAt: Double): ConcurrentGauge = new ConcurrentGauge {
    private[this] val value: AtomicReference[Double] = new AtomicReference[Double](startAt)

    def get: Double = value.get()

    def set(v: Double): (Double, Double) = {
      val old = value.getAndSet(v)
      (v, v - old)
    }
    def adjust(v: Double): (Double, Double) =
      (value.updateAndGet(_ + v), v)
  }
}
