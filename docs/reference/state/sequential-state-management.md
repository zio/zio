---
id: sequential 
title: "Sequential State Management"
---

This is a very common pattern to use variables to keep track of the state. For example, to calculate the length of a list, we can store intermediate results inside the `count` variable:

```scala mdoc:compile-only
def length[T](list: List[T]): Int = {
  var count = 0
  for (_ <- list) count += 1
  count
}
```

But in functional programming, we avoid using variables to keep track of the state. Instead, we use other techniques.

One common technique is to pass the new state as an argument to the next function. After we created a new version of the state, we pass it to the next function. We do this until we have reached the final state.

Assume we have the following code:

```scala mdoc:compile-only
var state = 5
state = state + 1
state = state * 2
state = state * state

println(state)
// Output: 144
```

We can rewrite it as a series of transformations, in which new states are passed to the next function:

```scala mdoc
def foo(state: Int): Int = bar(state + 1)
def bar(state: Int): Int = baz(state * 2)
def baz(state: Int): Int = state * state

println(foo(5)) 
// Output: 144
```

Now, what if we wanted to apply the transformation multiple times to a given state? We can combine this technique with recursive functions and call the function multiple times.

For example, we use function arguments to pass the state to the next function, using recursive calls. Assume we have a `length` function that returns the length of a list as below:

```scala mdoc:compile-only
def length[T](list: List[T]): Int = {
  var count = 0
  for (_ <- list) count += 1
  count
}
```

If we want to convert the above function to a series of state transformations, first, we need to diagnose what is the state?

One obvious answer is the `count` variable. The `count` variable is the state of the above function that keeps track of the length of the list. Another non-obvious state is the remainder of the list that is not yet processed during the list traversal.

So we can model the state composed of the `count` and the `remainder` of the list:

```scala mdoc:compile-only
case class State[T](count: Int, remainder: List[T])
```

Now we are ready to write the state transformation function called `loop` as below:

```scala mdoc:compile-only
case class State[T](count: Int, remainder: List[T])

def loop[T](state: State[T]): State[T] = {
  state.remainder match {
    case Nil => state
    case _ :: tail => loop(State(state.count + 1, tail))
  }
}
```

Here are series of `loop` calls, when we call it with the `State(0, List("a", "b", "c", "d"))` state:

```scala mdoc:compile-only
loop(State(0, List("a", "b", "c", "d")))
loop(State(1, List("b", "c", "d")))
loop(State(2, List("c", "d")))
loop(State(3, List("d")))
loop(State(4, List()))
// Output:
// State(4, List())
```

Let's do some small modification to the `loop` function and use it inside the `length` function:

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

The same pattern can be used when we have side effects. Assume we have a function that tries to read names from the input, until the user enters the "q" command indicating the end of the input. We can write the function like this:

```scala mdoc:compile-only
import scala.io.StdIn._

def getNames: List[String] = {
  def getName() = readLine("Please enter a name or 'q' to exit: ")
  var names = List.empty[String]
  var input = getName()
  while (input != "q") {
    names = names appended input
    input = getName()
  }
  names
} 
```

Using the previous pattern, we can eliminate the need to use variables:

```scala mdoc:compile-only
import scala.io.StdIn._

def getNames: Seq[String] = {
  def loop(names: List[String]): List[String] = {
    val name = readLine("Please enter a name or 'q' to exit: ")
    if (name == "q") names else loop(names appended name)
  }
  loop(List.empty[String])
}
```

But, there is also a problem with the previous solution. The `getName` is not referentially transparent. In order to make it free of side effects, we can use `ZIO` to describe any effectual operation:

```scala mdoc:compile-only
import zio._

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

On this page, we have learned how to have stateful computations in our programs using recursion. However, this approach is not suitable for concurrent programs, where multiple fibers want to change the state of the program concurrently. Let's move on to the next page, where we will discuss stateful computation over concurrent programs.
