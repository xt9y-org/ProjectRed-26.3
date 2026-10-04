package dev.xt9y.projectred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

    static void framedWire(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light
    ) {
        box(c, pose, light, .375F,.375F,.375F, .625F,.625F,.625F);
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
