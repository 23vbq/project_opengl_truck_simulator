import java.util.Random;

public class HeightMap {

    private static final int DEFAULT_SIZE = 170;
    private static final float DEFAULT_HEIGHT_SCALE = 16.0f;

    private final int size;
    private final float[][] grid;
    private final float heightScale;

    public HeightMap() {
        this(DEFAULT_SIZE, DEFAULT_HEIGHT_SCALE, new Random().nextInt());
    }

    public HeightMap(int size, float heightScale, int seed) {
        this.size = size;
        this.heightScale = heightScale;
        this.grid = new float[size][size];
        generate(seed);
    }

    public void generate(int seed) {
        PerlinNoise noise = new PerlinNoise(seed);

        float frequency = 0.020f;
        int octaves = 5;
        float persistence = 0.45f;

        float minHeight = Float.MAX_VALUE;
        float maxHeight = -Float.MAX_VALUE;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                float amplitude = 1.0f;
                float maxAmplitude = 0.0f;
                float sampleFrequency = frequency;
                float height = 0.0f;

                for (int octave = 0; octave < octaves; octave++) {
                    float noiseValue = noise.noise(x * sampleFrequency, z * sampleFrequency);
                    height += noiseValue * amplitude;
                    maxAmplitude += amplitude;
                    amplitude *= persistence;
                    sampleFrequency *= 2.0f;
                }

                height /= maxAmplitude;
                grid[x][z] = height;
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
            }
        }

        float range = maxHeight - minHeight;
        if (range < 0.0001f) {
            range = 1.0f;
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                float normalized = (grid[x][z] - minHeight) / range;
                grid[x][z] = normalized;
            }
        }

        smoothGrid(3);
    }

    public float getHeight(int gridX, int gridZ) {
        int clampedX = Math.max(0, Math.min(size - 1, gridX));
        int clampedZ = Math.max(0, Math.min(size - 1, gridZ));
        return grid[clampedX][clampedZ] * heightScale;
    }

    public float getHeight(float worldX, float worldZ) {
        float clampedX = Math.max(0.0f, Math.min(size - 1.001f, worldX));
        float clampedZ = Math.max(0.0f, Math.min(size - 1.001f, worldZ));

        int x0 = (int) clampedX;
        int z0 = (int) clampedZ;
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        float tx = clampedX - x0;
        float tz = clampedZ - z0;

        float h00 = getHeight(x0, z0);
        float h10 = getHeight(x1, z0);
        float h01 = getHeight(x0, z1);
        float h11 = getHeight(x1, z1);

        float h0 = lerp(h00, h10, tx);
        float h1 = lerp(h01, h11, tx);
        return lerp(h0, h1, tz);
    }

    public float getNormalizedHeight(int x, int z) {
        int clampedX = Math.max(0, Math.min(size - 1, x));
        int clampedZ = Math.max(0, Math.min(size - 1, z));
        return grid[clampedX][clampedZ];
    }

    public int getSize() {
        return size;
    }

    public float getHeightScale() {
        return heightScale;
    }

    private void smoothGrid(int passes) {
        float[][] temp = new float[size][size];

        for (int pass = 0; pass < passes; pass++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    float weightedSum = 0.0f;
                    float totalWeight = 0.0f;

                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = Math.max(0, Math.min(size - 1, x + dx));
                            int nz = Math.max(0, Math.min(size - 1, z + dz));

                            float weight = (dx == 0 && dz == 0) ? 4.0f : ((dx == 0 || dz == 0) ? 2.0f : 1.0f);
                            weightedSum += grid[nx][nz] * weight;
                            totalWeight += weight;
                        }
                    }

                    temp[x][z] = weightedSum / totalWeight;
                }
            }

            for (int x = 0; x < size; x++) {
                System.arraycopy(temp[x], 0, grid[x], 0, size);
            }
        }
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static class PerlinNoise {
        private final int[] perm = new int[512];

        PerlinNoise(int seed) {
            int[] p = new int[256];
            for (int i = 0; i < p.length; i++) {
                p[i] = i;
            }

            Random random = new Random(seed);
            for (int i = p.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temp = p[i];
                p[i] = p[j];
                p[j] = temp;
            }

            for (int i = 0; i < 512; i++) {
                perm[i] = p[i & 255];
            }
        }

        float noise(float x, float y) {
            int xi = fastFloor(x) & 255;
            int yi = fastFloor(y) & 255;
            float xf = x - fastFloor(x);
            float yf = y - fastFloor(y);

            float u = fade(xf);
            float v = fade(yf);

            int aa = perm[perm[xi] + yi];
            int ab = perm[perm[xi] + yi + 1];
            int ba = perm[perm[xi + 1] + yi];
            int bb = perm[perm[xi + 1] + yi + 1];

            float x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1.0f, yf), u);
            float x2 = lerp(grad(ab, xf, yf - 1.0f), grad(bb, xf - 1.0f, yf - 1.0f), u);
            return (lerp(x1, x2, v) + 1.0f) * 0.5f;
        }

        private float fade(float t) {
            return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
        }

        private float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private float grad(int hash, float x, float y) {
            int h = hash & 7;
            float u = h < 4 ? x : y;
            float v = h < 4 ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        private int fastFloor(float value) {
            return value >= 0.0f ? (int) value : (int) value - 1;
        }
    }
}
