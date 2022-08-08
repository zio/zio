---
id: index
title: "Three Types of Errors"
---

We should consider three types of errors when writing ZIO applications:

1. **[Failures](failures.md)** are expected errors. We use `ZIO.fail` to model failures. As they are expected, we know how to handle them. We should handle these errors and prevent them from propagating throughout the call stack.

2. **[Defects](defects.md)** are unexpected errors. We use `ZIO.die` to model a defect. As they are not expected, we need to propagate them through the application stack, until in the upper layers one of the following situations happens:

    - In one of the upper layers, it makes sense to expect these errors. So we will convert them to failure, and then they can be handled.
    - None of the upper layers won't catch these errors, so it will finally crash the whole application.

3. **[Fatals](fatals.md)** are catastrophic unexpected errors. When they occur we should kill the application immediately without propagating the error furthermore. At most, we might need to log the error and print its call stack.

