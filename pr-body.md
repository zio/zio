# Merged Fiber(Runtime) and Promise types in ZIO

This PR implements the proposal to merge Fiber(Runtime) and Promise types in ZIO.

## Changes
- Added `Promise.become` method to link fibers/promises
- Added unified `Fiber` type implementation
- Updated all related APIs and implementations
- Added comprehensive tests

## Related Issues
Addresses #9877: Can Fiber(Runtime) and Promise be merged?

## Implementation
The implementation follows the design where:
- A Promise awaiting completion is essentially a Fiber parked awaiting an async callback
- When a Fiber is forking work (which will eventually complete a promise), then awaiting a Promise, we avoid unnecessary allocations + indirection
- Added `Promise.become` method to link fibers/promises

## Testing
- All existing tests pass with the new implementation
- Added new tests for Promise.become functionality