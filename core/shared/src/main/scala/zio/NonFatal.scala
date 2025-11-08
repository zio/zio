package zio

object NonFatal {
  def apply(t: Throwable): Boolean = !t.isInstanceOf[VirtualMachineError]
}
