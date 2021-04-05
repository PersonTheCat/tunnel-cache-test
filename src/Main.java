import java.util.*;

/**
 * These changes demonstrate that caching tunnel spheres one at a time instead of regenerating the
 * sphere every time it is needed can actually be faster when completely avoiding all memory
 * allocations.
 *
 * The idea behind {@link SphereData} is that it will dynamically adapt to use as much space as needed.
 * Once the maximum memory allocation is achieved, there will be no further memory allocations (i.e.
 * no arrays created, no block positions or integers being wrapped, etc.).
 *
 * As a result of these changes, we can observe a ~65.3% time reduction in redundant sphere generation.
 */
public class Main {

    private static final int[] DUMMY_CHUNK_DATA = new int[16 * 16 * 256];
    private static final Random RANDOM = new Random();
    private static final int TEST_SIZE = 1_000;
    private static final int NUM_TESTS = 500;
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 10;
    private static final int WATER_CHECK = 1;
    private static final int GENERATE_SPHERE = 2;
    private static final int DECORATE_SPHERE = 3;

    public static void main(String[] args) {
        final List<TestCase> testCases = generateTestCases();
        Collections.shuffle(testCases, RANDOM);
        getResults(testCases).forEach((name, time) -> System.out.println(name + ": " + time + "ns"));
    }

