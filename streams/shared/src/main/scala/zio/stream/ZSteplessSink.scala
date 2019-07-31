package zio.stream

import zio._

trait ZSteplessSink[-R, +E, +A0, -A, +B] { self =>
  type State

  def cont(state: State): Boolean

  def extract(state: State): ZIO[R, E, B]

  def initial: ZIO[R, E, State]

  def leftover(state: State): Chunk[A0]

  def step(state: State, a: A): ZIO[R, E, State]

  final def flatMap[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    f: B => ZSteplessSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSteplessSink[R1, E1, A00, A1, C] =
    new ZSteplessSink[R1, E1, A00, A1, C] {
      type State = Either[self.State, (ZSteplessSink[R1, E1, A00, A1, C], Any)]

      val initial = self.initial.map(Left(_))

      def step(state: State, a: A1) = state match {
        case Left(s1) =>
          self.step(s1, a).flatMap { s2 =>
            if (cont(Left(s2))) UIO.succeed(Left(s2))
            else {
              val as = self.leftover(s2)

              self.extract(s2).flatMap { b =>
                val that = f(b)

                that.initial.flatMap { s3 =>
                  that.stepChunk(s3, as.map(ev)).map(s4 => Right((that, s4)))
                }
              }
            }
          }

        case Right((that, s1)) =>
          that.step(s1.asInstanceOf[that.State], a).map(s2 => Right((that, s2)))
      }

      def extract(state: State) = state match {
        case Left(s1) =>
          self.extract(s1).flatMap { b =>
            val that = f(b)

            that.initial.flatMap(s2 => that.extract(s2))
          }

        case Right((that, s1)) =>
          that.extract(s1.asInstanceOf[that.State])
      }

      def cont(state: State) = state match {
        case Left(s)          => self.cont(s)
        case Right((that, s)) => that.cont(s.asInstanceOf[that.State])
      }

      def leftover(state: State): Chunk[A00] = state match {
        case Left(s)          => self.leftover(s)
        case Right((that, s)) => that.leftover(s.asInstanceOf[that.State])
      }
    }

  final def map[C](f: B => C): ZSteplessSink[R, E, A0, A, C] =
    new ZSteplessSink[R, E, A0, A, C] {
      type State = self.State
      val initial                  = self.initial
      def step(state: State, a: A) = self.step(state, a)
      def cont(state: State)       = self.cont(state)
      def leftover(state: State)   = self.leftover(state)
      def extract(state: State)    = self.extract(state).map(f)
    }

  final def stepChunk[A1 <: A](state: State, as: Chunk[A1]): ZIO[R, E, State] = {
    val len = as.length

    def loop(s: State, i: Int): ZIO[R, E, State] =
      if (i >= len) UIO.succeed(s)
      else if (self.cont(s)) self.step(s, as(i)).flatMap(loop(_, i + 1))
      else UIO.succeed(s)

    loop(state, 0)
  }

  final def zip[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSteplessSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSteplessSink[R1, E1, A00, A1, (B, C)] =
    flatMap(b => that.map(c => (b, c)))
}

object ZSteplessSink {

  final def collectAll[A]: ZSteplessSink[Any, Nothing, Nothing, A, List[A]] =
    fold[List[A], Nothing, A](Nil)(_ => true)((list, a) => a :: list)(_ => Chunk.empty).map(_.reverse)

  final def fold[S, A0, A](
    z: S
  )(contFn: S => Boolean)(stepFn: (S, A) => S)(leftoverFn: S => Chunk[A0]): ZSteplessSink[Any, Nothing, A0, A, S] =
    new ZSteplessSink[Any, Nothing, A0, A, S] {
      type State = S
      val initial                  = UIO.succeed(z)
      def step(state: State, a: A) = UIO.succeed(stepFn(state, a))
      def cont(state: State)       = contFn(state)
      def leftover(state: State)   = leftoverFn(state)
      def extract(state: State)    = UIO.succeed(state)
    }

  final def identity[A]: ZSteplessSink[Any, Unit, Nothing, A, A] =
    new ZSteplessSink[Any, Unit, Nothing, A, A] {
      type State = Option[A]
      val initial                  = UIO.succeed(None)
      def step(state: State, a: A) = UIO.succeed(Some(a))
      def cont(state: State)       = state.fold(true)(_ => false)
      def leftover(state: State)   = Chunk.empty
      def extract(state: State)    = IO.fromOption(state)
    }
}
