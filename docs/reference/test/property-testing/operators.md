---
id: operators
title: "Operators"
---

1. `Gen#zipWith` — Composes this generator with the specified generator to create a cartesian product of elements with the specified function:

  ```scala mdoc:compile-only
  Gen.elements("a", "b", "c").zipWith(Gen.elements("1", "2", "3"))(_ + _)
    .runCollectN(5)
  // Sample Output: List(b1, a2, c1, b1, b1)
  ```

2. `Gen#zip` — Composes this generator with the specified generator to create a cartesian product of elements.

  ```scala mdoc:compile-only
  Gen.elements("a", "b", "c").zip(Gen.elements("1", "2", "3"))
    .runCollectN(5)
  (Gen.elements("a", "b", "c") <*> Gen.elements("1", "2", "3"))
    .runCollectN(5)
    
  // Sample Output: List((a,3), (a,3), (c,3), (b,3), (c,2))
  ```

3. `Gen#collect` — Maps the values produced by this generator with the specified partial function, discarding any values the partial function is not defined at:

  ```scala mdoc:compile-only
  Gen.int(-10, +10)
    .collect { case n if n % 2 == 0 => n }
    .runCollectN(5)
    .debug
  // Smaple Output: List(-6, -8, -2, 4, -6)
  ```

4. `Gen#filter` — Filters the values produced by this generator, discarding any values that do not meet the specified predicate:

  ```scala mdoc:compile-only
  Gen.int(-10, +10).filter(_ % 2 == 0).runCollectN(5) 
  // Sample Output: List(-6, 10, 0, -8, 4)
  ```

Using `filter` can reduce test performance, especially if many values must be discarded. It is recommended to use combinators such as `map` and `flatMap` to create generators of the desired values instead:

  ```scala mdoc:compile-only
  Gen.int(-10, +10).map(_ * 2).runCollectN(5)
  // Sample Output: List(2, 6, -6, 20, -14)
  ```
