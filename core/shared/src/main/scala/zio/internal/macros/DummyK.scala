package zio.internal.macros

case class DummyK[A]()

object DummyK {
  private val singleton: DummyK[Any] = DummyK()
  implicit def dummyK[A]: DummyK[A]  = singleton.asInstanceOf[DummyK[A]]
}
