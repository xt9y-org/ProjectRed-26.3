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
            case DOWN -> box(c, pose, light, 0,0,0, 1,.125F,1);
            case UP -> box(c, pose, light, 0,.875F,0, 1,1,1);
            case NORTH -> box(c, pose, light, 0,0,0, 1,1,.125F);
            case SOUTH -> box(c, pose, light, 0,0,.875F, 1,1,1);
            case WEST -> box(c, pose, light, 0,0,0, .125F,1,1);
            case EAST -> box(c, pose, light, .875F,0,0, 1,1,1);
        }
    }

    static void gateSurface(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float depth
    ) {
        // Slightly raised to prevent z-fighting with the base.
        float lo = .0625F;
        float hi = .9375F;
        float d = depth;

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

    static void arrayGateBody(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment
    ) {
        switch (attachment) {
            case DOWN -> box(c, pose, light, 0,0,0, 1,.75F,1);
            case UP -> box(c, pose, light, 0,.25F,0, 1,1,1);
            case NORTH -> box(c, pose, light, 0,0,0, 1,1,.75F);
            case SOUTH -> box(c, pose, light, 0,0,.25F, 1,1,1);
            case WEST -> box(c, pose, light, 0,0,0, .75F,1,1);
            case EAST -> box(c, pose, light, .25F,0,0, 1,1,1);
        }
    }

    static void gatePointer(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float surfaceDepth,
            float angle,
            float lateralOffset
    ) {
        Direction forward = MultipartBlockEntity.localToWorld(
                attachment,
                rotation,
                0
        );
        Direction right = MultipartBlockEntity.localToWorld(
                attachment,
                rotation,
                1
        );

        float fx = forward.getStepX();
        float fy = forward.getStepY();
        float fz = forward.getStepZ();
        float rx = right.getStepX();
        float ry = right.getStepY();
        float rz = right.getStepZ();

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float dx = fx * cos + rx * sin;
        float dy = fy * cos + ry * sin;
        float dz = fz * cos + rz * sin;

        float px = -fx * sin + rx * cos;
        float py = -fy * sin + ry * cos;
        float pz = -fz * sin + rz * cos;

        float cx = .5F + rx * lateralOffset;
        float cy = .5F + ry * lateralOffset;
        float cz = .5F + rz * lateralOffset;

        float face = surfaceDepth + .002F;
        switch (attachment) {
            case DOWN -> cy = face;
            case UP -> cy = 1.0F - face;
            case NORTH -> cz = face;
            case SOUTH -> cz = 1.0F - face;
            case WEST -> cx = face;
            case EAST -> cx = 1.0F - face;
        }

        float back = .055F;
        float front = .255F;
        float halfWidth = .026F;

        float ax = cx - dx * back - px * halfWidth;
        float ay = cy - dy * back - py * halfWidth;
        float az = cz - dz * back - pz * halfWidth;

        float bx = cx + dx * front - px * halfWidth;
        float by = cy + dy * front - py * halfWidth;
        float bz = cz + dz * front - pz * halfWidth;

        float cx2 = cx + dx * front + px * halfWidth;
        float cy2 = cy + dy * front + py * halfWidth;
        float cz2 = cz + dz * front + pz * halfWidth;

        float dx2 = cx - dx * back + px * halfWidth;
        float dy2 = cy - dy * back + py * halfWidth;
        float dz2 = cz - dz * back + pz * halfWidth;

        float nx = -attachment.getStepX();
        float ny = -attachment.getStepY();
        float nz = -attachment.getStepZ();

        face(
                c,
                pose,
                light,
                nx,
                ny,
                nz,
                ax, ay, az, 0, 1,
                bx, by, bz, 0, 0,
                cx2, cy2, cz2, 1, 0,
                dx2, dy2, dz2, 1, 1
        );
    }

    static void gatePortIndicator(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            int local,
            float surfaceDepth
    ) {
        Direction direction = MultipartBlockEntity.localToWorld(
                attachment,
                rotation,
                local
        );
        Direction perpendicular = MultipartBlockEntity.localToWorld(
                attachment,
                rotation,
                (local + 1) & 3
        );

        float dx = direction.getStepX();
        float dy = direction.getStepY();
        float dz = direction.getStepZ();
        float px = perpendicular.getStepX();
        float py = perpendicular.getStepY();
        float pz = perpendicular.getStepZ();

        float cx = .5F + dx * .34F;
        float cy = .5F + dy * .34F;
        float cz = .5F + dz * .34F;

        float face = surfaceDepth + .003F;
        switch (attachment) {
            case DOWN -> cy = face;
            case UP -> cy = 1.0F - face;
            case NORTH -> cz = face;
            case SOUTH -> cz = 1.0F - face;
            case WEST -> cx = face;
            case EAST -> cx = 1.0F - face;
        }

        float along = .105F;
        float across = .045F;

        float ax = cx - dx * along - px * across;
        float ay = cy - dy * along - py * across;
        float az = cz - dz * along - pz * across;
        float bx = cx + dx * along - px * across;
        float by = cy + dy * along - py * across;
        float bz = cz + dz * along - pz * across;
        float cx2 = cx + dx * along + px * across;
        float cy2 = cy + dy * along + py * across;
        float cz2 = cz + dz * along + pz * across;
        float dx2 = cx - dx * along + px * across;
        float dy2 = cy - dy * along + py * across;
        float dz2 = cz - dz * along + pz * across;

        face(
                c,
                pose,
                light,
                -attachment.getStepX(),
                -attachment.getStepY(),
                -attachment.getStepZ(),
                ax, ay, az, 0, 1,
                bx, by, bz, 1, 1,
                cx2, cy2, cz2, 1, 0,
                dx2, dy2, dz2, 0, 0
        );
    }

    static void gateIndicator(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment
    ) {
        facePart(c, pose, light, attachment, .16F, .14F);
    }

    static void gateWireMask(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            String family,
            int count,
            int selectedMask,
            boolean border,
            boolean reflect,
            float surfaceDepth
    ) {
        float depth = surfaceDepth + (border ? .0010F : .0025F);

        for (int wire = 0; wire < count; wire++) {
            if ((selectedMask & (1 << wire)) == 0) continue;

            int maskIndex = GateWireMasks.maskIndex(family + "-" + wire);
            if (maskIndex < 0) continue;

            for (int i = GateWireMasks.start(maskIndex);
                    i < GateWireMasks.end(maskIndex);
                    i++) {
                int packed = GateWireMasks.packed(i);
                int x = GateWireMasks.x(packed);
                int y = GateWireMasks.y(packed);
                int w = GateWireMasks.width(packed);
                int h = GateWireMasks.height(packed);

                if (reflect) x = 32 - x - w;

                if (border) {
                    int x0 = Math.max(0, x - 2);
                    int y0 = Math.max(0, y - 2);
                    int x1 = Math.min(32, x + w + 2);
                    int y1 = Math.min(32, y + h + 2);
                    x = x0;
                    y = y0;
                    w = x1 - x0;
                    h = y1 - y0;
                }

                surfaceRect(
                        c,
                        pose,
                        light,
                        attachment,
                        rotation,
                        x / 32.0F,
                        y / 32.0F,
                        (x + w) / 32.0F,
                        (y + h) / 32.0F,
                        depth
                );
            }
        }
    }

    static void gateComponentBox(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float centerX,
            float centerZ,
            float width,
            float depth,
            float surfaceDepth,
            float height,
            boolean reflect
    ) {
        if (reflect) centerX = 1.0F - centerX;

        float x0 = centerX - width * .5F;
        float x1 = centerX + width * .5F;
        float z0 = centerZ - depth * .5F;
        float z1 = centerZ + depth * .5F;
        float y0 = surfaceDepth;
        float y1 = surfaceDepth + height;

        float[] p000 = gatePoint(attachment, rotation, x0, y0, z0);
        float[] p100 = gatePoint(attachment, rotation, x1, y0, z0);
        float[] p110 = gatePoint(attachment, rotation, x1, y0, z1);
        float[] p010 = gatePoint(attachment, rotation, x0, y0, z1);
        float[] p001 = gatePoint(attachment, rotation, x0, y1, z0);
        float[] p101 = gatePoint(attachment, rotation, x1, y1, z0);
        float[] p111 = gatePoint(attachment, rotation, x1, y1, z1);
        float[] p011 = gatePoint(attachment, rotation, x0, y1, z1);

        Direction right = MultipartBlockEntity.localToWorld(attachment, rotation, 1);
        Direction down = MultipartBlockEntity.localToWorld(attachment, rotation, 2);
        Direction normal = attachment.getOpposite();

        gateFace(c,pose,light, normal, p001,p011,p111,p101);
        gateFace(c,pose,light, normal.getOpposite(), p000,p100,p110,p010);
        gateFace(c,pose,light, right, p100,p101,p111,p110);
        gateFace(c,pose,light, right.getOpposite(), p000,p010,p011,p001);
        gateFace(c,pose,light, down, p010,p110,p111,p011);
        gateFace(c,pose,light, down.getOpposite(), p000,p001,p101,p100);
    }

    static void gateTorch(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float x,
            float z,
            int heightPixels,
            boolean reflect
    ) {
        float height = Math.max(.125F, (heightPixels - 1) / 16.0F);
        float cx = x / 16.0F;
        float cz = z / 16.0F;

        // ProjectRed's torch model is a crossed stem rather than a square
        // post. Two thin prisms reproduce the original silhouette while
        // retaining the original torch texture.
        gateComponentBox(
                c, pose, light, attachment, rotation,
                cx, cz,
                .25F, .125F,
                .125F, height,
                reflect
        );
        gateComponentBox(
                c, pose, light, attachment, rotation,
                cx, cz,
                .125F, .25F,
                .125F, height,
                reflect
        );
    }

    static void gateChip(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float x,
            float z,
            boolean reflect
    ) {
        gateComponentBox(
                c, pose, light, attachment, rotation,
                x / 16.0F,
                z / 16.0F,
                .21875F,
                .21875F,
                .125F,
                .09125F,
                reflect
        );
    }

    static void gateLever(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float x,
            float z,
            boolean on,
            boolean reflect
    ) {
        float cx = x / 16.0F;
        float cz = z / 16.0F;
        gateComponentBox(
                c, pose, light, attachment, rotation,
                cx, cz, .25F, .50F, .125F, .125F, reflect
        );
        gateComponentBox(
                c, pose, light, attachment, rotation,
                cx,
                cz + (on ? -.055F : .055F),
                .125F,
                .28F,
                .25F,
                .22F,
                reflect
        );
    }

    private static float[] gatePoint(
            Direction attachment,
            int rotation,
            float x,
            float y,
            float z
    ) {
        Direction right = MultipartBlockEntity.localToWorld(attachment, rotation, 1);
        Direction down = MultipartBlockEntity.localToWorld(attachment, rotation, 2);
        Direction normal = attachment.getOpposite();

        float ox = .5F + attachment.getStepX() * .5F;
        float oy = .5F + attachment.getStepY() * .5F;
        float oz = .5F + attachment.getStepZ() * .5F;
        float dx = x - .5F;
        float dz = z - .5F;

        return new float[] {
                ox + right.getStepX() * dx + down.getStepX() * dz + normal.getStepX() * y,
                oy + right.getStepY() * dx + down.getStepY() * dz + normal.getStepY() * y,
                oz + right.getStepZ() * dx + down.getStepZ() * dz + normal.getStepZ() * y
        };
    }

    private static void gateFace(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction normal,
            float[] a,
            float[] b,
            float[] d,
            float[] e
    ) {
        face(
                c, pose, light,
                normal.getStepX(), normal.getStepY(), normal.getStepZ(),
                a[0],a[1],a[2],0,0,
                b[0],b[1],b[2],0,1,
                d[0],d[1],d[2],1,1,
                e[0],e[1],e[2],1,0
        );
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
        // BaseCenterWirePart uses 2/8 from center on every axis:
        // a 0.25..0.75 center cube with equally wide connection arms.
        box(c, pose, light, .25F,.25F,.25F, .75F,.75F,.75F);
        for (Direction direction : Direction.values()) {
            if ((connections & (1 << direction.ordinal())) == 0) continue;
            switch (direction) {
                case DOWN -> box(c,pose,light,.25F,0,.25F,.75F,.25F,.75F);
                case UP -> box(c,pose,light,.25F,.75F,.25F,.75F,1,.75F);
                case NORTH -> box(c,pose,light,.25F,.25F,0,.75F,.75F,.25F);
                case SOUTH -> box(c,pose,light,.25F,.25F,.75F,.75F,.75F,1);
                case WEST -> box(c,pose,light,0,.25F,.25F,.25F,.75F,.75F);
                case EAST -> box(c,pose,light,.75F,.25F,.25F,1,.75F,.75F);
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
                .25F-e,.25F-e,.25F-e,
                .75F+e,.75F+e,.75F+e
        );
        for (Direction direction : Direction.values()) {
            if ((connections & (1 << direction.ordinal())) == 0) continue;
            switch (direction) {
                case DOWN -> box(c,pose,light,.25F-e,0,.25F-e,.75F+e,.25F,.75F+e);
                case UP -> box(c,pose,light,.25F-e,.75F,.25F-e,.75F+e,1,.75F+e);
                case NORTH -> box(c,pose,light,.25F-e,.25F-e,0,.75F+e,.75F+e,.25F);
                case SOUTH -> box(c,pose,light,.25F-e,.25F-e,.75F,.75F+e,.75F+e,1);
                case WEST -> box(c,pose,light,0,.25F-e,.25F-e,.25F,.75F+e,.75F+e);
                case EAST -> box(c,pose,light,.75F,.25F-e,.25F-e,1,.75F+e,.75F+e);
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
            int mask,
            int rgb
    ) {
        if (shape == 0) {
            drawSevenSegment(
                    c, pose, light,
                    attachment, rotation,
                    .18F, .18F, .46F, .82F,
                    mask & 0xFF,
                    rgb
            );
            drawSevenSegment(
                    c, pose, light,
                    attachment, rotation,
                    .54F, .18F, .82F, .82F,
                    (mask >>> 8) & 0xFF,
                    rgb
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
                    .145F,
                    rgb
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
            int bits,
            int rgb
    ) {
        float w = u1 - u0;
        float h = v1 - v0;
        float t = Math.min(w, h) * .13F;
        float mid = (v0 + v1) * .5F;

        // 0 top, 1 upper-right, 2 lower-right, 3 bottom,
        // 4 lower-left, 5 upper-left, 6 middle, 7 decimal point.
        if ((bits & 0x01) != 0) surfaceRect(c,pose,light,attachment,rotation,u0+t,v0,u1-t,v0+t,.145F,rgb);
        if ((bits & 0x02) != 0) surfaceRect(c,pose,light,attachment,rotation,u1-t,v0+t,u1,mid-t*.5F,.145F,rgb);
        if ((bits & 0x04) != 0) surfaceRect(c,pose,light,attachment,rotation,u1-t,mid+t*.5F,u1,v1-t,.145F,rgb);
        if ((bits & 0x08) != 0) surfaceRect(c,pose,light,attachment,rotation,u0+t,v1-t,u1-t,v1,.145F,rgb);
        if ((bits & 0x10) != 0) surfaceRect(c,pose,light,attachment,rotation,u0,mid+t*.5F,u0+t,v1-t,.145F,rgb);
        if ((bits & 0x20) != 0) surfaceRect(c,pose,light,attachment,rotation,u0,v0+t,u0+t,mid-t*.5F,.145F,rgb);
        if ((bits & 0x40) != 0) surfaceRect(c,pose,light,attachment,rotation,u0+t,mid-t*.5F,u1-t,mid+t*.5F,.145F,rgb);
        if ((bits & 0x80) != 0) surfaceRect(c,pose,light,attachment,rotation,u1-t*1.2F,v1-t*1.2F,u1,v1,.145F,rgb);
    }

    static void gatePanelLights(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            int mask,
            float centerX,
            float centerZ,
            boolean rotate180,
            boolean reflect
    ) {
        if (reflect) centerX = 1.0F - centerX;

        float cell = 1.0F / 16.0F;
        float startX = centerX - 2.0F * cell;
        float startZ = centerZ - 2.0F * cell;
        float inset = .006F;

        for (int bit = 0; bit < 16; bit++) {
            if ((mask & (1 << bit)) == 0) continue;

            int visualBit = rotate180 ? 15 - bit : bit;
            int row = visualBit / 4;
            int col = visualBit % 4;

            float x0 = startX + col * cell + inset;
            float z0 = startZ + row * cell + inset;
            float x1 = startX + (col + 1) * cell - inset;
            float z1 = startZ + (row + 1) * cell - inset;

            surfaceRect(
                    c, pose, light,
                    attachment, rotation,
                    x0, z0, x1, z1,
                    .314F
            );
        }
    }

    static void gateSignalBar(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            int level,
            boolean inverted,
            boolean reflect
    ) {
        level = Math.max(0, Math.min(15, level));
        if (level == 0) return;

        float centerX = .5F;
        if (reflect) centerX = 1.0F - centerX;
        float fraction = level / 15.0F;
        float z0 = inverted ? .75F - .5F * fraction : .25F;
        float z1 = inverted ? .75F : .25F + .5F * fraction;

        surfaceRect(
                c, pose, light,
                attachment, rotation,
                centerX - .035F, z0,
                centerX + .035F, z1,
                .377F
        );
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
            float depth,
            int rgb
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

        orientedQuadColor(
                c, pose, light,
                attachment.getOpposite(),
                a[0],a[1],a[2],
                b[0],b[1],b[2],
                e[0],e[1],e[2],
                d[0],d[1],d[2],
                rgb
        );
    }

    private static void orientedQuadColor(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            Direction normal,
            float ax,float ay,float az,
            float bx,float by,float bz,
            float cx,float cy,float cz,
            float dx,float dy,float dz,
            int rgb
    ) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float nx = normal.getStepX();
        float ny = normal.getStepY();
        float nz = normal.getStepZ();

        vertexColor(c,pose,light,ax,ay,az,0,0,nx,ny,nz,r,g,b);
        vertexColor(c,pose,light,bx,by,bz,1,0,nx,ny,nz,r,g,b);
        vertexColor(c,pose,light,cx,cy,cz,1,1,nx,ny,nz,r,g,b);
        vertexColor(c,pose,light,dx,dy,dz,0,1,nx,ny,nz,r,g,b);
        vertexColor(c,pose,light,dx,dy,dz,0,1,-nx,-ny,-nz,r,g,b);
        vertexColor(c,pose,light,cx,cy,cz,1,1,-nx,-ny,-nz,r,g,b);
        vertexColor(c,pose,light,bx,by,bz,1,0,-nx,-ny,-nz,r,g,b);
        vertexColor(c,pose,light,ax,ay,az,0,0,-nx,-ny,-nz,r,g,b);
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

    private static void vertexColor(
            VertexConsumer c, PoseStack.Pose pose, int light,
            float x,float y,float z,float u,float v,
            float nx,float ny,float nz,
            int r,int g,int b
    ) {
        c.addVertex(pose,x,y,z)
                .setColor(r,g,b,255)
                .setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose,nx,ny,nz);
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
