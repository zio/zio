package scalaz.ioeffect

import scalaz.Monad

/***
 * Monads in which `IO` computations may be embedded. Any monad built by applying a sequence of
 * monad transformers to the `IO` monad will be an instance of this class. Instances should satisfy the following laws,
 * which state that `liftIO` is a transformer of monads:
 *
 * liftIO . return = return
 * liftIO (m >>= f) = liftIO m >>= (liftIO . f)
 *
 * @tparam M - the monad in which to lift
 */
trait MonadIO[M[_]] extends Monad[M] {

  /**
   * Lift a computation from the `IO` monad into `M`
   */
  def liftIO[E, A](io: IO[E, A]): M[A]

}

object MonadIO {
  def apply[M[_]](implicit M: MonadIO[M]): MonadIO[M] = M

}
