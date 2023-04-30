package zio

import zio.internal.macros.ServiceProxyMacros

trait ServiceProxyVersionSpecific {
  def generate[A](service: ScopedRef[A]): A = macro ServiceProxyMacros.makeImpl[A]
}
