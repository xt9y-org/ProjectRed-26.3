package dev.xt9y.projectred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Direct port of ProjectRed's legacy FWireModelGen and FWireFrameModelGen.
 *
 * Framed red alloy, insulated and bundled wires use the same original
 * ProjectRed texture for both conductor and frame, exactly like the old
 * RenderFramedWire pipeline.
 */
final class ProjectRedFramedWireModel {
    private record V(float x, float y, float z, float u, float v) {}
    private record Quad(V a, V b, V c, V d) {}

    private static final Map<Integer, Quad[]> WIRE_CACHE = new ConcurrentHashMap<>();
    private static final Quad[][] FRAME_MODELS = generateFrameModels();

    static void render(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            int thickness,
            int connections,
            int rgb
    ) {
        int mask = connections & 0x3F;
        int key = mask | thickness << 6;
        Quad[] wire = WIRE_CACHE.computeIfAbsent(
                key,
                ignored -> generateWire(thickness, mask)
        );

        emitModel(consumer, pose, light, wire, rgb);
        emitModel(consumer, pose, light, FRAME_MODELS[6], rgb);

        for (int side = 0; side < 6; side++) {
            if ((mask & 1 << side) != 0) {
                emitModel(consumer, pose, light, FRAME_MODELS[side], rgb);
            }
        }
    }

    private static Quad[] generateWire(int thickness, int connMap) {
        int connCount = Integer.bitCount(connMap);
        int tw = thickness + 1;
        float w = tw / 16.0F + .004F;

        List<V> vertices = new ArrayList<>(connCount * 64 + 96);
        for (int side = 0; side < 6; side++) {
            V[] v;
            if (connCount == 0) {
                v = generateStub(side, tw, w);
            } else if (connCount == 1 && (connMap & 1 << (side ^ 1)) != 0) {
                v = generateStub(side, tw, w);
            } else if ((connMap & 1 << side) != 0) {
                v = generateStraight(side, tw, w);
            } else {
                v = generateFlat(side, tw, w, connMap);
            }

            int cycle = side / 2;
            for (int i = 0; i < v.length; i++) {
                v[i] = axisCycle(v[i], cycle);
            }
            add(vertices, v);
        }

        return quads(vertices);
    }

    private static V[] generateStub(int side, int tw, float w) {
        V[] v = faceVerts(side, .5F - w, tw, w);
        for (int i = 0; i < v.length; i++) {
            v[i] = translateUv(v[i], 12, 12);
        }
        return v;
    }

    private static V[] generateStraight(int side, int tw, float w) {
        V[] v = new V[20];
        V[] face = faceVerts(side, 0, tw, w);
        System.arraycopy(face, 0, v, 0, 4);

        if ((side & 1) == 0) {
            v[4] = new V(.5F-w,0,.5F+w,8-tw,24);
            v[5] = new V(.5F+w,0,.5F+w,8+tw,24);
            v[6] = new V(.5F+w,.5F-w,.5F+w,8+tw,16+tw);
            v[7] = new V(.5F-w,.5F-w,.5F+w,8-tw,16+tw);
        } else {
            v[4] = new V(.5F-w,.5F+w,.5F+w,8-tw,16-tw);
            v[5] = new V(.5F+w,.5F+w,.5F+w,8+tw,16-tw);
            v[6] = new V(.5F+w,1,.5F+w,8+tw,8);
            v[7] = new V(.5F-w,1,.5F+w,8-tw,8);
        }

        for (int r = 1; r < 4; r++) {
            for (int i = 0; i < 4; i++) {
                V q = rotateY(v[i + 4], r);
                if (r >= 2) q = reflectUvX(q, 8, 16);
                v[i + r * 4 + 4] = q;
            }
        }

        for (int i = 0; i < 4; i++) {
            v[i] = translateUv(v[i], 12, 12);
        }
        return v;
    }

    private static V[] generateFlat(
            int side,
            int tw,
            float w,
            int connMap
    ) {
        V[] v = faceVerts(side, .5F - w, tw, w);

        int fConnMask = 0;
        for (int i = 0; i < 4; i++) {
            int absoluteSide = ((side & 6) + i + 2) % 6;
            if ((connMap & 1 << absoluteSide) != 0) {
                fConnMask |= 1 << i;
            }
        }

        int rot;
        if ((fConnMask & 0xC) == 0) {
            rot = 0;
        } else if ((fConnMask & 3) == 0) {
            rot = 1;
        } else {
            rot = 2;
        }

        if (rot == 1) {
            for (int i = 0; i < v.length; i++) {
                v[i] = rotateUv(v[i], 1, 8, 16);
            }
        } else if (rot == 2) {
            for (int i = 0; i < v.length; i++) {
                v[i] = translateUv(
                        rotateUv(v[i], 1, 8, 16),
                        16,
                        0
                );
            }
        }

        return v;
    }

