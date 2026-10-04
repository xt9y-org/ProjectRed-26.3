package dev.xt9y.projectred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

final class RenderGeometry {
    static void box(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            float x0, float y0, float z0,
            float x1, float y1, float z1
    ) {
        face(c, pose, light, 0, 1, 0,
                x0,y1,z0,0,0, x0,y1,z1,0,1, x1,y1,z1,1,1, x1,y1,z0,1,0);
        face(c, pose, light, 0,-1, 0,
                x0,y0,z0,0,0, x1,y0,z0,1,0, x1,y0,z1,1,1, x0,y0,z1,0,1);
        face(c, pose, light, 0, 0,-1,
                x0,y0,z0,0,1, x0,y1,z0,0,0, x1,y1,z0,1,0, x1,y0,z0,1,1);
        face(c, pose, light, 0, 0, 1,
                x1,y0,z1,0,1, x1,y1,z1,0,0, x0,y1,z1,1,0, x0,y0,z1,1,1);
        face(c, pose, light,-1, 0, 0,
                x0,y0,z1,0,1, x0,y1,z1,0,0, x0,y1,z0,1,0, x0,y0,z0,1,1);
        face(c, pose, light, 1, 0, 0,
                x1,y0,z0,0,1, x1,y1,z0,0,0, x1,y1,z1,1,0, x1,y0,z1,1,1);
    }

    static void facePart(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            float width,
            float depth
    ) {
        float lo = 0.5F - width * 0.5F;
        float hi = 0.5F + width * 0.5F;
        switch (attachment) {
            case DOWN -> box(c, pose, light, lo,0,lo, hi,depth,hi);
            case UP -> box(c, pose, light, lo,1-depth,lo, hi,1,hi);
            case NORTH -> box(c, pose, light, lo,lo,0, hi,hi,depth);
            case SOUTH -> box(c, pose, light, lo,lo,1-depth, hi,hi,1);
            case WEST -> box(c, pose, light, 0,lo,lo, depth,hi,hi);
            case EAST -> box(c, pose, light, 1-depth,lo,lo, 1,hi,hi);
        }
    }

    static void gateBoard(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment
    ) {
        switch (attachment) {
            case DOWN -> box(c, pose, light, .0625F,0,.0625F, .9375F,.125F,.9375F);
            case UP -> box(c, pose, light, .0625F,.875F,.0625F, .9375F,1,.9375F);
            case NORTH -> box(c, pose, light, .0625F,.0625F,0, .9375F,.9375F,.125F);
            case SOUTH -> box(c, pose, light, .0625F,.0625F,.875F, .9375F,.9375F,1);
            case WEST -> box(c, pose, light, 0,.0625F,.0625F, .125F,.9375F,.9375F);
            case EAST -> box(c, pose, light, .875F,.0625F,.0625F, 1,.9375F,.9375F);
        }
    }

    static void gateSurface(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation
    ) {
        // Slightly raised to prevent z-fighting with the base.
        float lo = .0625F;
        float hi = .9375F;
        float d = .126F;

        switch (attachment) {
            case DOWN -> orientedQuad(c, pose, light, attachment, rotation,
                    lo,d,lo, hi,d,lo, hi,d,hi, lo,d,hi);
            case UP -> orientedQuad(c, pose, light, attachment, rotation,
                    lo,1-d,hi, hi,1-d,hi, hi,1-d,lo, lo,1-d,lo);
            case NORTH -> orientedQuad(c, pose, light, attachment, rotation,
                    lo,hi,d, hi,hi,d, hi,lo,d, lo,lo,d);
            case SOUTH -> orientedQuad(c, pose, light, attachment, rotation,
                    hi,hi,1-d, lo,hi,1-d, lo,lo,1-d, hi,lo,1-d);
            case WEST -> orientedQuad(c, pose, light, attachment, rotation,
                    d,hi,hi, d,hi,lo, d,lo,lo, d,lo,hi);
            case EAST -> orientedQuad(c, pose, light, attachment, rotation,
                    1-d,hi,lo, 1-d,hi,hi, 1-d,lo,hi, 1-d,lo,lo);
        }
    }

    static void gateIndicator(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment
    ) {
        facePart(c, pose, light, attachment, .16F, .14F);
    }

