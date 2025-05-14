import zio.*;
import cats.effect.IO;
import cats.effect.unsafe.implicits.global;

public class RacePerformanceTest {
    private static final int ITERATIONS = 10000;

    public static void main(String[] args) {
        System.out.println("Starting performance test...");
        System.out.println("Running " + ITERATIONS + " iterations for each implementation");

        // Warmup
        long catsWarmupTime = runCatsRace();
        System.out.println("Cats-Effect warmup time (ns): " + catsWarmupTime);

        long zioWarmupTime = runZioRace();
        System.out.println("ZIO warmup time (ns): " + zioWarmupTime);

        // Actual test
        long catsTime = runCatsRace();
        System.out.println("Cats-Effect time (ns): " + catsTime);

        long zioTime = runZioRace();
        System.out.println("ZIO time (ns): " + zioTime);

        double ratio = (double) catsTime / zioTime;
        System.out.println("Performance ratio: Cats-Effect / ZIO = " + ratio);
        System.out.println("ZIO is " + ratio + "x faster than Cats-Effect");
    }

    private static long runCatsRace() {
        long startTime = System.nanoTime();

        int result = loop(0);
        if (result != ITERATIONS) {
            throw new RuntimeException("Unexpected result: " + result);
        }

        long endTime = System.nanoTime();
        return endTime - startTime;
    }

    private static int loop(int i) {
        if (i < ITERATIONS) {
            return IO.race(IO.never(), IO.delay(() -> i + 1))
                    .flatMap(either -> either.fold(
                            left -> IO.pure(left), // This should never happen
                            right -> loop(right)
                    ))
                    .unsafeRunSync();
        } else {
            return i;
        }
    }

    private static long runZioRace() {
        long startTime = System.nanoTime();

        int result = Unsafe.unsafe(unsafe -> {
            return Runtime.default_().unsafe.run(
                    zioLoop(0)
            ).getOrThrowFiberFailure();
        });

        if (result != ITERATIONS) {
            throw new RuntimeException("Unexpected result: " + result);
        }

        long endTime = System.nanoTime();
        return endTime - startTime;
    }

    private static ZIO<Object, Object, Integer> zioLoop(int i) {
        if (i < ITERATIONS) {
            return ZIO.never().race(ZIO.succeed(i + 1))
                    .flatMap(either -> either.fold(
                            left -> ZIO.succeed(left), // This should never happen
                            right -> zioLoop(right)
                    ));
        } else {
            return ZIO.succeed(i);
        }
    }
}