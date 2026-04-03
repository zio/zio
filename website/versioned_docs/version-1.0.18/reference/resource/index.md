---
id: index
title: "Introduction"
---

When we are writing a long-lived application, resource management is very important. Proper resource management is vital to any large-scale application. We need to make sure that our application is resource-safe, and it doesn't leak any resource.

Leaking socket connections, database connections or file descriptors is not acceptable in a web application. ZIO provides some good construct to make sure about this concern.

To write a resource-safe application, we need to make sure whenever we are opening a resource, we have a mechanism to close that resource whether we use that resource completely or not, for example, an exception occurred during resource usage.

## Try / Finally
Before we dive into the ZIO solution, it's better to review the `try` / `finally` which is the standard approach in the Scala language to manage resources.

Scala has a `try` / `finally` construct which helps us to make sure we don't leak resources because no matter what happens in the try, the `finally` block will be executed. So we can open files in the try block, and then we can close them in the `finally` block, and that gives us the guarantee that we will not leak resources.

Assume we want to read a file and return the number of its lines:


```scala
def lines(file: String): Task[Long] = Task.effect {
  def countLines(br: BufferedReader): Long = br.lines().count()
  val bufferedReader = new BufferedReader(
    new InputStreamReader(new FileInputStream("file.txt")),
    2048
  )
  val count = countLines(bufferedReader)
  bufferedReader.close()
  count
}
```

What happens if after opening the file and before closing the file, an exception occurs? So, the `bufferedReader.close()` line, doesn't have a chance to close the resource. This creates a resource leakage. The Scala language has `try...finally` construct, which helps up to prevent these situations.

Let's rewrite the above example with `try..finally`:

```scala
def lines(file: String): Task[Long] = Task.effect {
  def countLines(br: BufferedReader): Long = br.lines().count()
  val bufferedReader = new BufferedReader(
    new InputStreamReader(new FileInputStream("file.txt")),
    2048
  )
  try countLines(bufferedReader)
  finally bufferedReader.close()
}
```

Now, we are sure that if our program is interrupted during the process of a file, the `finally` block will be executed.

The `try` / `finally` solve simple problems, but it has some drawbacks:

1. It's not composable; We can't compose multiple resources together.

2. When we have multiple resources, we end up with messy and ugly code, hard to reason about, and refactoring.
3. We don't have any control over the order of resource clean-up
4. It only helps us to handle resources sequentially. It can't compose multiple resources, concurrently.
5. It doesn't support asynchronous workflows.
6. It's a manual way of resource management, not automatic. To have a resource-safe application we need to manually check that all resources are managed correctly. This way of resource management is error-prone in case of forgetting to manage resources, correctly.

## ZIO Solution

ZIO's resource management features work across synchronous, asynchronous, concurrent, and other effect types, and provide strong guarantees even in the presence of failure, interruption, or defects in the application.

ZIO has two major mechanisms to manage resources.

### bracket

ZIO generalized the pattern of `try` / `finally` and encoded it in `ZIO.bracket` or `ZIO#bracket` operations. 

Every bracket requires three actions:
1. **Acquiring Resource**— An effect describing the acquisition of resource. For example, opening a file.
2. **Using Resource**— An effect describing the actual process to produce a result. For example, counting the number of lines in a file.
3. **Releasing Resource**— An effect describing the final step of releasing or cleaning up the resource. For example, closing a file.


```scala
def use(resource: Resource): Task[Any] = Task.effect(???)
def release(resource: Resource): UIO[Unit] = Task.effectTotal(???)
def acquire: Task[Resource]                = Task.effect(???)

val result1: Task[Any] = acquire.bracket(release, use)
val result2: Task[Any] = acquire.bracket(release)(use) // More ergonomic API

val result3: Task[Any] = Task.bracket(acquire, release, use)
val result4: Task[Any] = Task.bracket(acquire)(release)(use) // More ergonomic API
```

The bracket guarantees us that the `acquiring` and `releasing` of a resource will not be interrupted. These two guarantees ensure us that the resource will always be released.

Let's try a real example. We are going to write a function which count line number of given file. As we are working with file resource, we should separate our logic into three part. At the first part, we create a `BufferedReader`. At the second, we count the file lines with given `BufferedReader` resource, and at the end we close that resource:

