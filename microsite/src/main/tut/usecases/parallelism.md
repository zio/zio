---
layout: docs
section: usecases
title:  "Parallelism"
---

# {{page.title}}


```scala
trait IO[E, A] {
  def zipPar(that: IO[E, B]): IO[E, (A, B)]
}

object IO {
  def collectAllPar(as: Iterable[IO[E, A]]): IO[E, List[A]]
  def foreachPar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]]
}
```
Using `collectAllPar` for a given list of tasks, the whole tasks will be executed in parallel, then you will get back all the executed computations

using `zip` you can run 2 tasks in parallel

and if you want to perform an action for every item in a list of tasks, you can call `foreachPar`

```scala
val profile: IO[Error, List[Information]]  =
 IO.collectAllPar(loadName(user) :: loadPicture(user) :: Nil)

val profileAndFriends: IO[Error, (UserInfo, List[Friend])] = infoUser.zipPar(friendList(user))

val newProfile: IO[Error, List[Profile]] = IO.foreachPar(readUser :: readImage)(createProfile)
}
```
