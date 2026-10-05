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
 * Direct port of ProjectRed WireModel3D for Integration gate surface wires.
 */
final class ProjectRedGateWireModel {
    private record V(float x,float y,float z,float u,float v) {}
    private record Quad(V a,V b,V c,V d) {}

    private static final Map<Integer, Quad[]> CACHE = new ConcurrentHashMap<>();

    static void render(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Direction attachment,
            int rotation,
            String family,
            int count,
            int selectedMask,
            boolean border,
            boolean reflect
    ) {
        Direction right=MultipartBlockEntity.localToWorld(attachment,rotation,1);
        Direction down=MultipartBlockEntity.localToWorld(attachment,rotation,2);
        Direction normal=attachment.getOpposite();

        float ox=.5F+attachment.getStepX()*.5F;
        float oy=.5F+attachment.getStepY()*.5F;
        float oz=.5F+attachment.getStepZ()*.5F;

        for(int wire=0;wire<count;wire++){
            if((selectedMask & 1<<wire)==0) continue;
            int maskIndex=GateWireMasks.maskIndex(family+"-"+wire);
            if(maskIndex<0) continue;

            int key=(maskIndex<<1)|(border?1:0);
            Quad[] model=CACHE.computeIfAbsent(
                    key,
                    ignored->generate(maskIndex,border)
            );

            for(Quad q:model){
                emit(
                        consumer,pose,light,q,
                        right,down,normal,
                        ox,oy,oz,
                        reflect
                );
            }
        }
    }

    private static Quad[] generate(int maskIndex,boolean border){
        List<Quad> out=new ArrayList<>();
        float height=border ? .01F : .02F;
        float d=.0004F-height/50.0F;

        for(int i=GateWireMasks.start(maskIndex);i<GateWireMasks.end(maskIndex);i++){
            int packed=GateWireMasks.packed(i);
            int x=GateWireMasks.x(packed);
            int y=GateWireMasks.y(packed);
            int w=GateWireMasks.width(packed);
            int h=GateWireMasks.height(packed);

            if(border){
                x-=2;
                y-=2;
                w+=4;
                h+=4;
                if(x<0){w+=x;x=0;}
                if(y<0){h+=y;y=0;}
                if(x+w>=32) w-=x+w-32;
                if(y+h>=32) h-=y+h-32;
            }

            float x1=x/32.0F+d;
            float z1=y/32.0F+d;
            float x2=(x+w)/32.0F-d;
            float z2=(y+h)/32.0F-d;
            appendBlock(
                    out,
                    x1,.125F,z1,
                    x2,.125F+height,z2
            );
        }

        return out.toArray(Quad[]::new);
    }

    /**
     * Equivalent to CCModel.generateBlock(..., mask=1), followed by
     * shrinkUVs(0.0005).
     */
    private static void appendBlock(
            List<Quad> out,
            float x1,float y1,float z1,
            float x2,float y2,float z2
    ){
        // top
        addShrunk(out,
                new V(x2,y2,z2,x2,z2),
                new V(x2,y2,z1,x2,z1),
                new V(x1,y2,z1,x1,z1),
                new V(x1,y2,z2,x1,z2));

        // side at z1
        addShrunk(out,
                new V(x1,y1,z1,1-x1,1-y1),
                new V(x1,y2,z1,1-x1,1-y2),
                new V(x2,y2,z1,1-x2,1-y2),
                new V(x2,y1,z1,1-x2,1-y1));

        // side at z2
        addShrunk(out,
                new V(x2,y1,z2,x2,1-y1),
                new V(x2,y2,z2,x2,1-y2),
                new V(x1,y2,z2,x1,1-y2),
                new V(x1,y1,z2,x1,1-y1));

        // side at x1
        addShrunk(out,
                new V(x1,y1,z2,z2,1-y1),
                new V(x1,y2,z2,z2,1-y2),
                new V(x1,y2,z1,z1,1-y2),
                new V(x1,y1,z1,z1,1-y1));

        // side at x2
        addShrunk(out,
                new V(x2,y1,z1,1-z1,1-y1),
                new V(x2,y2,z1,1-z1,1-y2),
                new V(x2,y2,z2,1-z2,1-y2),
                new V(x2,y1,z2,1-z2,1-y1));
    }

    private static void addShrunk(List<Quad> out,V a,V b,V c,V d){
        float cu=(a.u+b.u+c.u+d.u)*.25F;
        float cv=(a.v+b.v+c.v+d.v)*.25F;
        out.add(new Quad(
                shrink(a,cu,cv),
                shrink(b,cu,cv),
                shrink(c,cu,cv),
                shrink(d,cu,cv)
        ));
    }

    private static V shrink(V q,float cu,float cv){
        float u=q.u+(q.u<cu ? .0005F : -.0005F);
        float v=q.v+(q.v<cv ? .0005F : -.0005F);
        return new V(q.x,q.y,q.z,u,v);
    }

    private static void emit(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int light,
            Quad q,
            Direction right,
            Direction down,
            Direction normal,
            float ox,float oy,float oz,
            boolean reflect
    ){
        float[] a=point(q.a,right,down,normal,ox,oy,oz,reflect);
        float[] b=point(q.b,right,down,normal,ox,oy,oz,reflect);
        float[] c=point(q.c,right,down,normal,ox,oy,oz,reflect);
        float[] d=point(q.d,right,down,normal,ox,oy,oz,reflect);

        float abx=b[0]-a[0],aby=b[1]-a[1],abz=b[2]-a[2];
        float adx=d[0]-a[0],ady=d[1]-a[1],adz=d[2]-a[2];
        float nx=aby*adz-abz*ady;
        float ny=abz*adx-abx*adz;
        float nz=abx*ady-aby*adx;
        float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(len>1e-7F){nx/=len;ny/=len;nz/=len;}

        vertex(consumer,pose,light,a,q.a,nx,ny,nz);
        vertex(consumer,pose,light,b,q.b,nx,ny,nz);
        vertex(consumer,pose,light,c,q.c,nx,ny,nz);
        vertex(consumer,pose,light,d,q.d,nx,ny,nz);

        vertex(consumer,pose,light,d,q.d,-nx,-ny,-nz);
        vertex(consumer,pose,light,c,q.c,-nx,-ny,-nz);
        vertex(consumer,pose,light,b,q.b,-nx,-ny,-nz);
        vertex(consumer,pose,light,a,q.a,-nx,-ny,-nz);
    }

    private static float[] point(
            V q,
            Direction right,
            Direction down,
            Direction normal,
            float ox,float oy,float oz,
            boolean reflect
    ){
        float x=reflect ? 1.0F-q.x : q.x;
        float dx=x-.5F;
        float dz=q.z-.5F;
        return new float[]{
                ox+right.getStepX()*dx+down.getStepX()*dz+normal.getStepX()*q.y,
                oy+right.getStepY()*dx+down.getStepY()*dz+normal.getStepY()*q.y,
                oz+right.getStepZ()*dx+down.getStepZ()*dz+normal.getStepZ()*q.y
        };
    }

    private static void vertex(
            VertexConsumer c,
            PoseStack.Pose pose,
            int light,
            float[] p,
            V source,
            float nx,float ny,float nz
    ){
        c.addVertex(pose,p[0],p[1],p[2])
                .setColor(255,255,255,255)
                .setUv(source.u,source.v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose,nx,ny,nz);
    }

    private ProjectRedGateWireModel(){}
}
