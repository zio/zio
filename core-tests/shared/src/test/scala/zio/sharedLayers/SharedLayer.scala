package zio.sharedLayers

import zio.{Has, ZIO, ZLayer}

import java.util.concurrent.atomic.AtomicInteger

object SharedLayer {
  private val counter = new AtomicInteger(0)

  val test: ZLayer[Any, Nothing, Has[Unit]] = {
    ZLayer.fromZIO(ZIO.debug("Constructing a super expensive layer") *> ZIO.succeed(updateCounter()))
  }

  def updateCounter() = {
    val res = counter.getAndAdd(1)
    if (res > 0) throw new RuntimeException("Should only be called one time!")
  }

}