    static void wireFace(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            float width,
            float depth,
            int connections
    ) {
        facePart(c, pose, light, attachment, width, depth);

        float half = width * .5F;
        float lo = .5F - half;
        float hi = .5F + half;

        for (Direction direction : Direction.values()) {
            if ((connections & (1 << direction.ordinal())) == 0) continue;
            if (direction.getAxis() == attachment.getAxis()) continue;

            switch (attachment) {
                case DOWN -> {
                    if (direction == Direction.NORTH) box(c,pose,light,lo,0,0,hi,depth,.5F);
                    if (direction == Direction.SOUTH) box(c,pose,light,lo,0,.5F,hi,depth,1);
                    if (direction == Direction.WEST) box(c,pose,light,0,0,lo,.5F,depth,hi);
                    if (direction == Direction.EAST) box(c,pose,light,.5F,0,lo,1,depth,hi);
                }
                case UP -> {
                    if (direction == Direction.NORTH) box(c,pose,light,lo,1-depth,0,hi,1,.5F);
                    if (direction == Direction.SOUTH) box(c,pose,light,lo,1-depth,.5F,hi,1,1);
                    if (direction == Direction.WEST) box(c,pose,light,0,1-depth,lo,.5F,1,hi);
                    if (direction == Direction.EAST) box(c,pose,light,.5F,1-depth,lo,1,1,hi);
                }
                case NORTH -> {
                    if (direction == Direction.UP) box(c,pose,light,lo,.5F,0,hi,1,depth);
                    if (direction == Direction.DOWN) box(c,pose,light,lo,0,0,hi,.5F,depth);
                    if (direction == Direction.WEST) box(c,pose,light,0,lo,0,.5F,hi,depth);
                    if (direction == Direction.EAST) box(c,pose,light,.5F,lo,0,1,hi,depth);
                }
                case SOUTH -> {
                    if (direction == Direction.UP) box(c,pose,light,lo,.5F,1-depth,hi,1,1);
                    if (direction == Direction.DOWN) box(c,pose,light,lo,0,1-depth,hi,.5F,1);
                    if (direction == Direction.WEST) box(c,pose,light,0,lo,1-depth,.5F,hi,1);
                    if (direction == Direction.EAST) box(c,pose,light,.5F,lo,1-depth,1,hi,1);
                }
                case WEST -> {
                    if (direction == Direction.UP) box(c,pose,light,0,.5F,lo,depth,1,hi);
                    if (direction == Direction.DOWN) box(c,pose,light,0,0,lo,depth,.5F,hi);
                    if (direction == Direction.NORTH) box(c,pose,light,0,lo,0,depth,hi,.5F);
                    if (direction == Direction.SOUTH) box(c,pose,light,0,lo,.5F,depth,hi,1);
                }
                case EAST -> {
                    if (direction == Direction.UP) box(c,pose,light,1-depth,.5F,lo,1,1,hi);
                    if (direction == Direction.DOWN) box(c,pose,light,1-depth,0,lo,1,.5F,hi);
                    if (direction == Direction.NORTH) box(c,pose,light,1-depth,lo,0,1,hi,.5F);
                    if (direction == Direction.SOUTH) box(c,pose,light,1-depth,lo,.5F,1,hi,1);
                }
            }
        }
    }

    static void framedWire(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            int connections
    ) {
        box(c, pose, light, .375F,.375F,.375F, .625F,.625F,.625F);
        for (Direction direction : Direction.values()) {
            if ((connections & (1 << direction.ordinal())) == 0) continue;
            switch (direction) {
                case DOWN -> box(c,pose,light,.4375F,0,.4375F,.5625F,.375F,.5625F);
                case UP -> box(c,pose,light,.4375F,.625F,.4375F,.5625F,1,.5625F);
                case NORTH -> box(c,pose,light,.4375F,.4375F,0,.5625F,.5625F,.375F);
                case SOUTH -> box(c,pose,light,.4375F,.4375F,.625F,.5625F,.5625F,1);
                case WEST -> box(c,pose,light,0,.4375F,.4375F,.375F,.5625F,.5625F);
                case EAST -> box(c,pose,light,.625F,.4375F,.4375F,1,.5625F,.5625F);
            }
        }
    }

