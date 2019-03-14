package scalaz.zio.stream

import scalaz.zio.{ Exit, IO, TestRuntime }

trait StreamTestUtils { self: TestRuntime =>
  def slurp[E, A](s: Stream[E, A]): Exit[E, List[A]] =
    slurp0(s)(_ => true)

  def slurp0[E, A](s: Stream[E, A])(cont: List[A] => Boolean): Exit[E, List[A]] = s match {
    case s: StreamPure[Any, A] =>
      Exit.succeed(s.foldPureLazy(List[A]())(cont)((acc, el) => el :: acc).reverse)
    case s =>
      unsafeRunSync {
        s.fold[Any, E, A, List[A]].flatMap(f0 => f0(List[A](), cont, (acc, el) => IO.succeed(el :: acc)).map(_.reverse))
      }
  }
}
