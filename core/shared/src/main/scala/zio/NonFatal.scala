package zio

/** Determines if a throwable is non-fatal. */
object NonFatal {
  def apply(t: Throwable): Boolean = !t.isInstanceOf[VirtualMachineError]
}
