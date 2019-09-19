package zio.stream

import zio.stream.AndThenSink.AndThenState
import zio.{Chunk, ZIO}

class AndThenSink[R1 <: R, R, E1 >: E, E, A, B, A0, C, A00](val self: ZSink[R, E, A0, A, B], val that: ZSink[R1, E1, B, B, C])
  extends ZSink[R1, E1, A0, A, C]{

  override type State = AndThenState[self.State, that.State, A0, B]

  def State(state1: self.State, leftovers1: Chunk[A0], state2: that.State, leftovers2: Chunk[B]): State =
    AndThenState(state1, leftovers1, state2, leftovers2)

  /**
   * Runs the sink from an initial state and produces a final value
   * of type `B`
   */
  override def extract(state: State): ZIO[R1, E1, (C, Chunk[A0])] = for {
    s2 <- that.extract(state.state2)
  } yield s2._1 -> Chunk.empty // todo is this correct ???

  /**
   * The initial state of the sink.
   */
  override def initial: ZIO[R1, E1, State] = {
    for {
      i1 <- self.initial
      i2 <- that.initial
    } yield State(i1, Chunk.empty, i2, Chunk.empty)
  }

  /**
   * Decides whether the Sink should continue from the current state.
   */
  override def cont(state: AndThenState[self.State, that.State, A0, B]): Boolean = {
    self.cont(state.state1) && that.cont(state.state2)
  }

  /**
   * Steps through one iteration of the sink.
   */
  override def step(state: AndThenState[self.State, that.State, A0, B], a: A): ZIO[R1, E1, AndThenState[self.State, that.State, A0, B]] = for {
    s0 <- self.initial
    s1 <- self.step(s0, a)
    b <- self.extract(s1)
    s2 <- that.step(state.state2, b._1)
  } yield AndThenState(s1, state.leftovers1 ++ b._2, s2, state.leftovers2)

}

object AndThenSink {
  case class AndThenState[S1, S2, A, B](state1: S1, leftovers1: Chunk[A], state2: S2, leftovers2: Chunk[B]) {
    override def toString: String = s"State[state1: $state1, state2: $state2, leftovers1: $leftovers1, leftovers2: $leftovers2]"
  }
}


