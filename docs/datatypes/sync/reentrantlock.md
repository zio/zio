---
id: reentrantlock 
title: "ReentrantLock"
---

A `ReentrantLock` is a synchronization data type that may be locked multiple times by the same fiber. When a fiber locks a reentrant lock, it will become the owner of that lock. Other threads cannot obtain the lock unless the lock owner unlocks the lock. As the lock is reentrant, the lock owner can call the lock again, multiple times.

When a fiber attempt to acquire the lock using `ReentrantLock#lock` one of the following cases will happen:

1. If the lock is not held by another fiber, it will acquire the lock. The call to the `ReentrantLock#lock` returns immediately, and the _hold count_ increased by one.

2. If the current fiber already holds the lock, then the _hold count_ is incremented by one, and the method returns immediately. Due to the reentrancy feature of the lock, its owner can acquire the lock multiple times.

3. If the lock is held by another fiber, then the current fiber will be put to sleep until the lock has been acquired, at which point the lock hold count will be reset to one.
