package zio

object RefM {

  /**
   * @see [[zio.ZRefM.make]]
   */
  def make[A](a: A): UIO[RefM[A]] =
    ZRefM.make(a)

  /**
   * @see [[zio.ZRefM.makeManaged]]
   */
  def makeManaged[A](a: A): UManaged[RefM[A]] =
    ZRefM.makeManaged(a)

  /**
   * @see [[zio.ZRefM.signalRef]]
   */
  def signalRef[A](a: A): UIO[(RefM[A], Queue[A])] =
    ZRefM.signalRef(a)
}
