package zio.query

/**
 * A `Request[E, A]` is a request from a data source for a value of type `A`
 * that may fail with an `E`.
 *
 * {{{
 * sealed trait UserRequest[+A] extends Request[Nothing, A]
 *
 * case object GetAllIds                 extends UserRequest[List[Int]]
 * final case class GetNameById(id: Int) extends UserRequest[String]
 *
 * }}}
 */
trait Request[+E, +A]
