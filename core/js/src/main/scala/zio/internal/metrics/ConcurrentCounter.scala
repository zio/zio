package zio.internal.metrics

private[zio] sealed abstract class ConcurrentCounter {
  def count: Double
  def increment(v: Double): (Double, Double)
}

private[zio] object ConcurrentCounter {
  def manual(): ConcurrentCounter = new ConcurrentCounter {
    private[this] var value: Double = 0.0

    def count: Double = value

    def increment(v: Double): (Double, Double) = {
      value = value + v
      (value, v)
    }
  }
}
