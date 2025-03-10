package zio.blocks.schema.binding

sealed trait RegisterType[A]
object RegisterType {
  case object Unit                 extends RegisterType[Unit]
  case object Char                 extends RegisterType[Char]
  case object Byte                 extends RegisterType[Byte]
  case object Short                extends RegisterType[Short]
  case object Int                  extends RegisterType[Int]
  case object Long                 extends RegisterType[Long]
  case object Float                extends RegisterType[Float]
  case object Double               extends RegisterType[Double]
  case object Boolean              extends RegisterType[Boolean]
  sealed trait Object[A <: AnyRef] extends RegisterType[A]

  def Object[A <: AnyRef](): RegisterType[A] = _object.asInstanceOf[RegisterType[A]]

  private val _object: RegisterType[AnyRef] =
    new RegisterType[AnyRef] {
      override def toString: String = "Object"

      override def equals(obj: Any): Boolean = obj match {
        case _: Object[_] => true
        case _            => false
      }

      override def hashCode: Int = 31
    }
}
