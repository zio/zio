package zio

/** 
 * The `ServiceProxy` object provides a utility to generate a proxy instance for a given service.
 *
 * The `generate` function creates a proxy instance of the service that forwards every ZIO method call to the 
 * underlying service, wrapped in a [[zio.ScopedRef]]. The generated proxy enables the service to change
 * its behavior at runtime.
 *
 * @note
 *   In order to successfully generate a service proxy, the type `A` must meet the following requirements:
 *    - `A` should be either a trait or a class with a primary constructor without any term parameters.
 *    - `A` should contain only ZIO methods or vals.
 *    - `A` should not have any abstract type members.
 *
 * @example
 * {{{
 *   trait MyService { def foo: UIO[String] }
 *
 *   val service1: MyService = new MyService { def foo = ZIO.succeed("zio1") }
 *   val service2: MyService = new MyService { def foo = ZIO.succeed("zio2") }
 *   for {
 *     ref  <- ScopedRef.make(service1)
 *     proxy = ServiceProxy.generate(ref)
 *     res1 <- proxy.foo
 *     _    <- ref.set(ZIO.succeed(service2))
 *     res2 <- proxy.foo
 *   } yield assertTrue(res1 == "zio1" && res2 == "zio2")
 * }}}
 */
object ServiceProxy extends ServiceProxyVersionSpecific
