# Flaky and Non-flaky Tests

> Whenever we deal with concurrency issues or race conditions, we should ensure that our tests pass consistently. The `nonFlaky` is a test aspect to do that.

Whenever we deal with concurrency issues or race conditions, we should ensure that our tests pass consistently. The `nonFlaky` is a test aspect to do that.

It will run a test several times, by default 100 times, and if all those times pass, it will pass, otherwise, it will fail:

```scala

test("random value is always greater than zero") {
  for {
    random <- Random.nextIntBounded(100)
  } yield assertTrue(random > 0)
} @@ nonFlaky
```

Additionally, there is a `TestAspect.flaky` test aspect which retries a test until it succeeds.
