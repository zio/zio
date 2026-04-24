# Before, After, and Around Test Aspects

> 1. We can run an effect _before_, _after_, or _around_ every test:
- `TestAspect.before`
- `TestAspect.after`
- `TestAspect.afterFailure`
- `TestAspect.afterSuccess`
- `TestAspect.around`

1. We can run an effect _before_, _after_, or _around_ every test:
- `TestAspect.before`
- `TestAspect.after`
- `TestAspect.afterFailure`
- `TestAspect.afterSuccess`
- `TestAspect.around`

```scala

test("before and after") {
  for {
    tmp <- System.env("TEMP_DIR")
  } yield assertTrue(tmp.contains("/tmp/test"))
} @@ TestAspect.before(
  TestSystem.putEnv("TEMP_DIR", s"/tmp/test")
) @@ TestAspect.after(
  System.env("TEMP_DIR").flatMap(deleteDir)
)
```

2. The `TestAspect.aroundTest` takes a scoped resource and evaluates every test within the context of the scoped function.

3. There are also `TestAspect.beforeAll`, `TestAspect.afterAll`, `afterAllFailure`, `afterAllSuccess`, and `TestAspect.aroundAll` variants.

4. Using `TestAspect.aroundWith` and `TestAspect.aroundAllWith` we can evaluate every test or all test between two given effects, `before` and `after`, where the result of the `before` effect can be used in the `after` effect.
