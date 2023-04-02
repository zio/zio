package dev.zio.quickstart.counter

import zio.http._
import zio.http.model.Method
import zio.{Ref, ZIO}

/** An http app that:
  *   - Accepts `Request` and returns a `Response`
  *   - Does not fail
  *   - Requires the `Ref[Int]` as the environment
  */
object CounterApp {
  def apply(): Http[Ref[Int], Nothing, Request, Response] = {
    Http.collectZIO[Request] {
      case Method.GET -> !! / "up" =>
        ZIO
          .service[Ref[Int]]
          .flatMap(ref =>
            ref
              .updateAndGet(_ + 1)
              .map(_.toString)
              .map(Response.text)
          )
      case Method.GET -> !! / "down" =>
        ZIO
          .service[Ref[Int]]
          .flatMap(ref =>
            ref
              .updateAndGet(_ - 1)
              .map(_.toString)
              .map(Response.text)
          )
      case Method.GET -> !! / "get" =>
        ZIO
          .service[Ref[Int]]
          .flatMap(ref => ref.get.map(_.toString).map(Response.text))
    }
  }
}
