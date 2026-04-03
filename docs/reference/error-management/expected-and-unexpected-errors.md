---
id: expected-and-unexpected-errors
title: "Expected and Unexpected Errors"
---

Inside an application, there are two distinct categories of errors:
- **Expected errors** are those that are expected to occur, and we tend to recover them. They are also known as _recoverable errors_ or _declared errors_.
- **Unexpected errors** are those that are not expected to occur, and they are not recoverable. They are also known as _non-recoverable errors_ or _defects_. 

## Expected Errors

Expected errors are those errors in which we expected them to happen in normal circumstances, and we can't prevent them. They can be predicted upfront, and we can plan for them. We know when, where, and why they occur. So we know when, where, and how to handle these errors. By handling them we can recover from the failure, this is why we say they are _recoverable errors_. All domain errors, business errors are expected once because we talk about them in workflows and user stories, so we know about them in the context of business flows.

For example, when accessing an external database, that database might be down for some short period of time, so we retry to connect again, or after some number of attempts, we might decide to use an alternative solution, e.g. using an in-memory database.

## Unexpected Errors

We know there is a category of things that we are not going to expect and plan for. These are the things we don't expect but of course, we know they are going to happen. We don't know what is the exact root of these errors at runtime, so we have no idea how to handle them. They are actually going to bring down our production application, and then we have to figure out what went wrong to fix them.

For example, the corrupted database file will cause an unexpected error. We can't handle that in runtime. It may be necessary to shut down the whole application in order to prevent further damage.

Most of the unexpected errors are rooted in programming errors. This means, we have just tested the _happy path_, so in case of _unhappy path_ we encounter a defect. When we have defects in our code we have no way of knowing about them otherwise we investigate, test, and fix them.

One of the common programming errors is forgetting to validate unexpected errors that may occur when we expect an input but the input is not valid, while we haven't validated the input. When the user inputs the invalid data, we might encounter the divide by zero exception or might corrupt our service state or a cause similar defect. 

These kinds of defects are common when we upgrade our service with the new data model for its input, while one of the other services is not upgraded with the new data contract and is calling our service with the deprecated data model. If we haven't a validation phase, they will cause defects!

Another example of defects is _memory errors_ like _buffer overflows_, _stack overflows_, _out-of-memory_, _invalid access to null pointers_, and so forth. Most of the time these unexpected errors are occurs when we haven't written a memory-safe and resource-safe program, or they might occur due to hardware issues or uncontrollable external problems. We as a developer don't know how to cope with these types of errors at runtime. We should investigate to find the exact root cause of these defects.

As we cannot handle unexpected errors, we should instead log them with their respective stack traces and contextual information. So later we could investigate the problem and try to fix them. The best we can do with unexpected errors is to _sandbox_ them to limit the damage that they do to the overall application. For example, an unexpected error in browser extension shouldn't crash the whole browser.

## Best Practices

So the best practice for each of these errors is as follows:

### Expected Errors

we handle expected errors with the aid of the Scala compiler, by pushing them into the type system. In ZIO there is the error type parameter called `E`, and this error type parameter is for modeling all the expected errors in the application.

A ZIO value has a type parameter `E` which is the type of _declared errors_ it can fail with. `E` only covers the errors which were specified at the outset. The same ZIO value could still throw exceptions in unforeseen ways. These unforeseen situations are called _defects_ in a ZIO program, and they lie outside `E`.

Bringing abnormal situations from the domain of defects into that of `E` enables the compiler to help us keep a tab on error conditions throughout the application, at compile time. This helps ensure the handling of domain errors in domain-specific ways.

### Unexpected Errors

We encode unexpected errors by not reflecting them to the type system because there is no way we could do it, and it wouldn't provide any value if we could. At best as we can, we simply sandbox that to some well-defined area of the application.

Note that _defects_, can creep silently to higher levels in our application, and, if they get triggered at all, their handling might eventually be in more general ways.

So for ZIO, expected errors are reflected in the type of the ZIO effect, whereas unexpected errors are not so reflective, and that is the distinction.

That is the best practice. It helps us write better code. The code that we can reason about its error properties and potential expected errors. We can look at the ZIO effect and know how it is supposed to fail.

## Conclusion

1. Unexpected errors are impossible to recover, and they will eventually shut down the application but expected errors can be recovered by handling them.
2. We do not type unexpected errors, but we type expected errors either explicitly or using general `Throwable` error type.
3. Unexpected errors mostly is a sign of programming errors, but expected errors part of domain errors.
4. Even though we haven't any clue on how to handle defects, we might still need to do some operation, before letting them crash the application. So in such a situation, we can [catch defects](recovering/catching.md) do following operations, and then rethrow them again:
  - logging the defect to a log aggregator
  - sending an email to alert developers
  - displaying a nice "unexpected error" message to the user
  - etc.
