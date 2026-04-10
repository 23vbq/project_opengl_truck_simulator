public class Road {
    private static final float ROAD_WIDTH = 8.0f;
    private static final float ROAD_HEIGHT_OFFSET = 0.55f;
    private static final float ROAD_SNAP_MARGIN = 1.0f;
    private static final int SAMPLES_PER_SECTION = 36;
    private float[][] pathPoints; // Punkty kontrolne drogi (x, z)
    private float[] sampleX;
    private float[] sampleZ;
    private float[] sampleDirX;
    private float[] sampleDirZ;
    private float[] sampleY;
    private float[] sampleLength;
    private int sampleCount;
    
    public Road(HeightMap heightMap) {
        generateWaypoints(heightMap);
        rebuildRoadSamples(heightMap);
    }
    
    private void generateWaypoints(HeightMap heightMap) {
        float mapSize = heightMap.getSize();
        float centerX = (mapSize - 1) * 0.5f;
        float centerZ = (mapSize - 1) * 0.5f;
        
        // Punkty kontrolne drogi - zakrętyi przez mapę
        pathPoints = new float[][] {
            { centerX * 0.2f, centerZ * 0.2f },      // Start lewy-górny
            { centerX * 0.5f, centerZ * 0.1f },      // Prawo-górny
            { centerX * 0.9f, centerZ * 0.3f },      // Daleko prawo-górny
            { centerX * 1.3f, centerZ * 0.7f },      // Prawo-środek
            { centerX * 1.1f, centerZ * 1.5f },      // Prawo-dół
            { centerX * 0.6f, centerZ * 1.5f },      // Środek-dół
            { centerX * 0.1f, centerZ * 1.2f },      // Lewo-dół
            { centerX * 0.05f, centerZ * 0.7f },     // Lewo-środek
            { centerX * 0.2f, centerZ * 0.2f }       // Zamknij pętlę
        };
    }
    
    public void draw(com.jogamp.opengl.GL2 gl, HeightMap heightMap, long time) {
        if (sampleCount < 2) {
            return;
        }

        // Rysuj asfalt
        drawAsphalt(gl);
        
        // Rysuj animowane linie
        drawCenterLines(gl, time);
        
        // Rysuj krawężniki
        drawEdgeLines(gl);
    }
    
    // Interpolacja Catmull-Rom między punktami drogi dla gładkich zakrętów
    public float getDistanceToRoad(float x, float z) {
        return getNearestRoadDistanceAndHeight(x, z)[0];
    }

            public float getSurfaceHeightAt(HeightMap heightMap, float x, float z) {
        if (sampleCount < 2) {
            rebuildRoadSamples(heightMap);
        }

        float[] nearest = getNearestRoadDistanceAndHeight(x, z);
        if (nearest[0] <= (ROAD_WIDTH * 0.5f + ROAD_SNAP_MARGIN)) {
            return nearest[1];
        }
        return Float.NaN;
            }

    private float[] getNearestRoadDistanceAndHeight(float x, float z) {
        float minDistSq = Float.MAX_VALUE;
        float bestY = Float.NaN;

        for (int i = 0; i < sampleCount - 1; i++) {
            float ax = sampleX[i];
            float az = sampleZ[i];
            float bx = sampleX[i + 1];
            float bz = sampleZ[i + 1];

            float abX = bx - ax;
            float abZ = bz - az;
            float abLenSq = abX * abX + abZ * abZ;
            if (abLenSq < 0.000001f) {
                continue;
            }

            float apX = x - ax;
            float apZ = z - az;
            float t = (apX * abX + apZ * abZ) / abLenSq;
            t = clamp(t, 0.0f, 1.0f);

            float cx = ax + abX * t;
            float cz = az + abZ * t;
            float dx = x - cx;
            float dz = z - cz;
            float distSq = dx * dx + dz * dz;

            if (distSq < minDistSq) {
                minDistSq = distSq;
                bestY = sampleY[i] + (sampleY[i + 1] - sampleY[i]) * t;
            }
        }

        return new float[] { (float) Math.sqrt(minDistSq), bestY };
    }

    private void rebuildRoadSamples(HeightMap heightMap) {
        int sections = Math.max(1, pathPoints.length - 1);
        sampleCount = sections * SAMPLES_PER_SECTION + 1;

        sampleX = new float[sampleCount];
        sampleZ = new float[sampleCount];
        sampleDirX = new float[sampleCount];
        sampleDirZ = new float[sampleCount];
        sampleY = new float[sampleCount];
        sampleLength = new float[sampleCount];

        float maxT = (float) sections;
        float dt = maxT / (sampleCount - 1);

        for (int i = 0; i < sampleCount; i++) {
            float t = i * dt;
            float[] p = interpolatePath(t);
            float[] pNext = interpolatePath(t + dt);

            sampleX[i] = p[0];
            sampleZ[i] = p[1];

            float dx = pNext[0] - p[0];
            float dz = pNext[1] - p[1];
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len < 0.0001f) {
                sampleDirX[i] = (i > 0) ? sampleDirX[i - 1] : 1.0f;
                sampleDirZ[i] = (i > 0) ? sampleDirZ[i - 1] : 0.0f;
            } else {
                sampleDirX[i] = dx / len;
                sampleDirZ[i] = dz / len;
            }

            float perpUnitX = -sampleDirZ[i];
            float perpUnitZ = sampleDirX[i];
            sampleY[i] = getRoadSurfaceHeight(heightMap, sampleX[i], sampleZ[i], perpUnitX, perpUnitZ);
        }

        smoothSampleHeights();

        sampleLength[0] = 0.0f;
        for (int i = 1; i < sampleCount; i++) {
            float dx = sampleX[i] - sampleX[i - 1];
            float dz = sampleZ[i] - sampleZ[i - 1];
            sampleLength[i] = sampleLength[i - 1] + (float) Math.sqrt(dx * dx + dz * dz);
        }
    }

    private void smoothSampleHeights() {
        if (sampleCount < 5) {
            return;
        }

        float[] smoothed = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int i0 = Math.max(0, i - 2);
            int i1 = Math.min(sampleCount - 1, i + 2);

            float weightedSum = 0.0f;
            float weightTotal = 0.0f;
            for (int k = i0; k <= i1; k++) {
                float w = (k == i) ? 0.40f : ((Math.abs(k - i) == 1) ? 0.22f : 0.08f);
                weightedSum += sampleY[k] * w;
                weightTotal += w;
            }
            smoothed[i] = weightedSum / Math.max(0.0001f, weightTotal);
        }
        sampleY = smoothed;
    }

    private float getRoadSurfaceHeight(HeightMap heightMap, float centerX, float centerZ, float perpUnitX, float perpUnitZ) {
        float halfWidth = ROAD_WIDTH * 0.5f;
        float[] sampleOffsets = new float[] { -1.0f, -0.65f, -0.3f, 0.0f, 0.3f, 0.65f, 1.0f };

        float maxHeight = heightMap.getHeight(centerX, centerZ);
        for (int i = 0; i < sampleOffsets.length; i++) {
            float side = sampleOffsets[i] * halfWidth;
            float sampleX = centerX + perpUnitX * side;
            float sampleZ = centerZ + perpUnitZ * side;
            float h = heightMap.getHeight(sampleX, sampleZ);
            if (h > maxHeight) {
                maxHeight = h;
            }
        }

        return maxHeight + ROAD_HEIGHT_OFFSET;
    }
    
    private float[] interpolatePath(float t) {
        float maxT = (float) (pathPoints.length - 1);
        while (t < 0.0f) {
            t += maxT;
        }
        while (t >= maxT) {
            t -= maxT;
        }

        // t jest w zakresie [0, pathPoints.length-1]
        int p0 = (int) Math.floor(t);
        int p1 = (p0 + 1) % pathPoints.length;
        int p2 = (p1 + 1) % pathPoints.length;
        int p3 = (p2 + 1) % pathPoints.length;
        
        float localT = t - p0;
        
        // Catmull-Rom
        float tt = localT * localT;
        float ttt = tt * localT;
        
        float c0 = -0.5f * ttt + tt - 0.5f * localT;
        float c1 = 1.5f * ttt - 2.5f * tt + 1.0f;
        float c2 = -1.5f * ttt + 2.0f * tt + 0.5f * localT;
        float c3 = 0.5f * ttt - 0.5f * tt;
        
        float x = c0 * pathPoints[p0][0] + c1 * pathPoints[p1][0] + c2 * pathPoints[p2][0] + c3 * pathPoints[p3][0];
        float z = c0 * pathPoints[p0][1] + c1 * pathPoints[p1][1] + c2 * pathPoints[p2][1] + c3 * pathPoints[p3][1];
        
        return new float[] { x, z };
    }
    
    private void drawAsphalt(com.jogamp.opengl.GL2 gl) {
        gl.glColor3f(0.2f, 0.2f, 0.22f); // Ciemny asfalt
        gl.glBegin(com.jogamp.opengl.GL2.GL_QUAD_STRIP);

        float halfWidth = ROAD_WIDTH * 0.5f;
        for (int i = 0; i < sampleCount; i++) {
            float perpX = -sampleDirZ[i] * halfWidth;
            float perpZ = sampleDirX[i] * halfWidth;
            float y = sampleY[i];

            gl.glVertex3f(sampleX[i] - perpX, y, sampleZ[i] - perpZ);
            gl.glVertex3f(sampleX[i] + perpX, y, sampleZ[i] + perpZ);
        }
        
        gl.glEnd();
    }
    
    private void drawCenterLines(com.jogamp.opengl.GL2 gl, long time) {
        gl.glColor3f(1.0f, 1.0f, 0.8f); // Żółty
        
        float lineWidth = 0.4f;
        float dashLength = 4.5f;
        float gapLength = 3.5f;
        float cycle = dashLength + gapLength;
        float scrollOffset = (time * 0.012f) % cycle;

        gl.glBegin(com.jogamp.opengl.GL2.GL_TRIANGLES);

        for (int i = 0; i < sampleCount - 1; i++) {
            float segmentLen = sampleLength[i + 1] - sampleLength[i];
            if (segmentLen < 0.001f) {
                continue;
            }

            float phase = (sampleLength[i] + scrollOffset) % cycle;
            if (phase < dashLength) {
                float perpX = -sampleDirZ[i] * lineWidth * 0.5f;
                float perpZ = sampleDirX[i] * lineWidth * 0.5f;

                float x1 = sampleX[i];
                float z1 = sampleZ[i];
                float x2 = sampleX[i + 1];
                float z2 = sampleZ[i + 1];
                float y1 = sampleY[i] + 0.025f;
                float y2 = sampleY[i + 1] + 0.025f;

                gl.glVertex3f(x1 - perpX, y1, z1 - perpZ);
                gl.glVertex3f(x2 - perpX, y2, z2 - perpZ);
                gl.glVertex3f(x1 + perpX, y1, z1 + perpZ);

                gl.glVertex3f(x1 + perpX, y1, z1 + perpZ);
                gl.glVertex3f(x2 - perpX, y2, z2 - perpZ);
                gl.glVertex3f(x2 + perpX, y2, z2 + perpZ);
            }
        }
        
        gl.glEnd();
    }
    
    private void drawEdgeLines(com.jogamp.opengl.GL2 gl) {
        gl.glColor3f(0.8f, 0.8f, 0.2f); // Jaśniejszy żółty na krawędziach
        
        float halfWidth = ROAD_WIDTH / 2.0f;
        gl.glLineWidth(2.0f);

        gl.glBegin(com.jogamp.opengl.GL2.GL_LINES);

        for (int i = 0; i < sampleCount - 1; i++) {
            float perpX1 = -sampleDirZ[i] * halfWidth;
            float perpZ1 = sampleDirX[i] * halfWidth;
            float perpX2 = -sampleDirZ[i + 1] * halfWidth;
            float perpZ2 = sampleDirX[i + 1] * halfWidth;

            float y1 = sampleY[i] + 0.015f;
            float y2 = sampleY[i + 1] + 0.015f;

            gl.glVertex3f(sampleX[i] - perpX1, y1, sampleZ[i] - perpZ1);
            gl.glVertex3f(sampleX[i + 1] - perpX2, y2, sampleZ[i + 1] - perpZ2);

            gl.glVertex3f(sampleX[i] + perpX1, y1, sampleZ[i] + perpZ1);
            gl.glVertex3f(sampleX[i + 1] + perpX2, y2, sampleZ[i + 1] + perpZ2);
        }
        
        gl.glLineWidth(1.0f);
        gl.glEnd();
    }

    public void drawWetOverlay(com.jogamp.opengl.GL2 gl, float wetness) {
        if (sampleCount < 2) return;
        float halfWidth = ROAD_WIDTH * 0.5f;
        float alpha = wetness * 0.32f;

        gl.glEnable(com.jogamp.opengl.GL2.GL_BLEND);
        gl.glBlendFunc(com.jogamp.opengl.GL2.GL_SRC_ALPHA, com.jogamp.opengl.GL2.GL_ONE);
        gl.glDisable(com.jogamp.opengl.GL2.GL_LIGHTING);
        gl.glDepthMask(false);

        gl.glBegin(com.jogamp.opengl.GL2.GL_QUAD_STRIP);
        for (int i = 0; i < sampleCount; i++) {
            float perpX = -sampleDirZ[i] * halfWidth;
            float perpZ = sampleDirX[i] * halfWidth;
            float y = sampleY[i] + 0.02f;
            // shimmer: vary alpha slightly along road for puddle effect
            float shimmer = 0.55f + 0.45f * (float) Math.sin(i * 0.38f + System.currentTimeMillis() * 0.0008f);
            gl.glColor4f(0.35f, 0.50f, 0.75f, alpha * shimmer);
            gl.glVertex3f(sampleX[i] - perpX, y, sampleZ[i] - perpZ);
            gl.glVertex3f(sampleX[i] + perpX, y, sampleZ[i] + perpZ);
        }
        gl.glEnd();

        gl.glDepthMask(true);
        gl.glEnable(com.jogamp.opengl.GL2.GL_LIGHTING);
        gl.glBlendFunc(com.jogamp.opengl.GL2.GL_SRC_ALPHA, com.jogamp.opengl.GL2.GL_ONE_MINUS_SRC_ALPHA);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
