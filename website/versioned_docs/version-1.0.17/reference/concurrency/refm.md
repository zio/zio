---
id: refm
title: "RefM"
---
`RefM[A]` models a **mutable reference** to a value of type `A` in which we can store **immutable** data, and update it atomically **and** effectfully.

> _**Note:**_
>
> Almost all of `RefM` operations are the same as `Ref`. We suggest reading [`Ref`](ref.md) at first if you are not familiar with `Ref`.

Let's explain how we can update a shared state effectfully with `RefM`. The `update` method and all other related methods get an effectful operation and then run they run these effects to change the shared state. This is the main difference between `RefM` and `Ref`. 

In the following example, we should pass in `updateEffect` to it which is the description of an update operation. So `RefM` is going to update the `refM` by running the `updateEffect`:

```scala
import zio._
for {
  refM   <- RefM.make("current")
  updateEffect = IO.effectTotal("update")
  _ <- refM.update(_ => updateEffect)
  value <- refM.get
} yield assert(value == "update")
```

In real-world applications, there are cases where we want to run an effect, e.g. query a database, and then update the shared state. This is where `RefM` can help us to update the shared state in a more actor model fashion. We have a shared mutable state but for every different command or message, and we want execute our effect and update the state. 

We can pass in an effectful program into every single update. All of them will be done parallel, but the result will be sequenced in such a fashion that they only touched the state at different times, and we end up with a consistent state at the end.

In the following example, we are going to send `getAge` request to usersApi for each user and updating the state respectively:


```scala
val meanAge =
  for {
    ref <- RefM.make(0)
    _ <- IO.foreachPar(users) { user =>
      ref.update(sumOfAges =>
        api.getAge(user).map(_ + sumOfAges)
      )
    }
    v <- ref.get
  } yield (v / users.length)
```
