package design.inherent.skyweavecompat.wayfinder;

import java.util.UUID;

/** One connection/world-bound presentation receipt, independent of rendering and permission. */
public final class WayfinderFeedback {
    private UUID request;
    private Object world,connection;
    private String dimension;
    private int sequence=-1;
    private boolean terminal;
    public boolean begin(UUID id,Object world,Object connection,String dimension) {
        if(id==null || id.equals(new UUID(0,0)) || world==null || connection==null)return false;
        if(this.request!=null && this.world==world && this.connection==connection) {
            if(this.request.equals(id))return !terminal;
            if(!terminal)return false;
        }
        this.request=id;this.world=world;this.connection=connection;this.dimension=dimension;
        sequence=-1;terminal=false;return true;
    }
    public boolean matches(UUID id,Object world,Object connection,String dimension) {
        return request!=null && request.equals(id) && this.world==world && this.connection==connection
                && this.dimension.equals(dimension) && !terminal;
    }
    public boolean accept(WayfinderStatusPacket packet,Object world,Object connection,String dimension) {
        if(!matches(packet.request(),world,connection,dimension) || !packet.dimension().equals(dimension)
                || packet.sequence()<=sequence)return false;
        sequence=packet.sequence();terminal=packet.status().terminal;return true;
    }
    public boolean active(){return request!=null && !terminal;}
    public void retire(UUID id,Object world,Object connection,String dimension) {
        if(matches(id,world,connection,dimension))terminal=true;
    }
    public UUID request(){return request;}
    public boolean sameSession(Object world,Object connection) {return this.world==world && this.connection==connection;}
    public void clear(){request=null;world=null;connection=null;dimension=null;sequence=-1;terminal=false;}
}
