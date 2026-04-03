---
id: shrinking
title: "Shrinking"
---

In Property-Based Testing, we specify certain properties of a program, then we ask the testing framework to generate random test data to discover counterexamples. The existence of counterexamples shows that our function, which is under the test, is not correct. Unfortunately, in almost all cases, the first counterexample is not the minimal one, and they are fairly large or complex. So it is not a pretty good sample to describe why our test is failing.

Shrinking is a mechanism that tries to find the smallest counterexample, which is the root cause of the test failure. So it helps a developer to find out why the test is failing.

Finding the smallest failing case is somehow cumbersome and requires many attempts. As a developer, we do not need to do shrinking ourselves. All generators in ZIO Test have built-in shrinkers, so when we test properties, in case of test failures, the ZIO Test attempts to reduce the counterexamples forward their own zero points.

Let's write a `reverse` function with an incorrect implementation:

```scala mdoc:silent
def reverse[T](list: List[T]): List[T] =
  if (list.length > 6) list.reverse.dropRight(1) else list.reverse
```

We know that if we reverse a list twice, it should give us the original list, so let's check this property:

```scala mdoc:compile-only
import zio.test._

suite("ReverseSpec"){
  // ∀ xs. reverse(reverse(xs)) == xs
  test("reversing a list twice must give the original list")(
    check(Gen.listOf(Gen.int)) { list =>
      assertTrue(reverse(reverse(list)) == list)
    }
  )
}
```

The following messages, is a sample output of the test renderer, after running the test:

```
- ReverseSpec
  - reversing a list twice must give the original list
    Test failed after 7 iterations with input: List(0, 0, 0, 0, 0, 0, 0)
    Original input before shrinking was: List(724856966, 1976458409, -940069360, -191508820, -291932258, 1296893186, 2010410723, 1134770522, 1260002835)
    ✗ List(0, 0, 0, 0, 0, 0) was not equal to List(0, 0, 0, 0, 0, 0, 0)
    reverse(reverse(list)) == list
    reverse(reverse(list)) = List(0, 0, 0, 0, 0, 0)
```

The initial failing input discovered by ZIO Test is `List(724856966, 1976458409, -940069360, -191508820, -291932258, 1296893186, 2010410723, 1134770522, 1260002835)`. The ZIO Test then tries to find the simplest counterexample which is `List(0, 0, 0, 0, 0, 0, 0)`. So the property still fails with the final shrunk value. The original input is a list of 9 somewhat useless numbers, while after shrinking, we have a list of 7 zero numbers, so we can find the bug faster.
