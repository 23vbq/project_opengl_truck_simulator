import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL2;

public class GltfModel {

    private static class Primitive {
        final float[] positions;
        final float[] normals;
        final int[] indices;
        final float[] color;
        final float minX;
        final float minY;
        final float minZ;
        final float maxX;
        final float maxY;
        final float maxZ;
        final int triangleCount;

        Primitive(float[] positions, float[] normals, int[] indices, float[] color, float minX, float minY, float minZ,
                float maxX, float maxY, float maxZ) {
            this.positions = positions;
            this.normals = normals;
            this.indices = indices;
            this.color = color;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.triangleCount = indices.length / 3;
        }

        float maxDimension() {
            float dx = maxX - minX;
            float dy = maxY - minY;
            float dz = maxZ - minZ;
            return Math.max(dx, Math.max(dy, dz));
        }
    }

    private final List<Primitive> primitives;
    private boolean loaded;

    private float boundsMinX;
    private float boundsMinY;
    private float boundsMinZ;
    private float boundsMaxX;
    private float boundsMaxY;
    private float boundsMaxZ;

    private float fitScale;
    private float offsetX;
    private float offsetY;
    private float offsetZ;
    private int displayListId;

    public GltfModel(String gltfPath) {
        primitives = new ArrayList<Primitive>();
        loaded = false;
        fitScale = 1.0f;
        offsetX = 0.0f;
        offsetY = 0.0f;
        offsetZ = 0.0f;
        displayListId = -1;

        try {
            load(gltfPath);
            loaded = !primitives.isEmpty();
        } catch (Exception e) {
            loaded = false;
            System.err.println("[GltfModel] Failed to load model: " + gltfPath + " -> " + e.getMessage());
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void draw(GL2 gl) {
        if (!loaded) {
            return;
        }

        if (displayListId < 0) {
            buildDisplayList(gl);
        }

        gl.glPushMatrix();
        gl.glTranslatef(offsetX, offsetY, offsetZ);
        gl.glScalef(fitScale, fitScale, fitScale);

        gl.glCallList(displayListId);

        gl.glPopMatrix();
    }

    private void buildDisplayList(GL2 gl) {
        displayListId = gl.glGenLists(1);
        if (displayListId == 0) {
            return;
        }

        gl.glNewList(displayListId, GL2.GL_COMPILE);
        for (int p = 0; p < primitives.size(); p++) {
            Primitive primitive = primitives.get(p);
            drawPrimitive(gl, primitive);
        }
        gl.glEndList();
    }

    private void drawPrimitive(GL2 gl, Primitive primitive) {
        float[] positions = primitive.positions;
        float[] normals = primitive.normals;
        int[] indices = primitive.indices;

        float[] c = primitive.color;
        boolean transparent = c[3] < 0.995f;
        if (transparent) {
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDepthMask(false);
        }

        gl.glColor4f(c[0], c[1], c[2], c[3]);

        gl.glBegin(GL2.GL_TRIANGLES);
        for (int i = 0; i < indices.length; i++) {
            int vertexIndex = indices[i] * 3;
            if (vertexIndex + 2 >= positions.length) {
                continue;
            }

            if (normals != null && vertexIndex + 2 < normals.length) {
                gl.glNormal3f(normals[vertexIndex], normals[vertexIndex + 1], normals[vertexIndex + 2]);
            }

            gl.glVertex3f(positions[vertexIndex], positions[vertexIndex + 1], positions[vertexIndex + 2]);
        }
        gl.glEnd();

        if (transparent) {
            gl.glDepthMask(true);
            gl.glDisable(GL2.GL_BLEND);
        }
    }

    @SuppressWarnings("unchecked")
    private void load(String gltfPath) throws Exception {
        Path jsonPath = resolvePath(gltfPath);
        if (!Files.exists(jsonPath)) {
            throw new IOException("glTF file not found: " + jsonPath.toString());
        }
        String jsonText = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);

        Object parsed = new JsonParser(jsonText).parseValue();
        if (!(parsed instanceof Map)) {
            throw new IllegalStateException("Invalid glTF JSON");
        }

        Map<String, Object> root = (Map<String, Object>) parsed;
        List<Object> buffers = asList(root.get("buffers"));
        if (buffers.isEmpty()) {
            throw new IllegalStateException("No buffers in glTF");
        }

        Map<String, Object> buffer0 = asMap(buffers.get(0));
        String binUri = asString(buffer0.get("uri"));
        if (binUri.length() == 0) {
            throw new IllegalStateException("Buffer uri missing in glTF");
        }
        Path binPath = jsonPath.getParent().resolve(binUri).normalize();
        if (!Files.exists(binPath)) {
            throw new IOException("Binary buffer missing: " + binPath.toString());
        }
        byte[] binData = readBinary(binPath);
        ByteBuffer byteBuffer = ByteBuffer.wrap(binData).order(ByteOrder.LITTLE_ENDIAN);

        List<Object> bufferViews = asList(root.get("bufferViews"));
        List<Object> accessors = asList(root.get("accessors"));
        List<Object> meshes = asList(root.get("meshes"));
        List<Object> materials = asList(root.get("materials"));
        List<Object> nodes = asList(root.get("nodes"));
        List<Object> scenes = asList(root.get("scenes"));
        List<float[]> materialColors = parseMaterialColors(materials);

        List<List<Primitive>> meshTemplates = buildMeshTemplates(meshes, accessors, bufferViews, byteBuffer, materialColors);
        if (meshTemplates.isEmpty()) {
            throw new IllegalStateException("No mesh primitives in glTF");
        }

        primitives.clear();
        initBounds();

        instantiateSceneNodes(root, scenes, nodes, meshTemplates);

        // Fallback when scene graph is missing or malformed.
        if (primitives.isEmpty()) {
            for (int m = 0; m < meshTemplates.size(); m++) {
                List<Primitive> meshPrims = meshTemplates.get(m);
                for (int p = 0; p < meshPrims.size(); p++) {
                    Primitive primitive = meshPrims.get(p);
                    primitives.add(primitive);
                    updateBounds(new float[] { primitive.minX, primitive.minY, primitive.minZ, primitive.maxX, primitive.maxY,
                            primitive.maxZ });
                }
            }
        }

        removeSuspiciousPrimitives();

        if (primitives.isEmpty()) {
            throw new IllegalStateException("No drawable primitives loaded from glTF");
        }

        finalizeTransform();
    }

    private List<List<Primitive>> buildMeshTemplates(List<Object> meshes, List<Object> accessors, List<Object> bufferViews,
            ByteBuffer byteBuffer, List<float[]> materialColors) {
        List<List<Primitive>> templates = new ArrayList<List<Primitive>>();

        for (int m = 0; m < meshes.size(); m++) {
            Map<String, Object> mesh = asMap(meshes.get(m));
            List<Object> meshPrimitives = asList(mesh.get("primitives"));
            List<Primitive> meshTemplate = new ArrayList<Primitive>();

            for (int p = 0; p < meshPrimitives.size(); p++) {
                Map<String, Object> primitive = asMap(meshPrimitives.get(p));
                int mode = getInt(primitive, "mode", 4);
                if (mode != 4) {
                    continue;
                }

                Map<String, Object> attributes = asMap(primitive.get("attributes"));
                if (!attributes.containsKey("POSITION")) {
                    continue;
                }

                int positionAccessorIndex = ((Number) attributes.get("POSITION")).intValue();
                float[] positions = readFloatAccessor(accessors, bufferViews, byteBuffer, positionAccessorIndex, 3);
                if (positions.length == 0) {
                    continue;
                }

                float[] normals = null;
                if (attributes.containsKey("NORMAL")) {
                    int normalAccessorIndex = ((Number) attributes.get("NORMAL")).intValue();
                    normals = readFloatAccessor(accessors, bufferViews, byteBuffer, normalAccessorIndex, 3);
                    if (normals.length != positions.length) {
                        normals = null;
                    }
                }

                int[] indices;
                if (primitive.containsKey("indices")) {
                    int indexAccessorIndex = ((Number) primitive.get("indices")).intValue();
                    indices = readIndexAccessor(accessors, bufferViews, byteBuffer, indexAccessorIndex);
                } else {
                    int vertexCount = positions.length / 3;
                    indices = new int[vertexCount];
                    for (int i = 0; i < vertexCount; i++) {
                        indices[i] = i;
                    }
                }

                if (indices.length < 3) {
                    continue;
                }

                float[] bounds = computeBounds(positions);
                int materialIndex = getInt(primitive, "material", -1);
                float[] color = colorForMaterial(materialColors, materialIndex);

                meshTemplate.add(new Primitive(positions, normals, indices, color, bounds[0], bounds[1], bounds[2], bounds[3],
                        bounds[4], bounds[5]));
            }

            templates.add(meshTemplate);
        }

        return templates;
    }

    private void instantiateSceneNodes(Map<String, Object> root, List<Object> scenes, List<Object> nodes,
            List<List<Primitive>> meshTemplates) {
        if (scenes.isEmpty() || nodes.isEmpty()) {
            return;
        }

        int sceneIndex = getInt(root, "scene", 0);
        if (sceneIndex < 0 || sceneIndex >= scenes.size()) {
            sceneIndex = 0;
        }

        Map<String, Object> scene = asMap(scenes.get(sceneIndex));
        List<Object> rootNodes = asList(scene.get("nodes"));
        float[] identity = identityMatrix();

        for (int i = 0; i < rootNodes.size(); i++) {
            Object nodeIndexValue = rootNodes.get(i);
            if (!(nodeIndexValue instanceof Number)) {
                continue;
            }
            int nodeIndex = ((Number) nodeIndexValue).intValue();
            traverseNode(nodeIndex, identity, nodes, meshTemplates);
        }
    }

    private void traverseNode(int nodeIndex, float[] parentMatrix, List<Object> nodes, List<List<Primitive>> meshTemplates) {
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            return;
        }

        Map<String, Object> node = asMap(nodes.get(nodeIndex));
        float[] local = nodeLocalMatrix(node);
        float[] world = multiplyMat4(parentMatrix, local);

        int meshIndex = getInt(node, "mesh", -1);
        if (meshIndex >= 0 && meshIndex < meshTemplates.size()) {
            List<Primitive> meshPrims = meshTemplates.get(meshIndex);
            for (int i = 0; i < meshPrims.size(); i++) {
                Primitive transformed = transformPrimitive(meshPrims.get(i), world);
                primitives.add(transformed);
                updateBounds(new float[] { transformed.minX, transformed.minY, transformed.minZ, transformed.maxX,
                        transformed.maxY, transformed.maxZ });
            }
        }

        List<Object> children = asList(node.get("children"));
        for (int i = 0; i < children.size(); i++) {
            Object child = children.get(i);
            if (child instanceof Number) {
                traverseNode(((Number) child).intValue(), world, nodes, meshTemplates);
            }
        }
    }

