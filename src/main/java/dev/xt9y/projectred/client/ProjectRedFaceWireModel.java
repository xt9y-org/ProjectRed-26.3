package dev.xt9y.projectred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/**
 * Direct port of the legacy ProjectRed WireModelGen face-wire geometry.
 *
 * The model is generated once per side/thickness/connection combination and
 * then reused. UVs target the original 32x32 transmission texture atlases.
 */
final class ProjectRedFaceWireModel {
    private static final int[] REORIENT_SIDE = {0, 3, 3, 0, 0, 3};
    private static final Map<Integer, Quad[]> CACHE = new ConcurrentHashMap<>();

    private record V(float x, float y, float z, float u, float v) {}
    private record Quad(V a, V b, V c, V d) {}

    static void render(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int thickness,
            int connectionData,
            int rgb
    ) {
        int connectedWorld = connectionData & 0x3F;
        int cornerWorld = connectionData >>> 6 & 0x3F;
        int internalWorld = connectionData >>> 12 & 0x3F;

        int corner = 0;
        int straight = 0;
        int internal = 0;

        // WireModelGen side-0 directions are:
        // r0=SOUTH, r1=WEST, r2=NORTH, r3=EAST.
        for (int r = 0; r < 4; r++) {
            Direction direction = MultipartBlockEntity.localToWorld(
                    attachment,
                    0,
                    (r + 2) & 3
            );
            int worldBit = 1 << direction.ordinal();
            int localBit = 1 << r;

            if ((internalWorld & worldBit) != 0) {
                internal |= localBit;
            } else if ((cornerWorld & worldBit) != 0) {
                corner |= localBit;
            } else if ((connectedWorld & worldBit) != 0) {
                straight |= localBit;
            }
        }

        int side = attachment.ordinal();
        int finalCorner = corner;
        int finalStraight = straight;
        int finalInternal = internal;
        int key = side
                | (thickness << 3)
                | (finalCorner << 5)
                | (finalStraight << 9)
                | (finalInternal << 13);

        Quad[] model = CACHE.computeIfAbsent(
                key,
                ignored -> generate(
                        side,
                        thickness,
                        finalCorner,
                        finalStraight,
                        finalInternal
                )
        );

        Direction xAxis = MultipartBlockEntity.localToWorld(
                attachment,
                0,
                1
        );
        Direction zAxis = MultipartBlockEntity.localToWorld(
                attachment,
                0,
                2
        );
        Direction yAxis = attachment.getOpposite();

        float ox = .5F + attachment.getStepX() * .5F;
        float oy = .5F + attachment.getStepY() * .5F;
        float oz = .5F + attachment.getStepZ() * .5F;

        int cr = rgb >> 16 & 0xFF;
        int cg = rgb >> 8 & 0xFF;
        int cb = rgb & 0xFF;

        for (Quad quad : model) {
            float[] a = point(quad.a, xAxis, yAxis, zAxis, ox, oy, oz);
            float[] b = point(quad.b, xAxis, yAxis, zAxis, ox, oy, oz);
            float[] c = point(quad.c, xAxis, yAxis, zAxis, ox, oy, oz);
            float[] d = point(quad.d, xAxis, yAxis, zAxis, ox, oy, oz);

            float abx = b[0] - a[0];
            float aby = b[1] - a[1];
            float abz = b[2] - a[2];
            float acx = c[0] - a[0];
            float acy = c[1] - a[1];
            float acz = c[2] - a[2];

            float nx = aby * acz - abz * acy;
            float ny = abz * acx - abx * acz;
            float nz = abx * acy - aby * acx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0e-7F) {
                nx /= length;
                ny /= length;
                nz /= length;
            }

            vertex(consumer,pose,light,a,quad.a,nx,ny,nz,cr,cg,cb);
            vertex(consumer,pose,light,b,quad.b,nx,ny,nz,cr,cg,cb);
            vertex(consumer,pose,light,c,quad.c,nx,ny,nz,cr,cg,cb);
            vertex(consumer,pose,light,d,quad.d,nx,ny,nz,cr,cg,cb);

            // ProjectRed's generated model has the correct exterior winding,
            // but custom geometry can be viewed from both sides around an
            // open corner. Emit the reverse face too so modern culling does
            // not drop that wrap.
            vertex(consumer,pose,light,d,quad.d,-nx,-ny,-nz,cr,cg,cb);
            vertex(consumer,pose,light,c,quad.c,-nx,-ny,-nz,cr,cg,cb);
            vertex(consumer,pose,light,b,quad.b,-nx,-ny,-nz,cr,cg,cb);
            vertex(consumer,pose,light,a,quad.a,-nx,-ny,-nz,cr,cg,cb);
        }
    }

    private static Quad[] generate(
            int side,
            int thickness,
            int cornerMask,
            int straightMask,
            int internalMask
    ) {
        int tw = thickness + 1;
        int th = tw + 1;
        float w = tw / 16.0F;
        float h = th / 16.0F;

        int mask = cornerMask
                | (straightMask << 4)
                | internalMask
                | (internalMask << 4);
        int connMask = cornerMask | straightMask | internalMask;
        int connCount = Integer.bitCount(connMask);

        List<V> vertices = new ArrayList<>(96);
        add(vertices, generateCenter(side, tw, w, h, connMask, connCount));

        for (int r = 0; r < 4; r++) {
            V[] sideVerts;
            if (connCount == 0) {
                sideVerts = (r & 1) != 0
                        ? generateStub(side, r, tw, th, w, h)
                        : generateFlat(side, r, tw, th, w, h);
            } else if (connCount == 1
                    && connMask == 1 << ((r + 2) & 3)) {
                sideVerts = generateStub(side, r, tw, th, w, h);
            } else {
                int stype = (mask >> r) & 0x11;
                sideVerts = switch (stype) {
                    case 0x01 -> generateCorner(side, r, tw, th, w, h);
                    case 0x10 -> generateStraight(side, r, tw, th, w, h);
                    case 0x11 -> generateInternal(side, r, tw, th, w, h);
                    default -> generateFlat(side, r, tw, th, w, h);
                };
            }

            for (int i = 0; i < sideVerts.length; i++) {
                sideVerts[i] = rotateGeometry(sideVerts[i], r);
            }
            add(vertices, sideVerts);
        }

        List<Quad> quads = new ArrayList<>(vertices.size() / 4);
        for (int i = 0; i + 3 < vertices.size(); i += 4) {
            V a = normalizeUv(vertices.get(i));
            V b = normalizeUv(vertices.get(i + 1));
            V c = normalizeUv(vertices.get(i + 2));
            V d = normalizeUv(vertices.get(i + 3));
            quads.add(new Quad(a,b,c,d));
        }
        return quads.toArray(Quad[]::new);
    }

    private static V[] generateCenter(
            int side,
            int tw,
            float w,
            float h,
            int connMask,
            int connCount
    ) {
        int tex;
        if (connCount == 0) {
            tex = 1;
        } else if (connCount == 1) {
            tex = (connMask & 5) != 0 ? 0 : 1;
        } else if (connMask == 5) {
            tex = 0;
        } else if (connMask == 10) {
            tex = 1;
        } else {
            tex = 2;
        }

        V[] v = {
                new V(.5F-w,h,.5F+w,8-tw,16+tw),
                new V(.5F+w,h,.5F+w,8+tw,16+tw),
                new V(.5F+w,h,.5F-w,8+tw,16-tw),
                new V(.5F-w,h,.5F-w,8-tw,16-tw)
        };

        if (tex == 0 || tex == 1) {
            tex = (tex + REORIENT_SIDE[side]) & 1;
        }

        int r = REORIENT_SIDE[side];
        if (tex == 1) r += 3;
        r &= 3;
        if (r != 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = rotateUv(v[i], r, 8.0F, 16.0F);
            }
        }

        if (tex == 2) {
            for (int i = 0; i < v.length; i++) {
                v[i] = translateUv(v[i], 16.0F, 0.0F);
            }
        }

        return v;
    }

    private static V[] generateStraight(
            int side, int r, int tw, int th, float w, float h
    ) {
        V[] v = generateExtension(8, tw, th, w, h);
        reflectSide(v, side, r);
        return v;
    }

    private static V[] generateStub(
            int side, int r, int tw, int th, float w, float h
    ) {
        V[] v = generateExtension(4, tw, th, w, h);
        for (int i = 0; i < 4; i++) {
            V q = v[i];
            v[i] = new V(q.x,q.y,q.z-.002F,q.u,q.v);
        }
        reflectSide(v, side, r);
        return v;
    }

    private static V[] generateFlat(
            int side, int r, int tw, int th, float w, float h
    ) {
        V[] v = {
                new V(.5F-w,0,.5F+w,16,16+tw),
                new V(.5F+w,0,.5F+w,16,16-tw),
                new V(.5F+w,h,.5F+w,16-th,16-tw),
                new V(.5F-w,h,.5F+w,16-th,16+tw)
        };

        Direction world = MultipartBlockEntity.localToWorld(
                Direction.values()[side],
                0,
                (r + 2) & 3
        );
        if ((world.ordinal() & 1) == 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = rotateUv(v[i], 2, 8.0F, 16.0F);
            }
        }
        return v;
    }

    private static V[] generateCorner(
            int side, int r, int tw, int th, float w, float h
    ) {
        V[] base = generateExtension(8 + th, tw, th, w, h);
        V[] v = new V[20];
        System.arraycopy(base, 0, v, 0, base.length);
        for (int i = 0; i < 4; i++) {
            v[i] = translateUv(v[i], 0.0F, -th);
        }

        v[16] = new V(.5F-w,0,1,8-tw,24+2*th);
        v[17] = new V(.5F+w,0,1,8+tw,24+2*th);
        v[18] = new V(.5F+w,0,1+h,8+tw,24+th);
        v[19] = new V(.5F-w,0,1+h,8-tw,24+th);

        reflectSide(v, side, r);
        return v;
    }

    private static V[] generateInternal(
            int side, int r, int tw, int th, float w, float h
    ) {
        V[] v = generateExtension(8, tw, th, w, h);
        v[0] = withUv(v[0],8+tw,24);
        v[1] = withUv(v[1],8-tw,24);
        v[2] = withUv(v[2],8-tw,24+tw);
        v[3] = withUv(v[3],8+tw,24+tw);

        reflectSide(v, side, r);
        for (int i = 4; i < 16; i++) {
            v[i] = translateUv(v[i],16,0);
        }
        return v;
    }

    private static V[] generateExtension(
            int tl, int tw, int th, float w, float h
    ) {
        float l = tl / 16.0F;
        return new V[] {
                new V(.5F-w,0,.5F+l,8-tw,24+2*th),
                new V(.5F+w,0,.5F+l,8+tw,24+2*th),
                new V(.5F+w,h,.5F+l,8+tw,24+th),
                new V(.5F-w,h,.5F+l,8-tw,24+th),

                new V(.5F-w,h,.5F+l,8-tw,16+tl),
                new V(.5F+w,h,.5F+l,8+tw,16+tl),
                new V(.5F+w,h,.5F+w,8+tw,16+tw),
                new V(.5F-w,h,.5F+w,8-tw,16+tw),

                new V(.5F-w,0,.5F+w,0,16+tw),
                new V(.5F-w,0,.5F+l,0,16+tl),
                new V(.5F-w,h,.5F+l,th,16+tl),
                new V(.5F-w,h,.5F+w,th,16+tw),

                new V(.5F+w,0,.5F+l,16,16+tl),
                new V(.5F+w,0,.5F+w,16,16+tw),
                new V(.5F+w,h,.5F+w,16-th,16+tw),
                new V(.5F+w,h,.5F+l,16-th,16+tl)
        };
    }

    private static void reflectSide(V[] v, int side, int r) {
        if (Math.floorMod(r + REORIENT_SIDE[side],4) < 2) return;
        for (int i = 0; i < v.length; i++) {
            v[i] = rotateUv(v[i],2,8.0F,16.0F);
        }
    }

    private static V rotateGeometry(V q, int r) {
        float x=q.x-.5F;
        float z=q.z-.5F;
        return switch (r & 3) {
            case 1 -> new V(.5F-z,q.y,.5F+x,q.u,q.v);
            case 2 -> new V(.5F-x,q.y,.5F-z,q.u,q.v);
            case 3 -> new V(.5F+z,q.y,.5F-x,q.u,q.v);
            default -> q;
        };
    }

    private static V rotateUv(V q, int r, float cx, float cv) {
        float u=q.u-cx;
        float v=q.v-cv;
        return switch (r & 3) {
            case 1 -> new V(q.x,q.y,q.z,cx-v,cv+u);
            case 2 -> new V(q.x,q.y,q.z,cx-u,cv-v);
            case 3 -> new V(q.x,q.y,q.z,cx+v,cv-u);
            default -> q;
        };
    }

    private static V translateUv(V q, float du, float dv) {
        return new V(q.x,q.y,q.z,q.u+du,q.v+dv);
    }

    private static V withUv(V q, float u, float v) {
        return new V(q.x,q.y,q.z,u,v);
    }

    private static V normalizeUv(V q) {
        return new V(q.x,q.y,q.z,q.u/32.0F,q.v/32.0F);
    }

    private static void add(List<V> target, V[] source) {
        for (V v : source) target.add(v);
    }

    private static float[] point(
            V v,
            Direction xAxis,
            Direction yAxis,
            Direction zAxis,
            float ox,
            float oy,
            float oz
    ) {
        float dx=v.x-.5F;
        float dz=v.z-.5F;
        return new float[] {
                ox+xAxis.getStepX()*dx+zAxis.getStepX()*dz+yAxis.getStepX()*v.y,
                oy+xAxis.getStepY()*dx+zAxis.getStepY()*dz+yAxis.getStepY()*v.y,
                oz+xAxis.getStepZ()*dx+zAxis.getStepZ()*dz+yAxis.getStepZ()*v.y
        };
    }

    private static void vertex(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            float[] p,
            V source,
            float nx,
            float ny,
            float nz,
            int r,
            int g,
            int b
    ) {
        c.addVertex(pose,p[0],p[1],p[2])
                .setColor(r,g,b,255)
                .setUv(source.u,source.v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose,nx,ny,nz);
    }

    private ProjectRedFaceWireModel() {}
}
