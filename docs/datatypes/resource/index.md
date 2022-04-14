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

```scala mdoc:invisible
import java.io._
import zio.{Scope, Task, UIO, ZIO}
```

```scala mdoc:silent:nest
def lines(file: String): Task[Long] = Task.attempt {
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

```scala mdoc:silent:nest
def lines(file: String): Task[Long] = Task.attempt {
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

### Acquire Release

ZIO generalized the pattern of `try` / `finally` and encoded it in `ZIO.acquireRelease` or `ZIO#acquireRelease` operations. 

Every acquire release requires three actions:
1. **Acquiring Resource**— An effect describing the acquisition of resource. For example, opening a file.
2. **Using Resource**— An effect describing the actual process to produce a result. For example, counting the number of lines in a file.
3. **Releasing Resource**— An effect describing the final step of releasing or cleaning up the resource. For example, closing a file.

```scala mdoc:invisible
trait Resource
```

```scala mdoc:silent
def use(resource: Resource): Task[Any] = Task.attempt(???)
def release(resource: Resource): UIO[Unit] = Task.succeed(???)
def acquire: Task[Resource]                = Task.attempt(???)

val result: Task[Any] = Task.acquireReleaseWith(acquire)(release)(use)
```

The acquire release guarantees us that the `acquiring` and `releasing` of a resource will not be interrupted. These two guarantees ensure us that the resource will always be released.

Let's try a real example. We are going to write a function which count line number of given file. As we are working with file resource, we should separate our logic into three part. At the first part, we create a `BufferedReader`. At the second, we count the file lines with given `BufferedReader` resource, and at the end we close that resource:

```scala:mdoc:silent
def lines(file: String): Task[Long] = {
  def countLines(reader: BufferedReader): Task[Long]    = Task.attempt(reader.lines().count())
  def releaseReader(reader: BufferedReader): UIO[Unit]  = Task.succeed(reader.close())
  def acquireReader(file: String): Task[BufferedReader] = Task.attempt(new BufferedReader(new FileReader(file), 2048))

  ZIO.acquireReleaseWith(acquireReader(file))(releaseReader)(countLines)
}
```

Let's write another function which copy a file from source to destination file. We can do that by nesting two acquire releases one for the `FileInputStream` and the other for `FileOutputStream`:

```scala mdoc:silent
def is(file: String): Task[FileInputStream]  = Task.attempt(???)
def os(file: String): Task[FileOutputStream] = Task.attempt(???)

def close(resource: Closeable): UIO[Unit] = Task.succeed(???)
def copy(from: FileInputStream, to: FileOutputStream): Task[Unit] = ???

def transfer(src: String, dst: String): ZIO[Any, Throwable, Unit] = {
  Task.acquireReleaseWith(is(src))(close) { in =>
    Task.acquireReleaseWith(os(dst))(close) { out =>
      copy(in, out)
    }
  }
}
```

As there isn't any dependency between our two resources (`is` and `os`), it doesn't make sense to use nested acquire releases, so let's `zip` these two acquisition into one effect, and then use one acquire release on them:

```scala mdoc:silent:nest
def transfer(src: String, dst: String): ZIO[Any, Throwable, Unit] =
  ZIO.acquireReleaseWith {
    is(src).zipPar(os(dst))
  } { case (in, out) =>
    ZIO.succeed(in.close()).zipPar(ZIO.succeed(out.close()))
  } { case (in, out) =>
    copy(in, out)
  }
```

While using acquire release is a nice and simple way of managing resources, but there are some cases where an acquire release is not the best choice:

1. Acquire release is not composable— When we have multiple resources, composing them with an acquire release is not straightforward.

2. Messy nested acquire releases — In the case of multiple resources, nested acquire releases remind us of callback hell awkwardness. The acquire release is designed with nested resource acquisition. In the case of multiple resources, we encounter inefficient nested acquire release calls, and it causes refactoring a complicated process.

Using acquire releases is simple and straightforward, but in the case of multiple resources, it isn't a good player. This is where we need another abstraction to cover these issues.

### Scope 

`Scope` is a composable data type for resource management, which wraps the acquisition and release action of a resource. We can think of `Scope` as a handle with build-in acquisition and release logic.

To create a scoped resource, we need to provide `acquire` and `release` action of that resource to the `acquireRelease` constructor:

```scala mdoc:silent
val scoped = ZIO.acquireRelease(acquire)(release)
```

We can use scoped resources by calling `scoped` on that. A scoped resource is meant to be used only inside of the `scoped` block. So that resource is not available outside of the `scoped` block. 

Let's try to rewrite a `transfer` example with `Scope`:

```scala mdoc:silent:nest
def transfer(from: String, to: String): ZIO[Any, Throwable, Unit] = {
  val resource = for {
    from <- ZIO.acquireRelease(is(from))(close)
    to   <- ZIO.acquireRelease(os(to))(close)
  } yield (from, to)

  ZIO.scoped {
    resource.flatMap { case (in, out) =>
      copy(in, out)
    }
  }
}
```
