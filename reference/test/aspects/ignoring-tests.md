# Ignoring Tests

> To ignore running a test, we can use the `ignore` test aspect:

To ignore running a test, we can use the `ignore` test aspect:

```scala

test("an ignored test") {
  assertTrue(false)
} @@ TestAspect.ignore
```

To fail all ignored tests, we can use the `success` test aspect:

```scala

suite("sample tests")(
  test("an ignored test") {
    assertTrue(false)
  } @@ TestAspect.ignore,
  test("another ignored test") {
    assertTrue(true)
  } @@ TestAspect.ignore
) @@ TestAspect.success
```
