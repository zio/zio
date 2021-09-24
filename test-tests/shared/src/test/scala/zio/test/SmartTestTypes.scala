package zio.test

import java.time.LocalDateTime

object SmartTestTypes {
  sealed trait Color

  case class Red(name: String) extends Color

  case class Blue(brightness: Int) extends Color

  case class Post(title: String, publishDate: Option[LocalDateTime] = None)

  case class User(name: String, posts: List[Post])

  case class Company(name: String, users: List[User])

}
