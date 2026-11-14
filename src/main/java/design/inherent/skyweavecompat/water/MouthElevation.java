package design.inherent.skyweavecompat.water;

/** One emitter's routed head and physical receiver, before native path publication. */
public final class MouthElevation {
    private int receiver,head;
    private boolean hasReceiver,water,hasHead;

    public void voidAt(int rimY) { receiver=rimY;water=false;hasReceiver=true; }
    public void waterAt(int waterY) { receiver=waterY;water=true;hasReceiver=true; }
    public boolean hasReceiver() { return hasReceiver; }
    public boolean hasHead() { return hasHead; }

    /** Capture native incision/sill/lake-floor output, not an independently guessed channel depth. */
    public void head(int nativeY) {
        if(hasHead && head!=nativeY)throw new IllegalStateException("Routed mouth head changed within an emission");
        head=nativeY;hasHead=true;
    }

    public boolean joins() { return hasReceiver && hasHead && (!water || receiver<=head); }

    /** DW uses seaLevel-1; this datum cannot raise the already routed incoming head. */
    public int datum() {
        if(!hasReceiver || !hasHead)throw new IllegalStateException("Mouth elevation is not bound");
        return Math.addExact(Math.min(receiver,head),1);
    }
    public int incomingY() { if(!hasHead)throw new IllegalStateException("Missing routed head");return head; }
    public int receiverY() { if(!hasReceiver)throw new IllegalStateException("Missing physical receiver");return receiver; }
}
