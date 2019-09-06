package zio.stream

import zio.stream.AndThenSink.AndThenState
import zio.stream.ZSink.Step
import zio.{Chunk, UIO, ZIO}

class AndThenSink[R1 <: R, R, E1 >: E, E, A, B, A0, C, A00, BB <: B](val self: ZSink[R, E, A0, A, B], val that: ZSink[R1, E1, BB, B, C])
  extends ZSink[R1, E1, A0, A, C]{

  override type State = AndThenState[self.State, that.State, A0, BB]

  def State(state1: self.State, leftovers1: Chunk[A0], state2: that.State, leftovers2: Chunk[BB]): State =
     AndThenState(state1, leftovers1, state2, leftovers2)

  /**
    * Runs the sink from an initial state and produces a final value
    * of type `B`
    */
  override def extract(state: State): ZIO[R1, E1, C] = {
    that.extract(state.state2)
  }

  /**
    * The initial state of the sink.
    */
  override def initial: ZIO[R1, E1, Step[State, Nothing]] = {
    for {
      i1 <- self.initial
      i2 <- that.initial
    } yield Step.more(State(Step.state(i1), Chunk.empty, Step.state(i2), Chunk.empty))
  }

  /**
    * Steps through one iteration of the sink
    */
  override def step(state: State,
                    a: A): ZIO[R1, E1, Step[State, A0]] = for {
     s0  <- self.initial
     s1  <- self.step(Step.state(s0), a)
     s2  <- if(Step.leftover(s1).notEmpty) UIO(Step.more(state))
            else propagate(self.extract(Step.state(s1)), state)
  } yield s2

  /**
    * we take a `b` (inside effect) and propagate it to `that` sink
    */
  private def propagate(bM: ZIO[R1, E1, B], state: State): ZIO[R1, E1, Step[State, A0]] = for {
    b <- bM
    s0 <- prefill2(state)
    s2 <- that.step(s0.state2, b)
    s3 = if(Step.leftover(s2).notEmpty) Step.more(s0.copy(leftovers2 = Step.leftover(s2)))
         else Step.done(s0.copy(state2 = Step.state(s2)), Chunk.empty)
  } yield s3

  private def prefill2(state: State): ZIO[R1, E1, State] = {
    state.leftovers2.foldM(Option(state.state2)){(acc, b) =>
      acc match {
        case Some(s) =>
          that.step(s, b).map(step =>
            if(Step.cont(step)) Some(Step.state(step))
            else None
          )
        case None => UIO(None)
      }
    }.map { state2 =>
      val updatedState2 = state2.getOrElse(state.state2)
      state.copy(state2 = updatedState2, leftovers2 = Chunk.empty)
    }
  }
}

object AndThenSink {
  case class AndThenState[S1, S2, A, B](state1: S1, leftovers1: Chunk[A], state2: S2, leftovers2: Chunk[B])
}


