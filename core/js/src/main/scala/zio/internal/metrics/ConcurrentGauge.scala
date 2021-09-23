package zio.internal.metrics

private[zio] sealed abstract class ConcurrentGauge {
  def get: Double
  def set(v: Double): (Double, Double)
  def adjust(v: Double): (Double, Double)
}

private[zio] object ConcurrentGauge {
  def manual(startAt: Double): ConcurrentGauge = new ConcurrentGauge {
    private[this] var value: Double = startAt

    def get: Double = value

    def set(v: Double): (Double, Double) = {
      val old = value
      value = v
      (v, v - old)
    }
    def adjust(v: Double): (Double, Double) = {
      value = value + v
      (value, v)
    }
  }
}