```scala:mdoc:silent
def lines(file: String): Task[Long] = {
  def countLines(reader: BufferedReader): Task[Long]    = Task.effect(reader.lines().count())
  def releaseReader(reader: BufferedReader): UIO[Unit]  = Task.effectTotal(reader.close())
  def acquireReader(file: String): Task[BufferedReader] = Task.effect(new BufferedReader(new FileReader(file), 2048))

  Task.bracket(acquireReader(file), releaseReader, countLines)
}
```

Let's write another function which copy a file from source to destination file. We can do that by nesting two brackets one for the `FileInputStream` and the other for `FileOutputStream`:

```scala
def is(file: String): Task[FileInputStream]  = Task.effect(???)
def os(file: String): Task[FileOutputStream] = Task.effect(???)

def close(resource: Closeable): UIO[Unit] = Task.effectTotal(???)
def copy(from: FileInputStream, to: FileOutputStream): Task[Unit] = ???

def transfer(src: String, dst: String): ZIO[Any, Throwable, Unit] = {
  Task.bracket(is(src))(close) { in =>
    Task.bracket(os(dst))(close) { out =>
      copy(in, out)
    }
  }
}
```

As there isn't any dependency between our two resources (`is` and `os`), it doesn't make sense to use nested brackets, so let's `zip` these two acquisition into one effect, and the use one bracket on them:

```scala
def transfer(src: String, dst: String): ZIO[Any, Throwable, Unit] = {
  is(src)
    .zipPar(os(dst))
    .bracket { case (in, out) =>
      Task
        .effectTotal(in.close())
        .zipPar(Task.effectTotal(out.close()))
    } { case (in, out) =>
      copy(in, out)
    }
}
```

While using bracket is a nice and simple way of managing resources, but there are some cases where a bracket is not the best choice:

1. Bracket is not composable— When we have multiple resources, composing them with a bracket is not straightforward.

2. Messy nested brackets— In the case of multiple resources, nested brackets remind us of callback hell awkwardness. The bracket is designed with nested resource acquisition. In the case of multiple resources, we encounter inefficient nested bracket calls, and it causes refactoring a complicated process.

Using brackets is simple and straightforward, but in the case of multiple resources, it isn't a good player. This is where we need another abstraction to cover these issues.

### ZManaged 

`ZManage` is a composable data type for resource management, which wraps the acquisition and release action of a resource. We can think of `ZManage` as a handle with build-in acquisition and release logic.

To create a managed resource, we need to provide `acquire` and `release` action of that resource to the `make` constructor:

```scala
val managed = ZManaged.make(acquire)(release)
```

We can use managed resources by calling `use` on that. A managed resource is meant to be used only inside of the `use` block. So that resource is not available outside of the `use` block. 

The `ZManaged` is a separate world like `ZIO`; In this world, we have a lot of combinators to combine `ZManaged` and create another `ZManaged`. At the end of the day, when our composed `ZManaged` prepared, we can run any effect on this resource and convert that into a `ZIO` world.

Let's try to rewrite a `transfer` example with `ZManaged`:

```scala
def transfer(from: String, to: String): ZIO[Any, Throwable, Unit] = {
  val resource = for {
    from <- ZManaged.make(is(from))(close)
    to   <- ZManaged.make(os(to))(close)
  } yield (from, to)

  resource.use { case (in, out) =>
    copy(in, out)
  }
}
```

Also, we can get rid of this ceremony and treat the `Managed` like a `ZIO` effect:

```scala
def transfer(from: String, to: String): ZIO[Any, Throwable, Unit] = {
  val resource: ZManaged[Any, Throwable, Unit] = for {
    from <- ZManaged.make(is(from))(close)
    to   <- ZManaged.make(os(to))(close)
    _    <- copy(from, to).toManaged_
  } yield ()
  resource.useNow
}
```

This is where the `ZManaged` provides us a composable and flexible way of allocating resources.  They can be composed with any `ZIO` effect by converting them using the `ZIO#toManaged_` operator.

`ZManaged` has several type aliases, each of which is useful for a specific workflow:

- **[Managed](managed.md)**— `Managed[E, A]` is a type alias for `Managed[Any, E, A]`.
- **[TaskManaged](task-managed.md)**— `TaskManaged[A]` is a type alias for `ZManaged[Any, Throwable, A]`.
- **[RManaged](rmanaged.md)**— `RManaged[R, A]` is a type alias for `ZManaged[R, Throwable, A]`.
- **[UManaged](umanaged.md)**— `UManaged[A]` is a type alias for `ZManaged[Any, Nothing, A]`.
- **[URManaged](urmanaged.md)**— `URManaged[R, A]` is a type alias for `ZManaged[R, Nothing, A]`.
