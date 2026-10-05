package dev.xt9y.projectred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

final class ProjectRedObjModel {
    private record Ref(int position, int uv, int normal) {}
    private record Face(String group, Ref[] refs) {}

    private static final Map<String, ProjectRedObjModel> CACHE = new ConcurrentHashMap<>();
    private static final int[] REORIENT_SIDE = {0, 3, 3, 0, 0, 3};

    private final float[][] positions;
    private final float[][] uvs;
    private final float[][] normals;
    private final Face[] faces;

    private ProjectRedObjModel(
            float[][] positions,
            float[][] uvs,
            float[][] normals,
            Face[] faces
    ) {
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.faces = faces;
    }

    static ProjectRedObjModel get(String name) {
        return CACHE.computeIfAbsent(name, ProjectRedObjModel::load);
    }

    void render(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float angleY,
            boolean reflect
    ) {
        render(
                consumer, pose, light, attachment, rotation,
                offsetX, offsetY, offsetZ, scaleXZ, angleY, reflect,
                0xFFFFFF
        );
    }

    void render(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float angleY,
            boolean reflect,
            int rgb
    ) {
        renderFiltered(
                consumer, pose, light, attachment, rotation,
                offsetX, offsetY, offsetZ, scaleXZ, angleY, reflect,
                rgb, null, true, 0.0F, 0.0F, false, false
        );
    }

    void renderGroup(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float angleY,
            boolean reflect,
            int rgb,
            String group
    ) {
        renderFiltered(
                consumer, pose, light, attachment, rotation,
                offsetX, offsetY, offsetZ, scaleXZ, angleY, reflect,
                rgb, group, true, 0.0F, 0.0F, false, false
        );
    }

    void renderCorrectedGroup(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float angleY,
            boolean reflect,
            int rgb,
            String group
    ) {
        renderFiltered(
                consumer, pose, light, attachment, rotation,
                offsetX, offsetY, offsetZ, scaleXZ, angleY, reflect,
                rgb, group, false, 0.0F, 0.0F, false, false
        );
    }

    void renderBundledCable(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float offsetX,
            float offsetY,
            float offsetZ,
            boolean reflect,
            int rgb,
            float uCenter,
            float vCenter
    ) {
        int side = attachment.ordinal();
        boolean rotateUv = Math.floorMod(
                rotation + REORIENT_SIDE[side],
                4
        ) >= 2;

        // ComponentModelBakery.bundledCablePrecomputed applies an X
        // reflection first, then a 180-degree UV rotation.
        boolean flipU = reflect ^ rotateUv;
        boolean flipV = rotateUv;

        renderFiltered(
                consumer, pose, light, attachment, rotation,
                offsetX, offsetY, offsetZ, 1.0F, 0.0F, reflect,
                rgb, null, true,
                uCenter, vCenter, flipU, flipV
        );
    }

