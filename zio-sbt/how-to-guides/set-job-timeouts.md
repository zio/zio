# Set Job Timeouts

> A job with no timeout inherits GitHub's default of six hours, which is a long time to wait for a hung build:

```scala
Job(id = "test", name = "Test", jobTimeout = Some(25), steps = ...)

// or on an existing job
ciTestJobs := ciTestJobs.value.map(_.withTimeout(25))
```

> **Note:**
>
> `Job` also has an older `timeoutMinutes: Int` field. It predates `jobTimeout` and was accepted but never rendered, so it is deprecated. It is still honoured when set to anything other than its historical default of `30`, so builds that set it keep working.
