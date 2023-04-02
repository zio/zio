package dev.zio.quickstart.greet

import zio.http._
import zio.http.model.Method

/**
 * An http app that: 
 *   - Accepts a `Request` and returns a `Response`
 *   - Does not fail
 *   - Does not use the environment
 */
object GreetingApp {
  def apply(): Http[Any, Nothing, Request, Response] =
    Http.collect[Request] {
      // GET /greet?name=:name
      case req@(Method.GET -> !! / "greet") if (req.url.queryParams.nonEmpty) =>
        Response.text(s"Hello ${req.url.queryParams("name").mkString(" and ")}!")

      // GET /greet
      case Method.GET -> !! / "greet" =>
        Response.text(s"Hello World!")

      // GET /greet/:name
      case Method.GET -> !! / "greet" / name =>
        Response.text(s"Hello $name!")
    }
}
