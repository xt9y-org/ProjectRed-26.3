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
 * Exact procedural port of ProjectRed's legacy RedstoneTorchModel and
 * FlippedRSTorchModel.
 */
final class ProjectRedTorchModel {
    private record Key(int xBits, int zBits, int height, boolean flipped) {}
    private record V(float x, float y, float z, float u, float v) {}
    private record Quad(V a, V b, V c, V d) {}

    private static final Map<Key, Quad[]> CACHE = new ConcurrentHashMap<>();

    static void render(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float x,
            float z,
            int height,
            boolean reflect,
            boolean flipped
    ) {
        Key key = new Key(
                Float.floatToIntBits(x),
                Float.floatToIntBits(z),
                height,
                flipped
        );
        Quad[] model = CACHE.computeIfAbsent(
                key,
                ignored -> generate(x, z, height, flipped)
        );

        Direction right = MultipartBlockEntity.localToWorld(
                attachment,
                rotation,
                1
        );
        Direction down = MultipartBlockEntity.localToWorld(
                attachment,
                rotation,
                2
        );
        Direction normal = attachment.getOpposite();

        float ox = .5F + attachment.getStepX() * .5F;
        float oy = .5F + attachment.getStepY() * .5F;
        float oz = .5F + attachment.getStepZ() * .5F;

        for (Quad q : model) {
            emit(
                    consumer, pose, light, q,
                    right, down, normal,
                    ox, oy, oz,
                    reflect
            );
        }
    }

    private static Quad[] generate(
            float xPixels,
            float zPixels,
            int height,
            boolean flipped
    ) {
        List<V> v = new ArrayList<>(20);

        // The four manually-authored top vertices in RedstoneTorchModel.
        v.add(new V(7/16F,10/16F,9/16F,7/16F,8/16F));
        v.add(new V(9/16F,10/16F,9/16F,9/16F,8/16F));
        v.add(new V(9/16F,10/16F,7/16F,9/16F,6/16F));
        v.add(new V(7/16F,10/16F,7/16F,7/16F,6/16F));

        // generateBlock(..., mask=0x33): only side indices 2 and 3.
        appendMaskedBlock(
                v,
                6/16F,(10-height)/16F,7/16F,
                10/16F,11/16F,9/16F,
                0x33
        );

        // generateBlock(..., mask=0x0f): only side indices 4 and 5.
        appendMaskedBlock(
                v,
                7/16F,(10-height)/16F,6/16F,
                9/16F,11/16F,10/16F,
                0x0F
        );

        float tx = -.5F + xPixels / 16.0F;
        float ty = (height - 10) / 16.0F;
        float tz = -.5F + zPixels / 16.0F;

        for (int i = 0; i < v.size(); i++) {
            V q = v.get(i);
            float px = q.x + tx;
            float py = q.y + ty;
            float pz = q.z + tz;

            if (flipped) {
                // Rotation(pi, 0,0,1).at(center), then Translation(0,-6/16,0).
                px = 1.0F - px;
                py = 1.0F - py - 6.0F / 16.0F;
            }

            // Legacy code applies Scale(1.0005) after all torch-local transforms.
            px *= 1.0005F;
            py *= 1.0005F;
            pz *= 1.0005F;

            v.set(i, new V(px,py,pz,q.u,q.v));
        }

        List<Quad> out = new ArrayList<>(5);
        for (int i = 0; i + 3 < v.size(); i += 4) {
            out.add(new Quad(v.get(i),v.get(i+1),v.get(i+2),v.get(i+3)));
        }
        return out.toArray(Quad[]::new);
    }

