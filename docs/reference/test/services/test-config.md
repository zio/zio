---
id: config
title: "TestConfig"
---

The `TestConfig` service provides access to default configuration settings used by ZIO Test:

1. Repeats — The number of times to repeat tests to ensure they are stable.
2. Retries — The number of times to retry flaky tests.
3. Samples — The number of sufficient samples to check for a random variable.
4. Shrinks — The maximum number of [shrinkings](../gen.md#shrinking) to minimize large failures.

Currently, the live version of `TestEnvironment` contains the live version of this service with the following configs:

```scala
TestConfig.live(
  repeats0 = 100,
  retries0 = 100,
  samples0 = 200,
  shrinks0 = 1000
)
```

So by default, the ZIO Test runner will run tests with this config. Regular users do not need access to this service unless they need to change or access configurations from the ZIO Test environment.
