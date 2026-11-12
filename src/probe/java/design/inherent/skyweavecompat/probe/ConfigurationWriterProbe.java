package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Native no-value-change saves, only inside the disposable harness directory. */
final class ConfigurationWriterProbe {
    static void run(Path output) throws Exception {
        var root=FMLPaths.GAMEDIR.get().toRealPath();
        if(!Files.isRegularFile(root.resolve("configuration-writer-fixture.json")))
            throw new IllegalStateException("Missing owned configuration-writer fixture marker");
        var rows=new JsonArray();var result=new JsonObject();result.add("nativeSpecs",rows);boolean stable=true;
        for(var type:ModConfig.Type.values())for(var config:ModConfigs.getConfigSet(type)) {
            var row=new JsonObject();row.addProperty("mod",config.getModId());row.addProperty("file",config.getFileName());
            row.addProperty("type",type.name());rows.add(row);
            if(config.getLoadedConfig()==null){row.addProperty("state","not loaded on this physical side/lifecycle");continue;}
            var path=config.getFullPath().toRealPath();
            if(!path.startsWith(root))throw new IllegalStateException("Native configuration escapes disposable root: "+config.getFileName());
            row.addProperty("path",root.relativize(path).toString());
            if(!(config.getSpec() instanceof ModConfigSpec spec)){row.addProperty("state","foreign spec; not saved by this probe");continue;}
            var original=Files.readAllBytes(path);var logical=values(spec.getValues());
            spec.save();var first=Files.readAllBytes(path);var firstValues=values(spec.getValues());
            spec.save();var second=Files.readAllBytes(path);var secondValues=values(spec.getValues());
            row.addProperty("before",hash(original));row.addProperty("afterFirstSave",hash(first));row.addProperty("afterSecondSave",hash(second));
            row.addProperty("logicalBefore",digest(logical));row.addProperty("logicalAfter",digest(secondValues));
            row.addProperty("valuesPreserved",logical.equals(firstValues) && logical.equals(secondValues));
            row.addProperty("secondSaveStable",Arrays.equals(first,second));row.addProperty("state","saved twice through native owner");
            if(!logical.equals(firstValues) || !logical.equals(secondValues) || !Arrays.equals(first,second)) {
                var changed=new JsonArray();
                var keys=new TreeSet<>(logical.keySet());keys.addAll(secondValues.keySet());
                for(var key:keys)if(!Objects.equals(logical.get(key),secondValues.get(key))) {
                    var difference=new JsonObject();difference.addProperty("key",key);
                    difference.addProperty("beforeValueSha256",hash(String.valueOf(logical.get(key)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    difference.addProperty("afterValueSha256",hash(String.valueOf(secondValues.get(key)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));changed.add(difference);
                }
                row.add("changedKeys",changed);
                boolean hostFallback=Arrays.equals(first,second) && changed.size()==1
                        && config.getModId().equals("powergrid") && config.getFileName().equals("powergrid-server.toml")
                        && net.neoforged.fml.ModList.get().getModContainerById("powergrid").orElseThrow().getModInfo().getVersion().toString().equals("0.6.2")
                        && "NATIVE".equals(logical.get("electricity.solver.solverBackend"))
                        && "JAVA".equals(secondValues.get("electricity.solver.solverBackend"));
                if(hostFallback) {
                    var typeClass=Class.forName("org.patryk3211.powergrid.config.CSolver$SolverBackend");
                    hostFallback=Boolean.FALSE.equals(typeClass.getMethod("isSupported").invoke(typeClass.getField("NATIVE").get(null)));
                }
                row.addProperty("expectedNativeHostFallback",hostFallback);
                // ValuesPreserved stays false. This exact source-verified hardware fallback
                // requires a host-owned seed; it does not qualify the whole file as managed.
                if(!hostFallback)stable=false;
            }
        }
        if(net.neoforged.fml.ModList.get().isLoaded("aeronautics_delivery_quests")) {
            var path=root.resolve("config/aeronautics_delivery_quests/custom_quests.json");
            byte[] before=Files.readAllBytes(path);
            Class.forName("com.ladderstar.adq.QuestGenerator").getMethod("loadCustomTemplates").invoke(null);
            result.addProperty("adqTemplateReloadBefore",hash(before));
            result.addProperty("adqTemplateReloadAfter",hash(Files.readAllBytes(path)));
        }
        if(net.neoforged.fml.ModList.get().isLoaded("bettervillage")) {
            var path=root.resolve("config/bettervillage_1.properties");
            var before=Files.readAllBytes(path);
            var config=Class.forName("com.jtorleonstudios.bettervillage.Config").getMethod("get").invoke(null);
            config.getClass().getMethod("save").invoke(config);
            var first=Files.readAllBytes(path);
            config.getClass().getMethod("save").invoke(config);
            result.addProperty("betterVillageSaveBefore",hash(before));
            result.addProperty("betterVillageSaveAfter",hash(Files.readAllBytes(path)));
            result.addProperty("betterVillageRepeatedSaveStable",Arrays.equals(before,first) && Arrays.equals(first,Files.readAllBytes(path)));
        }
        if(net.neoforged.fml.ModList.get().isLoaded("zfastnoise")) {
            var effective=(java.util.Map<?,?>)Class.forName("org.codeberg.zenxarch.fastnoise.FastNoiseConfig")
                    .getMethod("loadConfig").invoke(null);
            result.addProperty("zfastnoiseEffectiveBiomeMixin",(Boolean)effective.get("mixin.perf.biome"));
            if(!Boolean.FALSE.equals(effective.get("mixin.perf.biome")))stable=false;
        }
        result.addProperty("complete",stable);
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
    }
    private static Map<String,String> values(com.electronwill.nightconfig.core.UnmodifiableConfig config) {
        var values=new TreeMap<String,String>();collect(config,values);
        return values;
    }
    private static void collect(com.electronwill.nightconfig.core.UnmodifiableConfig config,Map<String,String> values) {
        for(var value:config.valueMap().values()) {
            if(value instanceof ModConfigSpec.ConfigValue<?> field) {
                if(values.size()>=16384)throw new IllegalStateException("Native spec exceeds writer-survey budget");
                values.put(String.join(".",field.getPath()),String.valueOf(field.getRaw()));
            } else if(value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested)collect(nested,values);
        }
    }
    private static String digest(Map<String,String> values) throws Exception {return hash(new Gson().toJson(values).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
