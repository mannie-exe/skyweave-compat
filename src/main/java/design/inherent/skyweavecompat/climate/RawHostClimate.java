package design.inherent.skyweavecompat.climate;

/** One generator-owned native host-cache call; source identity alone is not world ownership. */
public final class RawHostClimate implements AutoCloseable {
    private static final ThreadLocal<Object> CACHE=new ThreadLocal<>();
    private final Object previous;
    private RawHostClimate(Object cache) {
        previous=CACHE.get();
        if(cache==null)CACHE.remove();else CACHE.set(cache);
    }
    public static RawHostClimate query(Object cache) {return new RawHostClimate(cache);}
    public static boolean owns(Object cache) {return cache!=null && CACHE.get()==cache;}
    @Override public void close() {if(previous==null)CACHE.remove();else CACHE.set(previous);}
}