    private Primitive transformPrimitive(Primitive source, float[] matrix) {
        float[] srcPos = source.positions;
        float[] srcNorm = source.normals;

        float[] dstPos = new float[srcPos.length];
        float[] dstNorm = null;
        if (srcNorm != null) {
            dstNorm = new float[srcNorm.length];
        }

        for (int i = 0; i + 2 < srcPos.length; i += 3) {
            float x = srcPos[i];
            float y = srcPos[i + 1];
            float z = srcPos[i + 2];

            dstPos[i] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
            dstPos[i + 1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
            dstPos[i + 2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];

            if (dstNorm != null) {
                float nx = srcNorm[i];
                float ny = srcNorm[i + 1];
                float nz = srcNorm[i + 2];
                float tx = matrix[0] * nx + matrix[4] * ny + matrix[8] * nz;
                float ty = matrix[1] * nx + matrix[5] * ny + matrix[9] * nz;
                float tz = matrix[2] * nx + matrix[6] * ny + matrix[10] * nz;
                float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (len > 0.00001f) {
                    dstNorm[i] = tx / len;
                    dstNorm[i + 1] = ty / len;
                    dstNorm[i + 2] = tz / len;
                } else {
                    dstNorm[i] = 0.0f;
                    dstNorm[i + 1] = 1.0f;
                    dstNorm[i + 2] = 0.0f;
                }
            }
        }

        float[] bounds = computeBounds(dstPos);
        return new Primitive(dstPos, dstNorm, source.indices, source.color, bounds[0], bounds[1], bounds[2], bounds[3],
                bounds[4], bounds[5]);
    }

    private float[] identityMatrix() {
        return new float[] {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        };
    }

    private float[] multiplyMat4(float[] a, float[] b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[0 * 4 + r] * b[c * 4 + 0]
                        + a[1 * 4 + r] * b[c * 4 + 1]
                        + a[2 * 4 + r] * b[c * 4 + 2]
                        + a[3 * 4 + r] * b[c * 4 + 3];
            }
        }
        return out;
    }

    private float[] nodeLocalMatrix(Map<String, Object> node) {
        if (node.containsKey("matrix")) {
            List<Object> m = asList(node.get("matrix"));
            if (m.size() == 16) {
                float[] out = new float[16];
                for (int i = 0; i < 16; i++) {
                    out[i] = ((Number) m.get(i)).floatValue();
                }
                return out;
            }
        }

        float tx = 0.0f;
        float ty = 0.0f;
        float tz = 0.0f;
        if (node.containsKey("translation")) {
            List<Object> t = asList(node.get("translation"));
            if (t.size() >= 3) {
                tx = ((Number) t.get(0)).floatValue();
                ty = ((Number) t.get(1)).floatValue();
                tz = ((Number) t.get(2)).floatValue();
            }
        }

        float qx = 0.0f;
        float qy = 0.0f;
        float qz = 0.0f;
        float qw = 1.0f;
        if (node.containsKey("rotation")) {
            List<Object> q = asList(node.get("rotation"));
            if (q.size() >= 4) {
                qx = ((Number) q.get(0)).floatValue();
                qy = ((Number) q.get(1)).floatValue();
                qz = ((Number) q.get(2)).floatValue();
                qw = ((Number) q.get(3)).floatValue();
            }
        }

        float sx = 1.0f;
        float sy = 1.0f;
        float sz = 1.0f;
        if (node.containsKey("scale")) {
            List<Object> s = asList(node.get("scale"));
            if (s.size() >= 3) {
                sx = ((Number) s.get(0)).floatValue();
                sy = ((Number) s.get(1)).floatValue();
                sz = ((Number) s.get(2)).floatValue();
            }
        }

        float xx = qx * qx;
        float yy = qy * qy;
        float zz = qz * qz;
        float xy = qx * qy;
        float xz = qx * qz;
        float yz = qy * qz;
        float wx = qw * qx;
        float wy = qw * qy;
        float wz = qw * qz;

        float r00 = 1.0f - 2.0f * (yy + zz);
        float r01 = 2.0f * (xy + wz);
        float r02 = 2.0f * (xz - wy);

        float r10 = 2.0f * (xy - wz);
        float r11 = 1.0f - 2.0f * (xx + zz);
        float r12 = 2.0f * (yz + wx);

        float r20 = 2.0f * (xz + wy);
        float r21 = 2.0f * (yz - wx);
        float r22 = 1.0f - 2.0f * (xx + yy);

        return new float[] {
                r00 * sx, r01 * sx, r02 * sx, 0.0f,
                r10 * sy, r11 * sy, r12 * sy, 0.0f,
                r20 * sz, r21 * sz, r22 * sz, 0.0f,
                tx, ty, tz, 1.0f
        };
    }

    private void finalizeTransform() {
        float width = Math.max(0.0001f, boundsMaxX - boundsMinX);
        float height = Math.max(0.0001f, boundsMaxY - boundsMinY);
        float length = Math.max(0.0001f, boundsMaxZ - boundsMinZ);

        float targetWidth = 4.10f;
        float targetHeight = 4.30f;
        float targetLength = 8.10f;

        fitScale = Math.min(targetWidth / width, Math.min(targetHeight / height, targetLength / length));

        float centerX = (boundsMinX + boundsMaxX) * 0.5f;
        float centerZ = (boundsMinZ + boundsMaxZ) * 0.5f;

        // Translate in world units after scale, so center offsets must be scaled.
        offsetX = -centerX * fitScale;
        offsetZ = -centerZ * fitScale;

        // Old procedural truck touched ground around local Y ~= -1.02f; lift slightly to avoid sinking.
        float desiredGroundY = -0.88f;
        offsetY = desiredGroundY - boundsMinY * fitScale;
    }

    private void initBounds() {
        boundsMinX = Float.MAX_VALUE;
        boundsMinY = Float.MAX_VALUE;
        boundsMinZ = Float.MAX_VALUE;
        boundsMaxX = -Float.MAX_VALUE;
        boundsMaxY = -Float.MAX_VALUE;
        boundsMaxZ = -Float.MAX_VALUE;
    }

    private float[] computeBounds(float[] positions) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        for (int i = 0; i + 2 < positions.length; i += 3) {
            float x = positions[i];
            float y = positions[i + 1];
            float z = positions[i + 2];

            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }

        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    private void updateBounds(float[] bounds) {
        float minX = bounds[0];
        float minY = bounds[1];
        float minZ = bounds[2];
        float maxX = bounds[3];
        float maxY = bounds[4];
        float maxZ = bounds[5];

        if (minX < boundsMinX) {
            boundsMinX = minX;
        }
        if (minY < boundsMinY) {
            boundsMinY = minY;
        }
        if (minZ < boundsMinZ) {
            boundsMinZ = minZ;
        }
        if (maxX > boundsMaxX) {
            boundsMaxX = maxX;
        }
        if (maxY > boundsMaxY) {
            boundsMaxY = maxY;
        }
        if (maxZ > boundsMaxZ) {
            boundsMaxZ = maxZ;
        }
    }

    private List<float[]> parseMaterialColors(List<Object> materials) {
        List<float[]> colors = new ArrayList<float[]>();
        for (int i = 0; i < materials.size(); i++) {
            Map<String, Object> material = asMap(materials.get(i));
            String name = asString(material.get("name")).toLowerCase();
            Map<String, Object> pbr = null;
            if (material.containsKey("pbrMetallicRoughness")) {
                pbr = asMap(material.get("pbrMetallicRoughness"));
            }

            float[] color = new float[] { 0.78f, 0.10f, 0.10f, 1.0f };
            if (pbr != null && pbr.containsKey("baseColorFactor")) {
                List<Object> factor = asList(pbr.get("baseColorFactor"));
                if (factor.size() >= 3) {
                    color[0] = ((Number) factor.get(0)).floatValue();
                    color[1] = ((Number) factor.get(1)).floatValue();
                    color[2] = ((Number) factor.get(2)).floatValue();
                    if (factor.size() >= 4) {
                        color[3] = ((Number) factor.get(3)).floatValue();
                    }
                }
            }

            // Heuristic remap for common automotive material names when textures are not sampled.
            if (name.contains("vermelho") || name.contains("carpaint") || name.contains("cor_principal")) {
                color[0] = 0.78f;
                color[1] = 0.10f;
                color[2] = 0.10f;
            } else if (name.contains("chrome") || name.contains("prata") || name.contains("cromado")) {
                color[0] = 0.72f;
                color[1] = 0.74f;
                color[2] = 0.76f;
            } else if (name.contains("vidro") || name.contains("glass") || name.contains("mirror")) {
                color[0] = 0.28f;
                color[1] = 0.40f;
                color[2] = 0.52f;
                color[3] = 0.40f;
            } else if (name.contains("pneu")) {
                color[0] = 0.10f;
                color[1] = 0.10f;
                color[2] = 0.10f;
            }

            // Avoid fully black result when model relies mostly on textures.
            float brightness = color[0] + color[1] + color[2];
            if (brightness < 0.10f) {
                color[0] = 0.22f;
                color[1] = 0.22f;
                color[2] = 0.24f;
            }

            colors.add(color);
        }
        return colors;
    }

    private float[] colorForMaterial(List<float[]> materialColors, int materialIndex) {
        if (materialIndex < 0 || materialIndex >= materialColors.size()) {
            return new float[] { 0.78f, 0.10f, 0.10f, 1.0f };
        }
        float[] c = materialColors.get(materialIndex);
        return new float[] { c[0], c[1], c[2], c[3] };
    }

    private void removeSuspiciousPrimitives() {
        if (primitives.size() < 8) {
            return;
        }

        List<Float> dims = new ArrayList<Float>();
        for (int i = 0; i < primitives.size(); i++) {
            dims.add(Float.valueOf(primitives.get(i).maxDimension()));
        }

        dims.sort(null);
        float median = dims.get(dims.size() / 2).floatValue();
        float threshold = median * 3.5f;

        List<Primitive> kept = new ArrayList<Primitive>();
        for (int i = 0; i < primitives.size(); i++) {
            Primitive p = primitives.get(i);
            float dx = p.maxX - p.minX;
            float dy = p.maxY - p.minY;
            float dz = p.maxZ - p.minZ;

            boolean massiveOutlier = p.maxDimension() > threshold && p.triangleCount < 20000;
            boolean tallCylinderLike = dy > dx * 1.45f && dy > dz * 1.45f && Math.abs(dx - dz) < Math.max(dx, dz) * 0.22f
                    && p.triangleCount < 120000;

            boolean suspicious = massiveOutlier || tallCylinderLike;
            if (!suspicious) {
                kept.add(p);
            }
        }

        if (kept.size() == primitives.size() || kept.isEmpty()) {
            return;
        }

        primitives.clear();
        primitives.addAll(kept);

        initBounds();
        for (int i = 0; i < primitives.size(); i++) {
            Primitive p = primitives.get(i);
            updateBounds(new float[] { p.minX, p.minY, p.minZ, p.maxX, p.maxY, p.maxZ });
        }
    }

    private float[] readFloatAccessor(List<Object> accessors, List<Object> bufferViews, ByteBuffer data, int accessorIndex,
            int expectedComponents) {
        Map<String, Object> accessor = asMap(accessors.get(accessorIndex));
        int count = getInt(accessor, "count", 0);
        if (count <= 0) {
            return new float[0];
        }

        String type = asString(accessor.get("type"));
        int components = componentsForType(type);
        if (components != expectedComponents) {
            return new float[0];
        }

        int componentType = getInt(accessor, "componentType", 0);
        if (componentType != 5126) {
            return new float[0];
        }

        int bufferViewIndex = getInt(accessor, "bufferView", -1);
        if (bufferViewIndex < 0) {
            return new float[0];
        }

        Map<String, Object> bufferView = asMap(bufferViews.get(bufferViewIndex));
        int viewOffset = getInt(bufferView, "byteOffset", 0);
        int accessorOffset = getInt(accessor, "byteOffset", 0);
        int stride = getInt(bufferView, "byteStride", components * 4);
        int base = viewOffset + accessorOffset;

        float[] out = new float[count * components];
        for (int i = 0; i < count; i++) {
            int src = base + i * stride;
            for (int c = 0; c < components; c++) {
                out[i * components + c] = data.getFloat(src + c * 4);
            }
        }

        return out;
    }

    private int[] readIndexAccessor(List<Object> accessors, List<Object> bufferViews, ByteBuffer data, int accessorIndex) {
        Map<String, Object> accessor = asMap(accessors.get(accessorIndex));
        int count = getInt(accessor, "count", 0);
        if (count <= 0) {
            return new int[0];
        }

        int componentType = getInt(accessor, "componentType", 0);
        int bufferViewIndex = getInt(accessor, "bufferView", -1);
        if (bufferViewIndex < 0) {
            return new int[0];
        }

        Map<String, Object> bufferView = asMap(bufferViews.get(bufferViewIndex));
        int viewOffset = getInt(bufferView, "byteOffset", 0);
        int accessorOffset = getInt(accessor, "byteOffset", 0);

        int componentSize = componentSize(componentType);
        if (componentSize == 0) {
            return new int[0];
        }

        int stride = getInt(bufferView, "byteStride", componentSize);
        int base = viewOffset + accessorOffset;

        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int src = base + i * stride;
            out[i] = readIndexValue(data, src, componentType);
        }

        return out;
    }

    private int readIndexValue(ByteBuffer data, int offset, int componentType) {
        if (componentType == 5121) {
            return data.get(offset) & 0xFF;
        }
        if (componentType == 5123) {
            return data.getShort(offset) & 0xFFFF;
        }
        if (componentType == 5125) {
            return data.getInt(offset);
        }
        return 0;
    }

    private int componentSize(int componentType) {
        if (componentType == 5121) {
            return 1;
        }
        if (componentType == 5123) {
            return 2;
        }
        if (componentType == 5125 || componentType == 5126) {
            return 4;
        }
        return 0;
    }

    private int componentsForType(String type) {
        if ("SCALAR".equals(type)) {
            return 1;
        }
        if ("VEC2".equals(type)) {
            return 2;
        }
        if ("VEC3".equals(type)) {
            return 3;
        }
        if ("VEC4".equals(type)) {
            return 4;
        }
        return 0;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value == null) {
            return new ArrayList<Object>();
        }
        return (List<Object>) value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private byte[] readBinary(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private Path resolvePath(String gltfPath) {
        Path direct = Paths.get(gltfPath);
        if (direct.isAbsolute()) {
            return direct.normalize();
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize().resolve(gltfPath).normalize();
        if (Files.exists(cwd)) {
            return cwd;
        }

        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(gltfPath).normalize();
        if (Files.exists(userDir)) {
            return userDir;
        }

        return direct.toAbsolutePath().normalize();
    }

    private static class JsonParser {
        private final String text;
        private int index;

        JsonParser(String text) {
            this.text = text;
            this.index = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalStateException("Unexpected end of JSON");
            }

            char c = text.charAt(index);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                parseNull();
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> map = new LinkedHashMap<String, Object>();

            if (peek('}')) {
                expect('}');
                return map;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();

                if (peek(',')) {
                    expect(',');
                    continue;
                }
                expect('}');
                return map;
            }
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();
            List<Object> list = new ArrayList<Object>();

            if (peek(']')) {
                expect(']');
                return list;
            }

            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();

                if (peek(',')) {
                    expect(',');
                    continue;
                }
                expect(']');
                return list;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (index >= text.length()) {
                        throw new IllegalStateException("Invalid escape sequence");
                    }
                    char esc = text.charAt(index++);
                    if (esc == '"' || esc == '\\' || esc == '/') {
                        sb.append(esc);
                    } else if (esc == 'b') {
                        sb.append('\b');
                    } else if (esc == 'f') {
                        sb.append('\f');
                    } else if (esc == 'n') {
                        sb.append('\n');
                    } else if (esc == 'r') {
                        sb.append('\r');
                    } else if (esc == 't') {
                        sb.append('\t');
                    } else if (esc == 'u') {
                        if (index + 4 > text.length()) {
                            throw new IllegalStateException("Invalid unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    } else {
                        throw new IllegalStateException("Unsupported escape: " + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalStateException("Unterminated string");
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw new IllegalStateException("Invalid boolean value");
        }

        private void parseNull() {
            if (!text.startsWith("null", index)) {
                throw new IllegalStateException("Invalid null value");
            }
            index += 4;
        }

        private Number parseNumber() {
            int start = index;

            if (peek('-')) {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }

            boolean isFractional = false;
            if (peek('.')) {
                isFractional = true;
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }

            if (peek('e') || peek('E')) {
                isFractional = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }

            String numberText = text.substring(start, index);
            try {
                if (isFractional) {
                    return Double.valueOf(numberText);
                }
                return Long.valueOf(numberText);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid number: " + numberText);
            }
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalStateException("Expected '" + expected + "' at index " + index);
            }
            index++;
        }
    }
}
