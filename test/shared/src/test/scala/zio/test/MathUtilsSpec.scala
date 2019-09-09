package zio.test

import scala.concurrent.Future

import zio.DefaultRuntime
import zio.test.MathUtils._
import zio.test.TestUtils.label

object MathUtilsSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(logCeilComputesBaseTwoLogarithmRoundingUp, "logCeil computes base two logarithm rounding up"),
    label(logFloorComputesBaseTwoLogarithmRoundingDown, "logFloor computes base two logarithm rounding down"),
    label(pow2ComputesTwoRaisedToSpecifiedPower, "pow2 computes two raised to specified power")
  )

  def logCeilComputesBaseTwoLogarithmRoundingUp: Future[Boolean] =
    Future.successful {
      val positive = List
        .range(1, 100)
        .map(n => log2Ceil(n) == math.ceil((math.log(n.toDouble) / math.log(2))))
        .forall(identity)
      val zero     = log2Ceil(0) == 0
      val negative = log2Ceil(-1) == 0
      positive && zero && negative
    }

  def logFloorComputesBaseTwoLogarithmRoundingDown: Future[Boolean] =
    Future.successful {
      val positive = List
        .range(1, 100)
        .map(n => log2Floor(n) == math.floor((math.log(n.toDouble) / math.log(2))))
        .forall(identity)
      val zero     = log2Floor(0) == 0
      val negative = log2Floor(-1) == 0
      positive && zero && negative
    }

  def pow2ComputesTwoRaisedToSpecifiedPower: Future[Boolean] =
    Future.successful {
      val positive = List
        .range(1, 10)
        .map(n => pow2(n) == math.pow(2, n.toDouble))
        .forall(identity)
      val zero     = pow2(0) == 1
      val negative = pow2(-1) == 0
      positive && zero && negative
    }
}
