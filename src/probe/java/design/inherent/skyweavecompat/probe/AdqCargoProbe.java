package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real native board/cargo/payment on a copied, previously generated disposable offer. */
final class AdqCargoProbe {
    private MinecraftServer server;
    private final JsonObject report = new JsonObject();
    private int ticks, phase, emeralds, bread;
    private Object quest;
    private UUID questId, cargoId;
    private ServerPlayer player;
    private ChunkPos foreign;
    private boolean foreignOwned;
    private Class<?> generator, board, tracker, model;
    private BlockPos destination;
    private static Object value(Object owner, String name) throws Exception { return owner.getClass().getMethod(name).invoke(owner); }
    private List<?> quests() throws Exception { return (List<?>)generator.getMethod("getAvailableQuests").invoke(null); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    AdqCargoProbe() {
        report.addProperty("scope", "Native board codec, acceptance, cargo assembly, delivery and payment; actual Sable teleport is fixture transport, not a flight qualification");
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> server = event.getServer());
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ServerTickEvent.Post event) {
        if (server != event.getServer() || report.has("complete")) return;
        try {
            check(++ticks < 2400, "Cargo observation exceeded120seconds");
            var level = server.overworld();
            if (phase == 2) tracker.getMethod("tick", net.minecraft.server.level.ServerLevel.class).invoke(null, level);
            if (phase == 0) {
                generator = Class.forName("com.ladderstar.adq.QuestGenerator"); board = Class.forName("com.ladderstar.adq.QuestBoardMenuHandler");
                tracker = Class.forName("com.ladderstar.adq.DeliveryTracker"); model = Class.forName("com.ladderstar.adq.QuestModel");
                check(quests().size() == 1, "Expected the one actual saved offer; do not fabricate a replacement");
                quest = quests().getFirst(); questId = (UUID)value(quest, "getQuestId");
                check(value(quest, "getAcceptedBy") == null && !(boolean)value(quest, "isCompleted"), "Saved offer is not unclaimed");
                destination = (BlockPos)value(quest, "getEndingPos");
                report.addProperty("sourceQuestId", questId.toString()); report.addProperty("sourceDistance", (double)value(quest, "getDistance"));
                report.addProperty("sourceStart", value(quest, "getStartingPos").toString()); report.addProperty("sourceEnd", destination.toString());
                report.addProperty("sourceSchematic", String.valueOf(value(quest, "getSchematicName")));
                check((double)value(quest, "getDistance") > 4000, "Control lost its actual distant route");
                player = ProbePlayers.stationary(server, "CargoCheck", level.getSharedSpawnPos().above(2));
                emeralds = player.getInventory().countItem(Items.EMERALD); bread = player.getInventory().countItem(Items.BREAD);
                var channel = (EmbeddedChannel)player.connection.getConnection().channel(); drain(channel);
                board.getMethod("openBoard", Player.class).invoke(null, player); checkBoard(channel);
                board.getMethod("handleQuestAccept", ServerPlayer.class, UUID.class).invoke(null, player, UUID.randomUUID());
                check(value(quest, "getAcceptedBy") == null, "Unknown board ID accepted the real offer");
                board.getMethod("handleQuestAccept", ServerPlayer.class, UUID.class).invoke(null, player, questId);
                check(player.getUUID().equals(value(quest, "getAcceptedBy")) && (long)value(quest, "getAcceptedTime") > 0, "Native acceptance did not claim the saved offer");
                report.addProperty("nativeAcceptanceAndUnknownIdGuard", true);
                foreign = new ChunkPos(level.getSharedSpawnPos());
                check(!level.getForcedChunks().contains(foreign.toLong()), "Fixture foreign ticket is not independently owned");
                foreignOwned = true;
                level.setChunkForced(foreign.x, foreign.z, true);
                tracker.getMethod("tick", net.minecraft.server.level.ServerLevel.class).invoke(null, level);
                check(!(boolean)value(quest, "isCargoPickedUp"), "Faraway player unexpectedly picked up cargo");
                teleport((BlockPos)value(quest, "getStartingPos"), 1);
                phase = 1;
            } else if (phase == 1 && (boolean)value(quest, "isCargoPickedUp")) {
                cargoId = (UUID)value(quest, "getCargoEntityId"); check(cargoId != null, "Native pickup did not bind cargo");
                var container = ServerSubLevelContainer.getContainer(level);
                var found = container.getSubLevel(cargoId);
                if (!(found instanceof ServerSubLevel cargo)) return;
                int original = (int)value(quest, "getOriginalBlockCount");
                var count = tracker.getDeclaredMethod("countRemainingSublevelBlocks", net.minecraft.server.level.ServerLevel.class, ServerSubLevel.class, model);
                count.setAccessible(true); int remaining = (int)count.invoke(null, level, cargo, quest);
                report.addProperty("nativeOriginalCargoBlocks", original); report.addProperty("nativeRemainingCargoBlocks", remaining);
                check(original > 0 && remaining == original, "Fresh native cargo is not intact");
                check(destination.equals(value(quest, "getEndingPos")), "Native cargo replaced the real delivery destination");
                report.addProperty("nativePickupAndSableBody", cargoId.toString());
                report.addProperty("nativeCargoAdjustedStart", value(quest, "getStartingPos").toString());
                var handle = RigidBodyHandle.of(cargo); check(handle.isValid(), "Cargo has no native physics handle");
                handle.teleport(new org.joml.Vector3d(destination.getX() + .5, destination.getY() + 3, destination.getZ() + .5),
                        new org.joml.Quaterniond(cargo.logicalPose().orientation()));
                report.addProperty("fixtureTransport", "Actual native Sable handle teleport to original distant destination; no quest coordinate rewrite");
                teleport(destination, 5); phase = 2;
            } else if (phase == 2 && (boolean)value(quest, "isCompleted")) {
                check(quests().stream().noneMatch(q -> q == quest), "Native completed offer remains on the board");
                int gainedEmeralds = player.getInventory().countItem(Items.EMERALD) - emeralds;
                int gainedBread = player.getInventory().countItem(Items.BREAD) - bread;
                check(gainedEmeralds > 0 && gainedBread > 0, "Native completed cargo did not pay its real rewards");
                report.addProperty("emeraldsPaid", gainedEmeralds); report.addProperty("breadPaid", gainedBread);
                tracker.getMethod("tick", net.minecraft.server.level.ServerLevel.class).invoke(null, level);
                check(player.getInventory().countItem(Items.EMERALD) - emeralds == gainedEmeralds
                        && player.getInventory().countItem(Items.BREAD) - bread == gainedBread, "Repeated native tick duplicated payment");
                report.addProperty("nativeCompletionAndDuplicatePaymentGuard", true); phase = 3;
            } else if (phase == 3) {
                var cargo = ServerSubLevelContainer.getContainer(level).getSubLevel(cargoId);
                if (cargo != null && !cargo.isRemoved()) return;
                var forced = tracker.getDeclaredField("forcedChunksMap"); forced.setAccessible(true);
                check(!((Map<?, ?>)forced.get(null)).containsKey(questId), "Completed cargo retained its forced chunk");
                check(level.getForcedChunks().contains(foreign.toLong()), "Cargo cleanup removed the independent fixture ticket");
                report.addProperty("cargoRemovedForeignTicketPreserved", true);
                generator.getMethod("saveQuests").invoke(null);
                var file = generator.getDeclaredField("questFilePath"); file.setAccessible(true);
                var stored = JsonParser.parseString(Files.readString((Path)file.get(null))).getAsJsonArray();
                check(stored.isEmpty(), "Native save retained the completed offer");
                report.addProperty("nativeSavedBoardEmpty", true); finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }
    private void teleport(BlockPos position, int above) {
        player.teleportTo(position.getX() + .5, position.getY() + above, position.getZ() + .5);
        player.getAbilities().flying = true; server.overworld().getChunkSource().move(player);
    }
    private static void drain(EmbeddedChannel channel) { channel.runPendingTasks(); while (channel.readOutbound() != null) {} }
    @SuppressWarnings("unchecked") private void checkBoard(EmbeddedChannel channel) throws Exception {
        channel.runPendingTasks(); Object packet; boolean observed = false;
        while ((packet = channel.readOutbound()) != null) {
            if (!(packet instanceof ClientboundCustomPayloadPacket nativePacket)) continue;
            var payload = nativePacket.payload(); var type = payload.getClass();
            if (!type.getName().equals("com.ladderstar.adq.network.ClientboundQuestSyncPacket")) continue;
            if (!(boolean)type.getMethod("openScreen").invoke(payload)) continue;
            var codec = (StreamCodec<FriendlyByteBuf, Object>)type.getField("STREAM_CODEC").get(null);
            var buffer = new FriendlyByteBuf(Unpooled.buffer(1024, 1 << 20));
            try {
                codec.encode(buffer, payload); report.addProperty("nativeBoardPacketBytes", buffer.readableBytes());
                var decoded = codec.decode(buffer); var rows = (List<?>)type.getMethod("quests").invoke(decoded);
                check(rows.size() == 1 && questId.equals(value(rows.getFirst(), "getQuestId")), "Native board codec lost the saved offer");
                observed = true;
            } finally { buffer.release(); }
        }
        check(observed, "Native board did not emit its actual open-screen payload"); report.addProperty("nativeBoardPacketAndCodec", true);
    }
    private void finish(Throwable failure) {
        try {
            if (foreignOwned) server.overworld().setChunkForced(foreign.x, foreign.z, false);
            if (failure != null) { report.addProperty("error", failure.toString()); failure.printStackTrace(); }
            report.addProperty("complete", failure == null); report.addProperty("phase", phase); report.addProperty("ticks", ticks);
            Files.writeString(Path.of(System.getProperty("neighbors.probe.cargoOutput")), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (Exception error) { throw new IllegalStateException(error); }
        server.halt(false);
    }
}
