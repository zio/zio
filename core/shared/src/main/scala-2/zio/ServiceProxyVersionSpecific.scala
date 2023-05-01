package zio

import zio.internal.macros.ServiceProxyMacros

trait ServiceProxyVersionSpecific {

  /** 
   * Generates a proxy instance of the specified service.
   *
   * @tparam A The type of the service.
   * @param service The `ScopedRef` containing the service for which a proxy is to be generated.
   * @return A proxy instance of the service that forwards ZIO method calls to the underlying service
   *         and allows the service to change its behavior at runtime.
   */
  def generate[A](service: ScopedRef[A]): A = macro ServiceProxyMacros.makeImpl[A]
}
