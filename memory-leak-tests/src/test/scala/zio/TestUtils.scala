package zio

object TestUtils {

  def unsafeRunNewRuntime[E, A](zio: ZIO[Any, E, A]): A = {
    val rt = Runtime(ZEnvironment.empty, FiberRefs.empty, RuntimeFlags.default)
    Unsafe.unsafe(implicit unsafe => rt.unsafe.run(zio).getOrThrowFiberFailure())
  }
}