    private void renderFiltered(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float angleY,
            boolean reflect,
            int rgb,
            String group,
            boolean invertModelX,
            float uvCenterU,
            float uvCenterV,
            boolean flipU,
            boolean flipV
    ) {
        Direction right = MultipartBlockEntity.localToWorld(attachment, rotation, 1);
        Direction down = MultipartBlockEntity.localToWorld(attachment, rotation, 2);
        Direction normal = attachment.getOpposite();

        float originX = .5F + attachment.getStepX() * .5F;
        float originY = .5F + attachment.getStepY() * .5F;
        float originZ = .5F + attachment.getStepZ() * .5F;

        float cos = (float) Math.cos(angleY);
        float sin = (float) Math.sin(angleY);

        for (Face face : faces) {
            if (group != null && !group.equals(face.group)) continue;
            Ref[] refs = face.refs;
            if (refs.length < 3) continue;

            if (refs.length == 3) {
                emit(
                        consumer, pose, light,
                        refs[0], refs[1], refs[2], refs[2],
                        right, down, normal,
                        originX, originY, originZ,
                        offsetX, offsetY, offsetZ,
                        scaleXZ, cos, sin, reflect, rgb, invertModelX,
                        uvCenterU, uvCenterV, flipU, flipV
                );
                emit(
                        consumer, pose, light,
                        refs[2], refs[2], refs[1], refs[0],
                        right, down, normal,
                        originX, originY, originZ,
                        offsetX, offsetY, offsetZ,
                        scaleXZ, cos, sin, reflect, rgb, invertModelX,
                        uvCenterU, uvCenterV, flipU, flipV
                );
                continue;
            }

            for (int start = 1; start + 2 < refs.length; start += 2) {
                Ref a = refs[0];
                Ref b = refs[start];
                Ref c = refs[start + 1];
                Ref d = start + 2 < refs.length ? refs[start + 2] : c;
                emit(
                        consumer, pose, light,
                        a,b,c,d,
                        right,down,normal,
                        originX,originY,originZ,
                        offsetX,offsetY,offsetZ,
                        scaleXZ,cos,sin,reflect,rgb,invertModelX,
                        uvCenterU,uvCenterV,flipU,flipV
                );
                emit(
                        consumer, pose, light,
                        d,c,b,a,
                        right,down,normal,
                        originX,originY,originZ,
                        offsetX,offsetY,offsetZ,
                        scaleXZ,cos,sin,reflect,rgb,invertModelX,
                        uvCenterU,uvCenterV,flipU,flipV
                );
            }
        }
    }

    private void emit(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Ref a,
            Ref b,
            Ref c,
            Ref d,
            Direction right,
            Direction down,
            Direction normal,
            float originX,
            float originY,
            float originZ,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float cos,
            float sin,
            boolean reflect,
            int rgb,
            boolean invertModelX,
            float uvCenterU,
            float uvCenterV,
            boolean flipU,
            boolean flipV
    ) {
        vertex(consumer,pose,light,a,right,down,normal,originX,originY,originZ,offsetX,offsetY,offsetZ,scaleXZ,cos,sin,reflect,rgb,invertModelX,uvCenterU,uvCenterV,flipU,flipV);
        vertex(consumer,pose,light,b,right,down,normal,originX,originY,originZ,offsetX,offsetY,offsetZ,scaleXZ,cos,sin,reflect,rgb,invertModelX);
        vertex(consumer,pose,light,c,right,down,normal,originX,originY,originZ,offsetX,offsetY,offsetZ,scaleXZ,cos,sin,reflect,rgb,invertModelX);
        vertex(consumer,pose,light,d,right,down,normal,originX,originY,originZ,offsetX,offsetY,offsetZ,scaleXZ,cos,sin,reflect,rgb,invertModelX);
    }

