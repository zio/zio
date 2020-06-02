package zio

import java.time.{ Duration, Instant }

sealed trait Schedule2[-R, -A, +B] { self =>
  type State 

  def && [R1 <: R, A1 <: A, C](that: Schedule2[R1, A1, C]): Schedule2[R1, A1, (B, C)]
}
object Schedule2 {
  type StepFunction[S, -A, +B] = (S, Duration, Instant, A) => (S, Instant, B)

  final case class Stateful[S, R, A, B](initial: S, step: StepFunction[S, A, B], tapInput: A => URIO[R, Any], tapOutput: B => URIO[R, Any]) extends Schedule2[R, A, B] { self =>
    type State = S

    def && [R1 <: R, A1 <: A, C](that: Schedule2[R1, A1, C]): Schedule2[R1, A1, (B, C)] = 
      that match {
        case that : Stateful[s, R1, A1, C] =>
          Stateful[(self.State, that.State), R1, A1, (B, C)](
            initial   = (self.initial, that.initial),
            step      = (tupleState, length, now, a1) => {
              val (selfState, selfNext, b) = self.step(tupleState._1, length, now, a1)
              val (thatState, thatNext, c) = that.step(tupleState._2, length, now, a1)

              val next: Instant = 
                if (selfNext.compareTo(thatNext) < 0) thatNext else selfNext

              ((selfState, thatState), next, (b, c))
            },
            tapInput  = a => self.tapInput(a) *> that.tapInput(a),
            tapOutput = t => self.tapOutput(t._1) *> that.tapOutput(t._2)
          )
      }
  }
}