package zio

object RefM {

  /**
   * @see [[zio.ZRefM.dequeueRef]]
   */
  @deprecated("use SubscriptionRef", "2.0.0")
  def dequeueRef[A](a: A): UIO[(RefM[A], Dequeue[A])] =
    ZRefM.dequeueRef(a)

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
}
