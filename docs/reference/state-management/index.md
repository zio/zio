---
id: index
title: "Introduction to State Management in ZIO"
---

When we write a program, more often we need to keep track of some sort of state during the execution of the program. If an object has a state, its behavior is influenced by passing the time.

Here are some examples:

- **Counter**— Assume a RESTful API, which has a set of endpoints, and wants to keep track of how many requests have been made to each endpoint.
- **Bank Account Balance**— Each bank account has a balance, and it can be deposited or withdrawn. So its value is changing over time.
- **Temperature**— The temperature of a room is changing over time.
- **List length**— When we are iterating over a list of items, we might need to keep track of the number of items we have seen so far. So during the calculation of the length of the list, we need an intermediate state that records the number of items we have seen so far.

In imperative programming, one common way to store the state is using a variable. So we can update their values in place. But this approach can introduce bugs, especially when the state is shared between multiple components. So it is better to avoid using variables to keep track of the state.

From the aspect of concurrency, we have two general approaches to maintaining the state in functional programming:
1. **[Recursion](state-management-using-recursion.md)**— In this approach, we can update the state by passing the new state to the next component. This is a very easy way to maintain the state, but it can't be used in a concurrent environment, because we can't share the state between multiple fibers.

2. **[Concurrent](concurrent-state-management.md)**— ZIO has a powerful data type called `Ref`, which is the description of a mutable reference. We can use `Ref` to share the state between multiple fibers, e.g. producer and consumer components.

In this section, we will talk about these two approaches.
