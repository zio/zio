package scalaz.zio.delay
import scalaz.zio.ZIO
import scalaz.zio.clock.Clock
import scalaz.zio.clock._
import scalaz.zio.delay.DelayComparison._

sealed trait DelayComparison { self =>
  def choose: ZIO[Clock, Nothing, Delay] = self match {
    case Choose(delay) => clockService.map(_ => delay)
  }

  def +(that: DelayComparison) = Sum(self, that)
}
object DelayComparison {
  final case class Choose(value: Delay)                        extends DelayComparison
  final case class Min(l: DelayComparison, r: DelayComparison) extends DelayComparison
  final case class Max(l: DelayComparison, r: DelayComparison) extends DelayComparison
  final case class Sum(l: DelayComparison, r: DelayComparison) extends DelayComparison
}