    private static V[] faceVerts(int side, float d, int tw, float w) {
        V[] v = {
                new V(.5F-w,d,.5F-w,8-tw,16+tw),
                new V(.5F+w,d,.5F-w,8+tw,16+tw),
                new V(.5F+w,d,.5F+w,8+tw,16-tw),
                new V(.5F-w,d,.5F+w,8-tw,16-tw)
        };

        if ((side & 1) != 0) {
            for (int i = 0; i < v.length; i++) {
                V q = v[i];
                v[i] = new V(q.x, 1.0F - q.y, q.z, q.u, q.v);
            }
            V t = v[1];
            v[1] = v[3];
            v[3] = t;
        }
        return v;
    }

    private static Quad[][] generateFrameModels() {
        Quad[][] models = new Quad[7][];
        final float w = 2.0F / 8.0F;
        final float d = 1.0F / 16.0F - .002F;

        // Center frame: legacy model creates one eight-vertex strip for side
        // 0 then generates sided copies for all six block faces.
        V[] base = new V[8];
        base[0] = new V(.5F-w,.5F-w,.5F-w,20,8);
        base[1] = new V(.5F+w,.5F-w,.5F-w,28,8);
        base[2] = new V(.5F+w,.5F-w,.5F+w,28,0);
        base[3] = new V(.5F-w,.5F-w,.5F+w,20,0);
        base[4] = new V(.5F-w,.5F-w+d,.5F+w,20,8);
        base[5] = new V(.5F+w,.5F-w+d,.5F+w,28,8);
        base[6] = new V(.5F+w,.5F-w+d,.5F-w,28,0);
        base[7] = new V(.5F-w,.5F-w+d,.5F-w,20,0);

        List<V> center = new ArrayList<>(48);
        for (int side = 0; side < 6; side++) {
            for (V q : base) {
                center.add(sideRotation(q, side));
            }
        }
        models[6] = quads(center);

        // Side frame for DOWN, then exact CCL sideRotations copies.
        V[] down = new V[36];
        down[0] = new V(.5F-w,0,.5F+w,16,0);
        down[1] = new V(.5F+w,0,.5F+w,16,8);
        down[2] = new V(.5F+w,.5F-w,.5F+w,20,8);
        down[3] = new V(.5F-w,.5F-w,.5F+w,20,0);
        down[4] = new V(.5F+w,0,.5F+w-d,16,0);
        down[5] = new V(.5F-w,0,.5F+w-d,16,8);
        down[6] = new V(.5F-w,.5F-w,.5F+w-d,20,8);
        down[7] = new V(.5F+w,.5F-w,.5F+w-d,20,0);

        for (int r = 1; r < 4; r++) {
            for (int i = 0; i < 8; i++) {
                down[i + r * 8] = rotateAroundCenterY(down[i], r);
            }
        }
        down[32] = new V(.5F-w,0,.5F-w,24,32);
        down[33] = new V(.5F+w,0,.5F-w,32,32);
        down[34] = new V(.5F+w,0,.5F+w,32,24);
        down[35] = new V(.5F-w,0,.5F+w,24,24);

        for (int side = 0; side < 6; side++) {
            List<V> verts = new ArrayList<>(36);
            for (int i = 0; i < down.length; i++) {
                V q = sideRotation(down[i], side);
                if ((side & 1) != 0 && i < 32) {
                    q = rotateUv(q, 2, 24, 4);
                }
                verts.add(q);
            }
            models[side] = quads(verts);
        }

        return models;
    }

    private static Quad[] quads(List<V> vertices) {
        List<Quad> out = new ArrayList<>(vertices.size() / 4);
        for (int i = 0; i + 3 < vertices.size(); i += 4) {
            out.add(new Quad(
                    normalizeUv(vertices.get(i)),
                    normalizeUv(vertices.get(i + 1)),
                    normalizeUv(vertices.get(i + 2)),
                    normalizeUv(vertices.get(i + 3))
            ));
        }
        return out.toArray(Quad[]::new);
    }

