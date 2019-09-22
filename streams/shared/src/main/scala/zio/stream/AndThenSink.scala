package zio.stream

import zio.stream.AndThenSink.AndThenState
import zio.{Chunk, ZIO}

class AndThenSink[R1 <: R, R, E1 >: E, E, A, B, C](val self: ZSink[R, E, A, A, B], val that: ZSink[R1, E1, B, B, C])
  extends ZSink[R1, E1, A, A, C]{

  override type State = AndThenState[self.State, that.State, A, B]

  def State(state1: self.State, leftovers1: Chunk[A], state2: that.State, leftovers2: Chunk[B]): State =
    AndThenState(state1, leftovers1, state2, leftovers2)

  /**
   * Runs the sink from an initial state and produces a final value
   * of type `B`
   */
  override def extract(state: State): ZIO[R1, E1, (C, Chunk[A])] = for {
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
  override def cont(state: AndThenState[self.State, that.State, A, B]): Boolean = {
    self.cont(state.state1) && that.cont(state.state2)
  }

  /**
   * Steps through one iteration of the sink.
   */
  override def step(state: AndThenState[self.State, that.State, A, B], a: A): ZIO[R1, E1, AndThenState[self.State, that.State, A, B]] = for {
    s0 <- self.initial
    s1 <- self.step(s0, a)
    res <- self.extract(s1)
    (result, leftover) = res
    s2 <- that.step(state.state2, result)
  } yield AndThenState(s1, state.leftovers1 ++ leftover, s2, state.leftovers2)

//  private def fillSelf(state: self.State, leftover: Chunk[A]): ZIO[R, E, self.State] = {
//    val res = if(self.cont(state) && !leftover.isEmpty) for {
//      s <- self.step(state, leftover.apply(0))
//    } yield fillSelf(s, leftover.drop(1))
//    else
//  }

}

object AndThenSink {
  case class AndThenState[S1, S2, A, B](state1: S1, leftovers1: Chunk[A], state2: S2, leftovers2: Chunk[B]) {
    override def toString: String = s"State[state1: $state1, state2: $state2, leftovers1: $leftovers1, leftovers2: $leftovers2]"
  }
}


