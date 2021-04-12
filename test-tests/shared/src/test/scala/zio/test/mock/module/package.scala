package zio.test.mock

import zio.Has

/**
 * Example modules used for testing _ZIO Mock_ library.
 */
package object module {

  type PureModule   = Has[PureModule.Service]
  type ImpureModule = Has[ImpureModule.Service]
  type StreamModule = Has[StreamModule.Service]

  type T22[A] = (A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A)
}
