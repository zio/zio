---
id: index
title: "Introduction"
---

When we write a program, more often we need to keep track of some sort of state during the execution of the program. If an object has a state, its behavior is influenced by passing the time.

Here are some examples of some states:

- **Counter**— Assume a RESTful API, which has a set of endpoints, and wants to keep track of how many requests have been made to each endpoint.
- **Bank Account Balance**— Each bank account has a balance, and it can be deposited or withdrawn. So its value is changing over time.
- **Temperature**— The temperature of a room is changing over time.
- **List of Users**— In a web application, we can have a list of users, and we can add, remove, or modify users. They are changing over time, and we want to keep track of any changes to users.
- When we are iterating over a list of items, we might need to keep track of the number of items we have seen so far. So during the calculation of the length of the list, we need an intermediate state that records the number of items we have seen so far.

In imperative programming, we usually use a variable to keep track of the state:

```scala mdoc:compile-only
def length[T](list: List[T]): Int = {
  var count = 0
  for (_ <- list) count += 1
  count
}
```

In functional programming we avoid using variables to keep track of state. Instead, we use other techniques. For example, in recursive style, we use function arguments to pass the state to the next function:

```scala mdoc:compile-only
def length[T](list: List[T]): Int = {
  def loop(list: List[T], count: Int): Int = {
    list match {
      case Nil => count
      case _ :: tail => loop(tail, count + 1)
    }
  }

  loop(list, 0)
}
```

In imperative programming, when we have some I/O operation, we can also use a variable to keep track of the state:

```scala mdoc:compile-only
import scala.io.StdIn._

def getNames: List[String] = {
  def getName() = readLine("Please enter a name or 'q' to exit: ")
  var names = List.empty[String]
  var input = getName()
  while (input != "exit") {
    names = names appended input
    input = getName()
  }
  names
} 
```

Using the previous pattern, we can eliminate the need to use a variable:

```scala mdoc:compile-only
import scala.io.StdIn._

def getNames: Seq[String] = {
  @tailrec
  def loop(names: List[String]): List[String] = {
    val name = readLine("Please enter a name or 'q' to exit: ")
    if (name == "exit") names
    else loop(name :: names)
  }
  loop(List.empty[String])
}
```

But, there is also a problem with the previous solution. The `getName` is not referentially transparent. In order to make it free of side effects, we can use `ZIO` to describe any effectual operation:

```scala mdoc:compile-only
def inputNames: ZIO[Any, String, List[String]] = {
  def loop(names: List[String]): ZIO[Any, String, List[String]] = {
    Console.readLine("Please enter a name or `q` to exit: ").orDie.flatMap {
      case "q" =>
        ZIO.succeed(names)
      case name =>
        loop(names appended name)
    }
  }

  loop(List.empty[String])
}
```

It is sometimes awkward to pass the state using function parameters. In such cases, we can use the `Ref` data type, which is a purely functional description of a mutable reference. All the operations on the `Ref` data type are effectful. So when we read or write to a `Ref`, we are performing an effectful operation.

So let's try to solve the problem, using the `Ref` data type:

```scala mdoc:compile-only
import zio._

def getNames: ZIO[Any, String, List[String]] =
  Ref.make(List.empty[String])
    .flatMap { ref =>
      Console
        .readLine("Please enter a name or 'q' to exit: ")
        .orDie
        .repeatWhileZIO {
          case "q" => ZIO.succeed(false)
          case name => ref.update(_ appended name).as(true)
        } *> ref.get
    }
```
