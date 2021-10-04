package zio

/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import _root_.zio.stm._ 

trait ZPool[+E, +A] {
  /**
    * Retrieves an item from the pool in a `Managed` effect. Note that if acquisition fails, then 
      the returned effect will fail for that same reason.
    */
  def get: ZManaged[Any, E, A]
}
object ZPool {
  /**
    * {{{
    * for {
    *   pool <- ZPool.make(acquireDbConnection, 10 to 20)
    *   _    <- pool.get.use { connection => useConnection(connection) }
    * } yield ()
    * }}}
    */
  def make[E, A](get: ZManaged[Any, E, A], range: Range): UManaged[ZPool[E, A]] = 
    for {
      down <- TRef.make(false).commit.toManaged
      size <- TRef.make(0).commit.toManaged
      free <- TSet.empty[TPromise[E, Acquired[A]]].commit.toManaged
      pool = DefaultPool(get, range, down, size, free)
      _    <- pool.initialize.forkDaemon.toManaged
      _    <- ZManaged.finalizer(pool.shutdown)
    } yield pool

  private case class Acquired[A](resource: A, finalizer: UIO[Any])

  private case class DefaultPool[E, A](creator: ZManaged[Any, E, A], range: Range, isShuttingDown: TRef[Boolean], size: TRef[Int], free: TSet[TPromise[E, Acquired[A]]]) extends ZPool[E, A] {
    def used: USTM[Int] = 
      for {
        size <- size.get 
        free <- free.size 
      } yield size - free 

    def acquireOne: USTM[TPromise[E, Acquired[A]]] = 
      free.takeFirstSTM(promise => promise.poll.flatMap { 
        case Some(Right(_)) => STM.succeedNow(promise)
        case _ => STM.fail(None)
      })

    // def acquireAll: USTM[Chunk[TPromise[E, Acquired[A]]]] = 
    //   for {
    //     used <- used 
    //     _    <- if (used > 0) 
    //   }

    def initialize: UIO[Unit] = {
      def allocate: IO[E, Acquired[A]] = 
        for {
          reservation <- creator.reserve
          resource    <- reservation.acquire
        } yield Acquired(resource, reservation.release(Exit.succeed(())))

      def createNewPoolSlot: STM[Nothing, TPromise[E, Acquired[A]]] = 
        for {
          promise <- TPromise.make[E, Acquired[A]] 
          _       <- free.put(promise)
          _       <- size.update(_ + 1)
        } yield promise

      def allocateToPool: UIO[Any] = 
        for {
          promise <- createNewPoolSlot.commit
          _       <- allocate.foldZIO(
                      error   => promise.fail(error).commit,
                      success => promise.succeed(success).commit
                    )
        } yield ()       
        
      ZIO.replicateZIODiscard(range.start)(allocateToPool)
      }

    def shutdown: UIO[Unit] = ???

    def get: ZManaged[Any, E, A] = {
      def takeFree: USTM[TPromise[E, Acquired[A]]] = 
        free.foldSTM(Option.empty[TPromise[E, Acquired[A]]]) { 
          case (None, promise) => 
            promise.poll.map {
              case Some(Right(value)) => Some(promise) 
              case _ => None
            }
          case (some, _) => STM.succeed(some)
        }.collect { case Some(promise) => promise }.flatMap { promise => 
          free.delete(promise).as(promise)
        }

      def release(promise: TPromise[E, Acquired[A]]): UIO[Unit] = 
        free.put(promise).commit
        
      def extract(promise: TPromise[E, Acquired[A]]): IO[E, A] = 
        promise.await.commit.map(_.resource)

      ZManaged.acquireReleaseWith(takeFree.commit)(release(_)).mapZIO(extract(_))
    }
  }
}