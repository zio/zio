# Errors and Exit Codes

> A program can fail in two different ways: it can fail to start, or it can start and report failure through its exit code. ZIO Process treats the two differently, and this page explains why, and how the error type reflects it.

## Failing to Start

If the program cannot be started, the effect fails with a `CommandError`. The cases that describe this are `CommandError.ProgramNotFound`, `CommandError.PermissionDenied` and `CommandError.WorkingDirectoryMissing`, which all extend `CommandError.NotStarted`. They are values in the error channel, so the code can match on them and, for example, fall back to another program.

## Starting and Failing

A program that starts and exits with a non-zero code has still run, and for many programs a non-zero code is an ordinary answer. `grep` exits with `1` when it finds no match, for example. For that reason `Command#exitCode` and `Process#exitCode` return the code as a success, whatever its value. The code decides what the value means.

When any non-zero code is a failure, `Command#successfulExitCode` and `Process#successfulExitCode` turn it into a `CommandError.NonZeroErrorCode` that carries the code.

The output shortcuts, such as `Command#string` and `Command#lines`, never look at the exit code. A program that prints partial output and then fails still produces a successful `String`. To check both the output and the code, run the command with `Command#run`, read the output, and then call `Process#successfulExitCode`, as shown in [Handle Command Errors](../how-to-handle-command-errors.md).

## Errors Are Exceptions

`CommandError` is a sealed class that extends `Exception`, so a value can be logged, wrapped or rethrown like any exception. Most cases carry the underlying exception as their cause. Failures that fit no other case are reported as `CommandError.Error`, and a failed read or write of a stream is reported as `CommandError.IOError`. The [CommandError](../reference/command-error.md) reference lists every case.
