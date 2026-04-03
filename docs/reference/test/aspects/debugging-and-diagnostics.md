---
id: debugging-and-diagnostics
title: "Debugging and Diagnostics"
---


## Debugging
The `TestConsole` service has two modes debug and silent state. ZIO Test has two corresponding test aspects to switch the debug state on and off:

1. `TestAspect.debug` — When the `TestConsole` is in the debug state, the console output is rendered to the standard output in addition to being written to the output buffer. We can manually enable this mode by using `TestAspect.debug` test aspect.

2. `TestAspect.silent` — This test aspect turns off the debug mode and turns on the silent mode. So the console output is only written to the output buffer and not rendered to the standard output.

## Diagnostics

The `diagnose` is an aspect that runs each test on a separate fiber and prints a fiber dump if the test fails or has not terminated within the specified duration.
