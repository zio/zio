package zio.internal

/**
 * ZScheduler implementation optimized to reduce worker park and unpark frequency.
 * By introducing a brief spin-wait or yielding phase before parking, we can
 * significantly decrease the overhead of thread context switching during high-throughput
 * workload bursts.
 */
private[zio] abstract class ZScheduler