    private static List<TestCase> generateTestCases() {
        final List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < NUM_TESTS; i ++) {
            testCases.add(new ManualGeneration());
            testCases.add(new CachedGeneration());
        }
        return testCases;
    }

    private static Map<String, Long> getResults(List<TestCase> testCases) {
        final Map<String, Long> results = new HashMap<>();
        final Map<String, Long> sums = new HashMap<>();
        for (TestCase test : testCases) {
            final String name = test.name();
            final long time = test.run();
            if (!sums.containsKey(name)) {
                sums.put(name, time);
            } else {
                final long sum = sums.get(name);
                sums.put(name, sum + time);
            }
        }
        sums.forEach((name, time) -> results.put(name, time / NUM_TESTS));
        return results;
    }

    private static int limitXZ(int xz) {
        return xz < 0 ? 0 : Math.min(xz, 16);
    }

    private static int limitY(int y) {
        return y < 1 ? 1 : Math.min(y, 248);
    }

    private static void write(int x, int y, int z, int d) {
        DUMMY_CHUNK_DATA[x << 12 | z << 8 | y] += d;
    }

    private static abstract class TestCase {

        final long run() {
            final var time = System.nanoTime();
            for (int i = 0; i < TEST_SIZE; i++) {
                final int x = RANDOM.nextInt(16);
                final int y = RANDOM.nextInt(256);
                final int z = RANDOM.nextInt(16);
                final int rad = RANDOM.nextInt(MAX_RADIUS - MIN_RADIUS + 1) + MIN_RADIUS;
                final int miX = limitXZ(x - rad - 1);
                final int maX = limitXZ(x + rad + 1);
                final int miY = limitY(y - rad - 1);
                final int maY = limitY(y + rad + 1);
                final int miZ = limitXZ(z - rad - 1);
                final int maZ = limitXZ(z + rad + 1);
                test(x, y, z, rad, miX, maX, miY, maY, miZ, maZ);
            }
            return (System.nanoTime() - time) / TEST_SIZE;
        }

        abstract void test(int x, int y, int z, int rad, int miX, int maX, int miY, int maY, int miZ, int maZ);
        abstract String name();
    }

    // In this test, we manually generate the sphere 3 times and do work with it.
    private static class ManualGeneration extends TestCase {

        @Override
        void test(int x, int y, int z, int rad, int miX, int maX, int miY, int maY, int miZ, int maZ) {
            // e.g. check for water
            for (int xi = miX; xi < maX; xi++) {
                final double distX = (xi + 0.5 - x) / rad;
                final double distX2 = distX * distX;
                for (int zi = miZ; zi < maZ; zi++) {
                    final double distZ = (zi + 0.5 - z) / rad;
                    final double distZ2 = distZ * distZ;
                    if ((distX2 + distZ2) >= 1.0) {
                        continue;
                    }
                    for (int yi = maY; yi > miY; yi--) {
                        final double distY = ((yi - 1) + 0.5 - y) / rad;
                        final double distY2 = distY * distY;
                        if ((distY > -0.7) && ((distX2 + distY2 + distZ2) < 1.0)) {
                            write(xi, yi, zi, WATER_CHECK);
                        }
                    }
                }
            }
            // e.g. generate sphere
            for (int xi = miX; xi < maX; xi++) {
                final double distX = (xi + 0.5 - x) / rad;
                final double distX2 = distX * distX;
                for (int zi = miZ; zi < maZ; zi++) {
                    final double distZ = (zi + 0.5 - z) / rad;
                    final double distZ2 = distZ * distZ;
                    if ((distX2 + distZ2) >= 1.0) {
                        continue;
                    }
                    for (int yi = maY; yi > miY; yi--) {
                        final double distY = ((yi - 1) + 0.5 - y) / rad;
                        final double distY2 = distY * distY;
                        if ((distY > -0.7) && ((distX2 + distY2 + distZ2) < 1.0)) {
                            write(xi, yi, zi, GENERATE_SPHERE);
                        }
                    }
                }
            }
            // e.g. decorate sphere
            for (int xi = miX; xi < maX; xi++) {
                final double distX = (xi + 0.5 - x) / rad;
                final double distX2 = distX * distX;
                for (int zi = miZ; zi < maZ; zi++) {
                    final double distZ = (zi + 0.5 - z) / rad;
                    final double distZ2 = distZ * distZ;
                    if ((distX2 + distZ2) >= 1.0) {
                        continue;
                    }
                    for (int yi = maY; yi > miY; yi--) {
                        final double distY = ((yi - 1) + 0.5 - y) / rad;
                        final double distY2 = distY * distY;
                        if ((distY > -0.7) && ((distX2 + distY2 + distZ2) < 1.0)) {
                            write(xi, yi, zi, DECORATE_SPHERE);
                        }
                    }
                }
            }
        }

        @Override
        String name() {
            return "Manual Generation";
        }
    }

    // In this test, we generate the sphere exactly 1 time and do work with it 3 times.
    private static class CachedGeneration extends TestCase {

        // For this to be accurate, the cache is only created once.
        private static final SphereData CACHE = new SphereData();

        @Override
        void test(int x, int y, int z, int rad, int miX, int maX, int miY, int maY, int miZ, int maZ) {
            CACHE.reset();
            CACHE.grow(maX - miX, maY - miY, maZ - miZ);
            // e.g. check for water
            for (int xi = miX; xi < maX; xi++) {
                final double distX = (xi + 0.5 - x) / rad;
                final double distX2 = distX * distX;
                for (int zi = miZ; zi < maZ; zi++) {
                    final double distZ = (zi + 0.5 - z) / rad;
                    final double distZ2 = distZ * distZ;
                    if ((distX2 + distZ2) >= 1.0) {
                        continue;
                    }
                    for (int yi = maY; yi > miY; yi--) {
                        final double distY = ((yi - 1) + 0.5 - y) / rad;
                        final double distY2 = distY * distY;
                        if ((distY > -0.7) && ((distX2 + distY2 + distZ2) < 1.0)) {
                            CACHE.add(xi, yi, zi);
                        }
                    }
                }
            }
            CACHE.forEach((xi, yi, zi) -> write(xi, yi, zi, WATER_CHECK));
            CACHE.forEach((xi, yi, zi) -> write(xi, yi, zi, GENERATE_SPHERE));
            CACHE.forEach((xi, yi, zi) -> write(xi, yi, zi, DECORATE_SPHERE));
        }

        @Override
        String name() {
            return "Cached Generation";
        }
    }
}
