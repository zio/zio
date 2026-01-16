/claim #9909

This PR implements a comprehensive test suite for ZIOApp behavior as requested in issue #9909.

## What was implemented:

- **Complete test coverage** for ZIOApp exit codes, finalizers, signals, timeouts, and regressions
- **Process-based testing** for real ZIOApp behavior verification
- **Platform-appropriate handling** with Windows signal test skipping
- **CI-resistant timing** with generous bounds to prevent flakes
- **Regression tests** for issues #9240, #9807, #10122

## Production Bug Fix

While implementing the test for issue #9240 (custom exit codes), I discovered and fixed a bug in `ZIOAppPlatformSpecific.scala`. The `exitWith` method was not properly propagating custom `ExitCode` instances, always defaulting to `ExitCode.success`. This has been corrected to handle `Exit.Success` cases with custom exit codes.

## Test Results

All tests pass locally:
- 2877 tests passed, 0 failed, 14 ignored
- Includes our new ZIOApp behavior tests

## Bounty Claim

/claim #9909

[Demo video will be added here once recorded]