    private static void appendMaskedBlock(
            List<V> out,
            float x1,float y1,float z1,
            float x2,float y2,float z2,
            int mask
    ) {
        float u1;
        float v1;
        float u2;
        float v2;

        if ((mask & 1) == 0) {
            u1=x1; v1=z1; u2=x2; v2=z2;
            out.add(new V(x1,y1,z2,u1,v2));
            out.add(new V(x1,y1,z1,u1,v1));
            out.add(new V(x2,y1,z1,u2,v1));
            out.add(new V(x2,y1,z2,u2,v2));
        }
        if ((mask & 2) == 0) {
            u1=x1; v1=z1; u2=x2; v2=z2;
            out.add(new V(x2,y2,z2,u2,v2));
            out.add(new V(x2,y2,z1,u2,v1));
            out.add(new V(x1,y2,z1,u1,v1));
            out.add(new V(x1,y2,z2,u1,v2));
        }
        if ((mask & 4) == 0) {
            u1=1-x1; v1=1-y2; u2=1-x2; v2=1-y1;
            out.add(new V(x1,y1,z1,u1,v2));
            out.add(new V(x1,y2,z1,u1,v1));
            out.add(new V(x2,y2,z1,u2,v1));
            out.add(new V(x2,y1,z1,u2,v2));
        }
        if ((mask & 8) == 0) {
            u1=x1; v1=1-y2; u2=x2; v2=1-y1;
            out.add(new V(x2,y1,z2,u2,v2));
            out.add(new V(x2,y2,z2,u2,v1));
            out.add(new V(x1,y2,z2,u1,v1));
            out.add(new V(x1,y1,z2,u1,v2));
        }
        if ((mask & 0x10) == 0) {
            u1=z1; v1=1-y2; u2=z2; v2=1-y1;
            out.add(new V(x1,y1,z2,u2,v2));
            out.add(new V(x1,y2,z2,u2,v1));
            out.add(new V(x1,y2,z1,u1,v1));
            out.add(new V(x1,y1,z1,u1,v2));
        }
        if ((mask & 0x20) == 0) {
            u1=1-z1; v1=1-y2; u2=1-z2; v2=1-y1;
            out.add(new V(x2,y1,z1,u1,v2));
            out.add(new V(x2,y2,z1,u1,v1));
            out.add(new V(x2,y2,z2,u2,v1));
            out.add(new V(x2,y1,z2,u2,v2));
        }
    }

    private static void emit(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Quad q,
            Direction right,
            Direction down,
            Direction normal,
            float ox,
            float oy,
            float oz,
            boolean reflect
    ) {
        float[] a = point(q.a,right,down,normal,ox,oy,oz,reflect);
        float[] b = point(q.b,right,down,normal,ox,oy,oz,reflect);
        float[] d = point(q.d,right,down,normal,ox,oy,oz,reflect);

        float abx=b[0]-a[0];
        float aby=b[1]-a[1];
        float abz=b[2]-a[2];
        float adx=d[0]-a[0];
        float ady=d[1]-a[1];
        float adz=d[2]-a[2];

        float nx=aby*adz-abz*ady;
        float ny=abz*adx-abx*adz;
        float nz=abx*ady-aby*adx;
        float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(len>1.0e-7F){ nx/=len; ny/=len; nz/=len; }

        vertex(c,pose,light,a,q.a,nx,ny,nz);
        vertex(c,pose,light,b,q.b,nx,ny,nz);
        float[] cc=point(q.c,right,down,normal,ox,oy,oz,reflect);
        vertex(c,pose,light,cc,q.c,nx,ny,nz);
        vertex(c,pose,light,d,q.d,nx,ny,nz);

        vertex(c,pose,light,d,q.d,-nx,-ny,-nz);
        vertex(c,pose,light,cc,q.c,-nx,-ny,-nz);
        vertex(c,pose,light,b,q.b,-nx,-ny,-nz);
        vertex(c,pose,light,a,q.a,-nx,-ny,-nz);
    }

    private static float[] point(
            V v,
            Direction right,
            Direction down,
            Direction normal,
            float ox,
            float oy,
            float oz,
            boolean reflect
    ) {
        float localX = reflect ? 1.0F - v.x : v.x;
        float dx=localX-.5F;
        float dz=v.z-.5F;
        return new float[]{
                ox+right.getStepX()*dx+down.getStepX()*dz+normal.getStepX()*v.y,
                oy+right.getStepY()*dx+down.getStepY()*dz+normal.getStepY()*v.y,
                oz+right.getStepZ()*dx+down.getStepZ()*dz+normal.getStepZ()*v.y
        };
    }

    private static void vertex(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            float[] p,
            V v,
            float nx,
            float ny,
            float nz
    ) {
        c.addVertex(pose,p[0],p[1],p[2])
                .setColor(255,255,255,255)
                .setUv(v.u,v.v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose,nx,ny,nz);
    }

    private ProjectRedTorchModel() {}
}
