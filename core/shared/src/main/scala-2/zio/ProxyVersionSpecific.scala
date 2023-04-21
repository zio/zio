package zio

import zio.internal.macros.ProxyMacros

trait ProxyVersionSpecific {
  def generate[A](service: ScopedRef[A]): A = macro ProxyMacros.makeImpl[A]
}
