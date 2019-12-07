package zio.test

import scala.collection.immutable.Iterable
import scala.concurrent.{ ExecutionContext, Future }

/**
 * `Async` is a thin functional wrapper around `scala.concurrent.Future` for
 * use in the ZIO Test internal test suite. All methods except `run` merely
 * describe actions and no effects will be run until `run` is called as long as
 * the underlying `Future` is not already "in flight" when the `Async` is
 * constructed. Note that this implementation is not stack safe for deeply
 * recursive calls but since we are only using this to sequence the tests in
 * the ZIO Test test suite that is okay. For any application you would be
 * dramatically better off using `ZIO` and higher level constructs such as
 * `ZStream` built on top of it, but we can't do that here since we are using
 * ZIO Test to test `ZIO` itself.
 */
trait Async[+A] {

  def run(ec: ExecutionContext): Future[A]

  /**
   * Handles some exceptions in this asynchronous computation by converting
   * them to values.
   */
  final def handle[B >: A](p: PartialFunction[Throwable, B]): Async[B] =
    ec => run(ec).recover(p)(ec)

  /**
   * Applies the function `f` to the result of this asynchronous computation.
   */
  final def map[B](f: A => B): Async[B] =
    ec => run(ec).map(f)(ec)

  /**
   * Uses the result of this asynchronous computation to determine a second
   * asynchronous computation.
   */
  final def flatMap[B](f: A => Async[B]): Async[B] =
    ec => run(ec).flatMap(a => f(a).run(ec))(ec)
}

object Async {

  /**
   * Wraps a asynchronous effect in an asynchronous computation.
   */
  final def apply[A](a: => A): Async[A] =
    ec => Future(a)(ec)

  /** Raises an exception in an asynchronous computation. */
  final def fail(e: Throwable): Async[Nothing] =
    _ => Future.failed(e)

  /** Wraps a `Future` in an asynchronous computation. */
  final def fromFuture[A](future: => Future[A]): Async[A] =
    _ => future

  /**
   * Collects the results of a collection of asynchronous computation into a
   * single asynchronous computation.
   */
  final def sequence[A](fas: Iterable[Async[A]]): Async[List[A]] =
    fas.foldRight(succeed(List.empty[A])) { (fa, fas) =>
      for {
        a  <- fa
        as <- fas
      } yield a :: as
    }

  /**
   * Promotes a constant value to an asynchronous computation.
   * */
  final def succeed[A](a: A): Async[A] =
    _ => Future.successful(a)
}
