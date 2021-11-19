package zio.test.mock

/**
 * Example modules used for testing _ZIO Mock_ library.
 */
package object module {

  type PureModule   = PureModule.Service
  type ImpureModule = ImpureModule.Service
  type StreamModule = StreamModule.Service

  type T22[A] = (A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A)
}