    static void framedWireOverlay(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            int connections
    ) {
        float e = .0015F;
        box(
                c,
                pose,
                light,
                .375F-e,.375F-e,.375F-e,
                .625F+e,.625F+e,.625F+e
        );
        for (Direction direction : Direction.values()) {
            if ((connections & (1 << direction.ordinal())) == 0) continue;
            switch (direction) {
                case DOWN -> box(c,pose,light,.4375F-e,0,.4375F-e,.5625F+e,.375F,.5625F+e);
                case UP -> box(c,pose,light,.4375F-e,.625F,.4375F-e,.5625F+e,1,.5625F+e);
                case NORTH -> box(c,pose,light,.4375F-e,.4375F-e,0,.5625F+e,.5625F+e,.375F);
                case SOUTH -> box(c,pose,light,.4375F-e,.4375F-e,.625F,.5625F+e,.5625F+e,1);
                case WEST -> box(c,pose,light,0,.4375F-e,.4375F-e,.375F,.5625F+e,.5625F+e);
                case EAST -> box(c,pose,light,.625F,.4375F-e,.4375F-e,1,.5625F+e,.5625F+e);
            }
        }
    }

    static void panelButtons(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            int mask
    ) {
        for (int bit = 0; bit < 16; bit++) {
            if ((mask & (1 << bit)) == 0) continue;

            int row = bit / 4;
            int col = bit % 4;
            float u0 = .17F + col * .165F;
            float v0 = .17F + row * .165F;
            float u1 = u0 + .12F;
            float v1 = v0 + .12F;

            surfaceRect(
                    c, pose, light,
                    attachment, rotation,
                    u0, v0, u1, v1,
                    .142F
            );
        }
    }

    static void segmentDisplay(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            int shape,
            int mask
    ) {
        if (shape == 0) {
            drawSevenSegment(
                    c, pose, light,
                    attachment, rotation,
                    .18F, .18F, .46F, .82F,
                    mask & 0xFF
            );
            drawSevenSegment(
                    c, pose, light,
                    attachment, rotation,
                    .54F, .18F, .82F, .82F,
                    (mask >>> 8) & 0xFF
            );
            return;
        }

        // ProjectRed's 16-segment mode maps the 16 bundled channels
        // directly to the 16 display elements. This compact grid keeps that
        // one-bit-per-element behavior visible without CBMultipart's model
        // dependency.
        for (int bit = 0; bit < 16; bit++) {
            if ((mask & (1 << bit)) == 0) continue;
            int row = bit / 4;
            int col = bit % 4;
            float u0 = .19F + col * .155F;
            float v0 = .19F + row * .155F;
            surfaceRect(
                    c, pose, light,
                    attachment, rotation,
                    u0, v0, u0 + .11F, v0 + .11F,
                    .145F
            );
        }
    }

    private static void drawSevenSegment(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float u0,
            float v0,
            float u1,
            float v1,
            int bits
    ) {
        float w = u1 - u0;
        float h = v1 - v0;
        float t = Math.min(w, h) * .13F;
        float mid = (v0 + v1) * .5F;

        // 0 top, 1 upper-right, 2 lower-right, 3 bottom,
        // 4 lower-left, 5 upper-left, 6 middle, 7 decimal point.
        if ((bits & 0x01) != 0) surfaceRect(c,pose,light,attachment,rotation,u0+t,v0,u1-t,v0+t,.145F);
        if ((bits & 0x02) != 0) surfaceRect(c,pose,light,attachment,rotation,u1-t,v0+t,u1,mid-t*.5F,.145F);
        if ((bits & 0x04) != 0) surfaceRect(c,pose,light,attachment,rotation,u1-t,mid+t*.5F,u1,v1-t,.145F);
        if ((bits & 0x08) != 0) surfaceRect(c,pose,light,attachment,rotation,u0+t,v1-t,u1-t,v1,.145F);
        if ((bits & 0x10) != 0) surfaceRect(c,pose,light,attachment,rotation,u0,mid+t*.5F,u0+t,v1-t,.145F);
        if ((bits & 0x20) != 0) surfaceRect(c,pose,light,attachment,rotation,u0,v0+t,u0+t,mid-t*.5F,.145F);
        if ((bits & 0x40) != 0) surfaceRect(c,pose,light,attachment,rotation,u0+t,mid-t*.5F,u1-t,mid+t*.5F,.145F);
        if ((bits & 0x80) != 0) surfaceRect(c,pose,light,attachment,rotation,u1-t*1.2F,v1-t*1.2F,u1,v1,.145F);
    }

