package design.inherent.skyweavecompat.probe;

import com.AdamNew.dynamicwaters.terrain.BasinTile;
import com.google.gson.*;
import design.inherent.skyweavecompat.water.MouthElevation;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Opt-in native method controls. Replays publish only into a detached private tile. */
public final class WaterEmissionControls {
    private static final boolean ENABLED=Boolean.getBoolean("neighbors.probe.waterEmitters");
    private record Emission(Object builder,Object[] arguments) {}
    private static Emission first;
    private static final ThreadLocal<Boolean> CAPTURING=ThreadLocal.withInitial(()->false);
    private static Object replaying;
    private static boolean failOnce;
    private static MouthElevation observed;
    private static int observedHead;
    private WaterEmissionControls() {}
    private static void check(boolean valid,String message) {if(!valid)throw new AssertionError(message);}
    private static Field field(Object owner,String name) throws ReflectiveOperationException {
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static MouthElevation frame(Object owner) throws ReflectiveOperationException {
        for(var f:owner.getClass().getDeclaredFields())if(f.getType()==MouthElevation.class) {f.setAccessible(true);return (MouthElevation)f.get(owner);}
        throw new AssertionError("Production emission scope field absent");
    }
    private static final class InjectedFailure extends RuntimeException {}

    public static synchronized void observe(Object builder,int[] cells,int from,int to,int owner,
            boolean source,boolean lake,boolean confluence,boolean mouth,boolean hitMain,int inlet,Object main,double offset) {
        if(!ENABLED)return;
        try {
            var bound=frame(builder);
            if(builder==replaying) {
                observed=bound;observedHead=bound==null?Integer.MIN_VALUE:bound.incomingY();
                if(failOnce) {failOnce=false;throw new InjectedFailure();}
            } else if(CAPTURING.get() && first==null && bound!=null && bound.joins() && mouth && !hitMain && inlet==Integer.MIN_VALUE) {
                first=new Emission(builder,new Object[]{cells.clone(),from,to,owner,source,lake,confluence,mouth,hitMain,inlet,main,offset});
            }
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Native emission observation failed",failure);}
    }

    static JsonObject run(MinecraftServer server,JsonObject fixture) throws Exception {
        first=null;CAPTURING.set(true);
        try {
            var point=fixture.getAsJsonArray("points").get(0).getAsJsonObject();var level=server.overworld();
            var generator=level.getChunkSource().getGenerator();
            BasinTile.build(Math.floorDiv(point.get("x").getAsInt(),1024),Math.floorDiv(point.get("z").getAsInt(),1024),generator,
                    level.getChunkSource().randomState(),level.getSeed(),generator.getSeaLevel(),
                    BasinTile.channelThreshold(com.AdamNew.dynamicwaters.Config.MOUNTAIN_RIVER_DENSITY.get()),true);
        } finally {CAPTURING.remove();}
        check(ENABLED && first!=null,"No real owned mouth reached the native emitter control");
        var emission=first;first=null;var builder=emission.builder();
        var outputField=field(builder,"tile");var oldOutput=outputField.get(builder);
        var generatorField=field(builder,"generator");var oldGenerator=generatorField.get(builder);
        var ctor=BasinTile.class.getDeclaredConstructor(int.class,int.class);ctor.setAccessible(true);
        var tile=(BasinTile)oldOutput;var output=ctor.newInstance(tile.tileX,tile.tileZ);
        tile.pathsByRegion.keySet().forEach(key->output.pathsByRegion.put(key,new ArrayList<>()));
        var method=Arrays.stream(builder.getClass().getDeclaredMethods()).filter(m->m.getName().equals("emit")).findFirst().orElseThrow();method.setAccessible(true);
        var report=new JsonObject();int before=server.overworld().getChunkSource().getLoadedChunksCount();
        replaying=builder;outputField.set(builder,output);
        try {
            var args=emission.arguments().clone();
            call(method,builder,args);check(count(output)==1,"Native void mouth did not publish one path");
            int head=observedHead;var path=only(output);
            check(observed!=null && observed.joins() && path.segmentsY[path.validNodeCount-1]==head,"Void mouth raised its native incoming head");
            String original=signature(output);report.addProperty("nativeIncomingY",head);
            report.addProperty("voidEndpointY",path.segmentsY[0]);
            clear(output);call(method,builder,args);check(original.equals(signature(output)),"Repeated native emission changed");
            report.addProperty("repeatIdentical",true);

            clear(output);args[9]=head-1;call(method,builder,args);
            check(count(output)==1 && observed!=null && observed.receiverY()==head-1 && only(output).segmentsY[0]==head-1,
                    "Declared native water receiver was not preserved");
            report.addProperty("lowerDeclaredWaterReceiver",true);
            clear(output);args[9]=head+64;call(method,builder,args);
            check(count(output)==0,"Impossible uphill receiver published a native path");
            check(frame(builder)==null,"Cancellation leaked the product emission frame");report.addProperty("uphillReceiverRefused",true);

            args=emission.arguments().clone();failOnce=true;
            boolean failed=false;try {call(method,builder,args);}catch(InjectedFailure expected) {failed=true;}
            check(failed && !failOnce && frame(builder)==null && count(output)==0,"Exception did not clear scope before publication");
            call(method,builder,args);check(original.equals(signature(output)),"Exception contaminated the next native emission");
            report.addProperty("exceptionScopeRestored",true);

            var floor=(int[])field(builder,"lakeFloorAt").get(builder);int cell=((int[])args[0])[(int)args[1]],saved=floor[cell];
            try {
                floor[cell]=head+7;clear(output);call(method,builder,args);
                check(observedHead==head+7,"Production datum did not use actual native lake-floor clamp");
                report.addProperty("nativeLakeFloorClamp",observedHead);
            } finally {floor[cell]=saved;}
            args=emission.arguments().clone();args[7]=false;args[6]=true;clear(output);call(method,builder,args);
            check(observed==null && count(output)==1,"Non-mouth confluence entered the local-mouth scope");
            String confluence=signature(output);clear(output);call(method,builder,args);
            check(confluence.equals(signature(output)),"Confluence original branch became unstable");report.addProperty("nonMouthConfluenceUnscoped",true);

            for(String preset:List.of("minecraft:normal","sky_archipelago:sky_islands")) {
                var foreign=server.registryAccess().registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.parse(preset)).createWorldDimensions().overworld();
                generatorField.set(builder,foreign);args=emission.arguments().clone();clear(output);call(method,builder,args);
                check(observed==null && count(output)==1,"Foreign generator acquired owned emission scope: "+preset);
                check(only(output).segmentsY[0]==field(builder,"seaLevel").getInt(builder)-1,"Foreign native sea endpoint changed: "+preset);
                report.addProperty("foreign_"+preset,true);
            }
        } finally {
            outputField.set(builder,oldOutput);generatorField.set(builder,oldGenerator);replaying=null;failOnce=false;observed=null;
        }
        check(server.overworld().getChunkSource().getLoadedChunksCount()==before,"Emitter control loaded world chunks");
        check(frame(builder)==null,"Native emission scope leaked after controls");
        report.addProperty("loadedChunksUnchanged",true);
        report.addProperty("scope","Real native emit against a private completed synchronous tile build; replays publish into another detached tile. No live asynchronous builder is mutated. Declared receiver/floor controls do not certify a physical lake mouth.");
        return report;
    }
    private static void call(Method method,Object target,Object[] args) throws Exception {
        observed=null;observedHead=Integer.MIN_VALUE;
        try {method.invoke(target,args);}catch(InvocationTargetException error) {
            if(error.getCause() instanceof InjectedFailure expected)throw expected;
            if(error.getCause() instanceof Exception failure)throw failure;
            throw error;
        }
        check(frame(target)==null,"Normal native return leaked emission scope");
    }
    private static void clear(BasinTile tile) {tile.pathsByRegion.values().forEach(List::clear);}
    private static int count(BasinTile tile) {return tile.pathsByRegion.values().stream().mapToInt(List::size).sum();}
    private static com.AdamNew.dynamicwaters.WatershedPath only(BasinTile tile) {return tile.pathsByRegion.values().stream().flatMap(List::stream).findFirst().orElseThrow();}
    private static String signature(BasinTile tile) {
        return tile.pathsByRegion.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e->e.getKey()+":"+e.getValue().stream().map(p->Arrays.toString(p.segmentsX)+Arrays.toString(p.segmentsZ)+Arrays.toString(p.segmentsY)).toList()).toList().toString();
    }
}
