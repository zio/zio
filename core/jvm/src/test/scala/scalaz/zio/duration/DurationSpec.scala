package scalaz.zio.duration

import scalaz.zio.AbstractRTSSpec

class DurationSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "DurationSpec".title ^ s2"""
        Make a Duration from positive nanos and check that:
          The Duration is Finite                              $e1
          Copy with a negative nanos returns Infinity         $e2
          Multiplying with a negative factor returns Infinity $e3

        Make a Duration from negative nanos and check that:
          The Duration is Infinity                            $e4
     """

  def e1 =
    Duration.fromNanos(1) must haveClass[Duration.Finite]

  def e2 =
    Duration.fromNanos(1).asInstanceOf[Duration.Finite].copy(-1) must_=== Duration.Infinity

  def e3 =
    Duration.fromNanos(1) * -1.0 must_=== Duration.Infinity

  def e4 =
    Duration.fromNanos(-1) must_=== Duration.Infinity
}