    private static void surfaceRect(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float u0,
            float v0,
            float u1,
            float v1,
            float depth
    ) {
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

        float cx = .5F + attachment.getStepX() * (.5F - depth);
        float cy = .5F + attachment.getStepY() * (.5F - depth);
        float cz = .5F + attachment.getStepZ() * (.5F - depth);

        float[] a = point(cx,cy,cz,right,down,u0,v0);
        float[] b = point(cx,cy,cz,right,down,u1,v0);
        float[] d = point(cx,cy,cz,right,down,u0,v1);
        float[] e = point(cx,cy,cz,right,down,u1,v1);

        orientedQuad(
                c, pose, light,
                attachment.getOpposite(),
                0,
                a[0],a[1],a[2],
                b[0],b[1],b[2],
                e[0],e[1],e[2],
                d[0],d[1],d[2]
        );
    }

    private static float[] point(
            float cx,
            float cy,
            float cz,
            Direction right,
            Direction down,
            float u,
            float v
    ) {
        float du = u - .5F;
        float dv = v - .5F;
        return new float[] {
                cx + right.getStepX() * du + down.getStepX() * dv,
                cy + right.getStepY() * du + down.getStepY() * dv,
                cz + right.getStepZ() * du + down.getStepZ() * dv
        };
    }

    private static void orientedQuad(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction normal,
            int rotation,
            float ax,float ay,float az,
            float bx,float by,float bz,
            float cx,float cy,float cz,
            float dx,float dy,float dz
    ) {
        float[][] uv = {
                {0,0},{1,0},{1,1},{0,1}
        };
        int r = Math.floorMod(rotation, 4);

        float nx = normal.getStepX();
        float ny = normal.getStepY();
        float nz = normal.getStepZ();

        float[][] p = {
                {ax,ay,az},{bx,by,bz},{cx,cy,cz},{dx,dy,dz}
        };

        for (int i = 0; i < 4; i++) {
            float[] t = uv[(i + r) & 3];
            float[] v = p[i];
            vertex(c,pose,light,v[0],v[1],v[2],t[0],t[1],nx,ny,nz);
        }
        for (int i = 3; i >= 0; i--) {
            float[] t = uv[(i + r) & 3];
            float[] v = p[i];
            vertex(c,pose,light,v[0],v[1],v[2],t[0],t[1],-nx,-ny,-nz);
        }
    }

    private static void face(
            VertexConsumer c, PoseStack.Pose pose, int light,
            float nx,float ny,float nz,
            float ax,float ay,float az,float au,float av,
            float bx,float by,float bz,float bu,float bv,
            float cx,float cy,float cz,float cu,float cv,
            float dx,float dy,float dz,float du,float dv
    ) {
        vertex(c,pose,light,ax,ay,az,au,av,nx,ny,nz);
        vertex(c,pose,light,bx,by,bz,bu,bv,nx,ny,nz);
        vertex(c,pose,light,cx,cy,cz,cu,cv,nx,ny,nz);
        vertex(c,pose,light,dx,dy,dz,du,dv,nx,ny,nz);
        vertex(c,pose,light,dx,dy,dz,du,dv,-nx,-ny,-nz);
        vertex(c,pose,light,cx,cy,cz,cu,cv,-nx,-ny,-nz);
        vertex(c,pose,light,bx,by,bz,bu,bv,-nx,-ny,-nz);
        vertex(c,pose,light,ax,ay,az,au,av,-nx,-ny,-nz);
    }

    private static void vertex(
            VertexConsumer c, PoseStack.Pose pose, int light,
            float x,float y,float z,float u,float v,
            float nx,float ny,float nz
    ) {
        c.addVertex(pose,x,y,z)
                .setColor(255,255,255,255)
                .setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose,nx,ny,nz);
    }

    private RenderGeometry() {}
}
