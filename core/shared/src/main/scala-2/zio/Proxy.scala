package zio

import zio.internal.macros.ProxyMacros

object Proxy {
  def generate[A](service: ScopedRef[A]): A = macro ProxyMacros.makeImpl[A]
}
