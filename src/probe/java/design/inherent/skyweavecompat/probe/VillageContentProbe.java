package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import java.util.TreeMap;

/** Native no-player loot rolls from realized containers, not Lootr entitlements. */
final class VillageContentProbe {
    private VillageContentProbe() {}

    /** Local placement integrity, not villager pathfinding or a survival playtest. */
    static JsonObject access(ServerLevel level, BoundingBox bounds) {
        int doors = 0, beds = 0, supportedBeds = 0;
        var unsupportedDoors = new JsonArray();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                var state = level.getBlockState(pos.set(x,y,z));
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    doors++;
                    if (!state.canSurvive(level,pos)) unsupportedDoors.add(pos.toShortString());
                }
                if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                        && state.getValue(net.minecraft.world.level.block.BedBlock.PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                    beds++;
                    var below = pos.below();
                    if (!level.getBlockState(below).getCollisionShape(level,below).isEmpty()) supportedBeds++;
                }
            }
        var result = new JsonObject(); result.addProperty("doors",doors); result.addProperty("beds",beds);
        result.addProperty("bedsWithSupportBelowHead",supportedBeds);result.add("unsupportedDoorPositions",unsupportedDoors);
        result.addProperty("evidence","Native door survival and local bed footing only; access/routes/jobs require separate review");
        return result;
    }

    static JsonArray inspect(ServerLevel level, BoundingBox bounds) throws Exception {
        return inspect(level, bounds, true);
    }

    static JsonArray inspect(ServerLevel level, BoundingBox bounds, boolean requireContainers) throws Exception {
        var rows = new JsonArray();
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                var entities = level.getChunk(cx, cz).getBlockEntities();
                // Stable ordering makes the saved/reopened comparison meaningful.
                for (var pos : entities.keySet().stream().sorted().toList()) {
                    if (!bounds.isInside(pos)) continue;
                    var entity = entities.get(pos);
                    ResourceKey<LootTable> key;
                    try {
                        @SuppressWarnings("unchecked")
                        var lootrKey = (ResourceKey<LootTable>)entity.getClass().getMethod("getInfoLootTable").invoke(entity);
                        key = lootrKey;
                    } catch (NoSuchMethodException absent) {
                        key = entity instanceof RandomizableContainerBlockEntity container ? container.getLootTable() : null;
                    }
                    if (key == null) continue;
                    var table = level.getServer().reloadableRegistries().getLootTable(key);
                    if (table == LootTable.EMPTY) throw new AssertionError("Missing realized village loot table: " + key);
                    var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                            .create(LootContextParamSets.CHEST);
                    var counts = new TreeMap<String, Integer>();
                    for (var stack : table.getRandomItems(params, pos.asLong() ^ level.getSeed()))
                        counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                    var row = new JsonObject(); row.add("position", new Gson().toJsonTree(pos));
                    row.addProperty("container", BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString());
                    row.addProperty("lootTable", key.location().toString()); row.add("sampleItems", new Gson().toJsonTree(counts));
                    rows.add(row);
                }
            }
        if (requireContainers && rows.isEmpty()) throw new AssertionError("No realized village loot containers");
        return rows;
    }
}
