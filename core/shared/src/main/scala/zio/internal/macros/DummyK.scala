package zio.internal.macros

/**
 * DummyK is used to pull `WeakTypeTag` information into a Macro when
 * there is otherwise no value to extract it from.
 * See: [[ZLayerFromAutoMacros.fromAutoImpl]]
 */
private[zio] final case class DummyK[A]()

private[zio] object DummyK {
  private val singleton: DummyK[Any] = DummyK()
  implicit def dummyK[A]: DummyK[A]  = singleton.asInstanceOf[DummyK[A]]
}