    private void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Ref ref,
            Direction right,
            Direction down,
            Direction normal,
            float originX,
            float originY,
            float originZ,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float cos,
            float sin,
            boolean reflect,
            int rgb,
            boolean invertModelX,
            float uvCenterU,
            float uvCenterV,
            boolean flipU,
            boolean flipV
    ) {
        float[] source = positions[ref.position];
        float x = (invertModelX ? -source[0] : source[0]) * scaleXZ;
        float y = source[1];
        float z = source[2] * scaleXZ;

        float rotatedX = x * cos - z * sin;
        float rotatedZ = x * sin + z * cos;
        float localX = rotatedX + offsetX;
        float localY = y + offsetY;
        float localZ = rotatedZ + offsetZ;

        if (reflect) localX = 1.0F - localX;

        float dx = localX - .5F;
        float dz = localZ - .5F;

        float wx = originX
                + right.getStepX() * dx
                + down.getStepX() * dz
                + normal.getStepX() * localY;
        float wy = originY
                + right.getStepY() * dx
                + down.getStepY() * dz
                + normal.getStepY() * localY;
        float wz = originZ
                + right.getStepZ() * dx
                + down.getStepZ() * dz
                + normal.getStepZ() * localY;

        float nx = 0.0F;
        float ny = 1.0F;
        float nz = 0.0F;
        if (ref.normal >= 0 && ref.normal < normals.length) {
            float[] n = normals[ref.normal];
            float modelNx = invertModelX ? -n[0] : n[0];
            float modelNy = n[1];
            float modelNz = n[2];
            float rotatedNx = modelNx * cos - modelNz * sin;
            float rotatedNz = modelNx * sin + modelNz * cos;
            if (reflect) rotatedNx = -rotatedNx;

            nx = right.getStepX() * rotatedNx
                    + down.getStepX() * rotatedNz
                    + normal.getStepX() * modelNy;
            ny = right.getStepY() * rotatedNx
                    + down.getStepY() * rotatedNz
                    + normal.getStepY() * modelNy;
            nz = right.getStepZ() * rotatedNx
                    + down.getStepZ() * rotatedNz
                    + normal.getStepZ() * modelNy;
        }

        float u = 0.0F;
        float v = 0.0F;
        if (ref.uv >= 0 && ref.uv < uvs.length) {
            u = uvs[ref.uv][0];
            v = 1.0F - uvs[ref.uv][1];

            if (flipU) u = 2.0F * uvCenterU - u;
            if (flipV) v = 2.0F * uvCenterV - v;
        }

        consumer.addVertex(pose,wx,wy,wz)
                .setColor(
                        rgb >> 16 & 0xFF,
                        rgb >> 8 & 0xFF,
                        rgb & 0xFF,
                        255
                )
                .setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose,nx,ny,nz);
    }

    private static ProjectRedObjModel load(String name) {
        String path = "/assets/projectred/textures/obj/integration/" + name + ".obj";
        try (InputStream stream = ProjectRedObjModel.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing ProjectRed model " + path);
            }

            List<float[]> positions = new ArrayList<>();
            List<float[]> uvs = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();
            List<Face> faces = new ArrayList<>();
            String currentGroup = "";

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.charAt(0) == '#') continue;

                    if (line.startsWith("g ") || line.startsWith("o ")) {
                        currentGroup = line.substring(2).trim();
                    } else if (line.startsWith("v ")) {
                        String[] p = line.substring(2).trim().split("\\s+");
                        positions.add(new float[] {
                                Float.parseFloat(p[0]),
                                Float.parseFloat(p[1]),
                                Float.parseFloat(p[2])
                        });
                    } else if (line.startsWith("vt ")) {
                        String[] p = line.substring(3).trim().split("\\s+");
                        uvs.add(new float[] {
                                Float.parseFloat(p[0]),
                                Float.parseFloat(p[1])
                        });
                    } else if (line.startsWith("vn ")) {
                        String[] p = line.substring(3).trim().split("\\s+");
                        normals.add(new float[] {
                                Float.parseFloat(p[0]),
                                Float.parseFloat(p[1]),
                                Float.parseFloat(p[2])
                        });
                    } else if (line.startsWith("f ")) {
                        String[] p = line.substring(2).trim().split("\\s+");
                        Ref[] refs = new Ref[p.length];
                        for (int i = 0; i < p.length; i++) {
                            String[] index = p[i].split("/");
                            refs[i] = new Ref(
                                    parseIndex(index, 0, positions.size()),
                                    parseIndex(index, 1, uvs.size()),
                                    parseIndex(index, 2, normals.size())
                            );
                        }
                        faces.add(new Face(currentGroup, refs));
                    }
                }
            }

            return new ProjectRedObjModel(
                    positions.toArray(float[][]::new),
                    uvs.toArray(float[][]::new),
                    normals.toArray(float[][]::new),
                    faces.toArray(Face[]::new)
            );
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Could not load ProjectRed model " + path, error);
        }
    }

    private static int parseIndex(String[] values, int index, int size) {
        if (index >= values.length || values[index].isEmpty()) return -1;
        int value = Integer.parseInt(values[index]);
        return value > 0 ? value - 1 : size + value;
    }

    private ProjectRedObjModel() {
        throw new AssertionError();
    }
}