    private static void emitModel(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Quad[] model,
            int rgb
    ) {
        int cr = rgb >> 16 & 0xFF;
        int cg = rgb >> 8 & 0xFF;
        int cb = rgb & 0xFF;

        for (Quad q : model) {
            float abx = q.b.x - q.a.x;
            float aby = q.b.y - q.a.y;
            float abz = q.b.z - q.a.z;
            float adx = q.d.x - q.a.x;
            float ady = q.d.y - q.a.y;
            float adz = q.d.z - q.a.z;

            float nx = aby * adz - abz * ady;
            float ny = abz * adx - abx * adz;
            float nz = abx * ady - aby * adx;
            float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len > 1.0e-7F) {
                nx /= len;
                ny /= len;
                nz /= len;
            }

            vertex(consumer,pose,light,q.a,nx,ny,nz,cr,cg,cb);
            vertex(consumer,pose,light,q.b,nx,ny,nz,cr,cg,cb);
            vertex(consumer,pose,light,q.c,nx,ny,nz,cr,cg,cb);
            vertex(consumer,pose,light,q.d,nx,ny,nz,cr,cg,cb);

            vertex(consumer,pose,light,q.d,-nx,-ny,-nz,cr,cg,cb);
            vertex(consumer,pose,light,q.c,-nx,-ny,-nz,cr,cg,cb);
            vertex(consumer,pose,light,q.b,-nx,-ny,-nz,cr,cg,cb);
            vertex(consumer,pose,light,q.a,-nx,-ny,-nz,cr,cg,cb);
        }
    }

    private static void vertex(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            V v,
            float nx,
            float ny,
            float nz,
            int r,
            int g,
            int b
    ) {
        c.addVertex(pose,v.x,v.y,v.z)
                .setColor(r,g,b,255)
                .setUv(v.u,v.v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose,nx,ny,nz);
    }

    private static V axisCycle(V q, int cycle) {
        return switch (cycle) {
            case 1 -> new V(q.z,q.x,q.y,q.u,q.v);
            case 2 -> new V(q.y,q.z,q.x,q.u,q.v);
            default -> q;
        };
    }

    private static V sideRotation(V q, int side) {
        float x=q.x-.5F;
        float y=q.y-.5F;
        float z=q.z-.5F;
        float nx;
        float ny;
        float nz;

        switch (side) {
            case 1 -> { nx=x; ny=-y; nz=-z; }
            case 2 -> { nx=x; ny=-z; nz=y; }
            case 3 -> { nx=x; ny=z; nz=-y; }
            case 4 -> { nx=y; ny=-x; nz=z; }
            case 5 -> { nx=-y; ny=x; nz=z; }
            default -> { nx=x; ny=y; nz=z; }
        }

        return new V(nx+.5F,ny+.5F,nz+.5F,q.u,q.v);
    }

    private static V rotateAroundCenterY(V q, int r) {
        float x=q.x-.5F;
        float z=q.z-.5F;
        return switch (r & 3) {
            case 1 -> new V(.5F-z,q.y,.5F+x,q.u,q.v);
            case 2 -> new V(.5F-x,q.y,.5F-z,q.u,q.v);
            case 3 -> new V(.5F+z,q.y,.5F-x,q.u,q.v);
            default -> q;
        };
    }

    private static V rotateY(V q, int r) {
        return rotateAroundCenterY(q,r);
    }

    private static V rotateUv(V q, int r, float centerU, float centerV) {
        float u=q.u-centerU;
        float v=q.v-centerV;
        return switch (r & 3) {
            case 1 -> new V(q.x,q.y,q.z,centerU-v,centerV+u);
            case 2 -> new V(q.x,q.y,q.z,centerU-u,centerV-v);
            case 3 -> new V(q.x,q.y,q.z,centerU+v,centerV-u);
            default -> q;
        };
    }

    private static V reflectUvX(V q, float centerU, float centerV) {
        return new V(q.x,q.y,q.z,2*centerU-q.u,q.v);
    }

    private static V translateUv(V q, float du, float dv) {
        return new V(q.x,q.y,q.z,q.u+du,q.v+dv);
    }

    private static V normalizeUv(V q) {
        return new V(q.x,q.y,q.z,q.u/32.0F,q.v/32.0F);
    }

    private static void add(List<V> target, V[] source) {
        for (V v : source) target.add(v);
    }

    private ProjectRedFramedWireModel() {}
}
