---
id: sized
title: "Changing the Size of Sized Generators"
---

To change the default _size_ used by [sized generators](../gen.md#sized-generators) we can use `sized` test aspect:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("generating small list of characters") {
  check(Gen.small(Gen.listOfN(_)(Gen.alphaNumericChar))) { n =>
    ZIO.attempt(n).debug *> Sized.size.map(s => assertTrue(s == 50))
  }
} @@ TestAspect.sized(50) @@ TestAspect.samples(5)
```

Sample output:

```
List(p, M)
List()
List(0, m, 5)
List(Y)
List(O, b, B, V)
+ generating small list of characters
Ran 1 test in 676 ms: 1 succeeded, 0 ignored, 0 failed
```
