package scalaz.zio.delay
import scalaz.zio.ZIO
import scalaz.zio.clock.{Clock, _}
import scalaz.zio.delay.DelayComparison._

sealed trait DelayComparison { self =>
  def choose: ZIO[Clock, Nothing, Delay] = self match {
    case Choose(delay)      => clockService.map(_ => delay)
    case TimesFactor(dc, fac) => dc.choose.map(_ * fac)
    case Min(l, r) => l.choose.zip(r.choose).flatMap{ case (l1, r1) => (l1 < r1).map(min => if (min) l1 else r1 )}
    case Max(l, r) => Min(r, l).choose
    case Sum(l, r) => l.choose.zip(r.choose).map{ case (l1, r1) => l1 + r1}
  }

  def +(that: DelayComparison): DelayComparison = Sum(self, that)
  def *(that: Double): DelayComparison = TimesFactor(self, that)

}
object DelayComparison {
  final case class Choose(value: Delay)                          extends DelayComparison
  final case class Min(l: DelayComparison, r: DelayComparison)   extends DelayComparison
  final case class Max(l: DelayComparison, r: DelayComparison)   extends DelayComparison
  final case class Sum(l: DelayComparison, r: DelayComparison)   extends DelayComparison
  final case class TimesFactor(l: DelayComparison, r: Double) extends DelayComparison

  def delay(value: Delay): DelayComparison        = Choose(value)
}
