package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;

/** Native upstream semantics in a disposable book/world, never production player data. */
final class QuestBehaviorProbe {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    // Reflection keeps this optional probe out of the minimum runtime's linkage graph.
    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        var methods = Arrays.stream(type.getMethods()).filter(method -> method.getName().equals(name)
                && !method.isBridge() && method.getParameterCount() == args.length).filter(method -> {
            var parameters = method.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                var parameter = parameters[i];
                if (parameter == long.class) parameter = Long.class;
                if (parameter == boolean.class) parameter = Boolean.class;
                if (parameter == int.class) parameter = Integer.class;
                if (args[i] != null && !parameter.isInstance(args[i])) return false;
            }
            return true;
        }).toList();
        if (methods.size() != 1) throw new IllegalStateException("Ambiguous/missing native method " + type.getName() + "." + name);
        try { return methods.getFirst().invoke(target instanceof Class<?> ? null : target, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }
    private static CommonListenerCookie cookie(String name) {
        return new CommonListenerCookie(new GameProfile(UUID.nameUUIDFromBytes(("skyweave-probe/" + name).getBytes(StandardCharsets.UTF_8)), name),
                0, net.minecraft.server.level.ClientInformation.createDefault(), false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
    }
    private static Object object(Object file, String code) throws Exception {
        return Objects.requireNonNull(call(file, "getBase", Long.parseLong(code, 16)), "Missing native fixture object " + code);
    }
    private static Object data(ServerPlayer player) throws Exception {
        return call(Class.forName("dev.ftb.mods.ftbquests.quest.TeamData"), "get", player);
    }
    private static void submit(Object file, Object task, Object team, ServerPlayer player) throws Exception {
        call(Class.forName("dev.ftb.mods.ftbquests.util.PlayerInventorySummary"), "build", player);
        call(file, "withPlayerContext", player, (Runnable)() -> {
            try { call(task, "submitTask", team, player, ItemStack.EMPTY); }
            catch (Exception failure) { throw new IllegalStateException("Native task submission failed", failure); }
        });
    }
    private static int inventory(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static int drops(ServerPlayer player, Item item) {
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    static void run(MinecraftServer server, Path output) throws Exception {
        var file = ((Optional<?>)call(Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile"), "getInstance")).orElseThrow();
        var ownerCookie = cookie("QuestProbeA"); var memberCookie = cookie("QuestProbeB");
        var owner = ProbePlayers.login(server, ownerCookie);
        var member = ProbePlayers.login(server, memberCookie);
        var report = new JsonObject();
        var saved = server.getWorldPath(LevelResource.ROOT).resolve("quest-behavior-complete.json");
        var teamReward = object(file, "1300000000000003");
        var personalReward = object(file, "1300000000000004");
        var overflowReward = object(file, "1300000000000005");
        var manager = Class.forName("dev.ftb.mods.ftbteams.data.TeamManagerImpl").getField("INSTANCE").get(null);
        if (Files.isRegularFile(saved)) {
            report = JsonParser.parseString(Files.readString(saved)).getAsJsonObject();
            var team = data(owner);
            check(call(team, "getTeamId").toString().equals(report.get("partyId").getAsString()), "Party identity changed on restart");
            check(call(data(member), "getTeamId").equals(call(team, "getTeamId")), "Member lost saved party");
            for (String code : List.of("1100000000000002", "1100000000000003", "1100000000000004"))
                check((Boolean)call(team, "isCompleted", object(file, code)), "Completed quest lost on restart: " + code);
            check((Boolean)call(team, "isRewardClaimed", owner.getUUID(), teamReward), "Team claim lost on restart");
            check((Boolean)call(team, "isRewardClaimed", member.getUUID(), personalReward), "Personal claim lost on restart");
            check((Boolean)call(team, "isRewardClaimed", owner.getUUID(), overflowReward), "Overflow claim lost on restart");
            report.addProperty("reloadedPartyClaims", true);
        } else {
            var personal = data(member);
            member.getInventory().clearContent();
            member.getInventory().setItem(0, new ItemStack(Items.OAK_LOG));
            submit(file, object(file, "1200000000000004"), personal, member);
            check(inventory(member, Items.OAK_LOG) == 1, "Detection task consumed inventory");
            call(personal, "claimReward", member, personalReward, false);
            check(inventory(member, Items.BREAD) == 1, "Initial personal reward not delivered");
            var party = call(manager, "createParty", owner, "Quest probe");
            call(party, "invite", owner, List.of(member.getGameProfile()));
            call(party, "join", member);
            var team = data(owner);
            check(call(team, "getTeamId").equals(call(data(member), "getTeamId")), "Members do not share native data");
            check((Boolean)call(team, "isCompleted", object(file, "1100000000000004")), "Personal progress did not merge into party");
            check((Boolean)call(team, "isRewardClaimed", member.getUUID(), personalReward), "Join lost an existing personal claim");
            report.addProperty("partyId", call(team, "getTeamId").toString());
            report.addProperty("nativeJoinMerge", true);
            var advancement = Objects.requireNonNull(server.getAdvancements().get(ResourceLocation.parse("minecraft:story/upgrade_tools")));
            for (var criterion : advancement.value().criteria().keySet()) owner.getAdvancements().award(advancement, criterion);
            // AdvancementTask polls native advancement state; awarding is not an immediate task event.
            submit(file, object(file, "1200000000000002"), team, owner);
            check((Long)call(team, "getProgress", object(file, "1200000000000002")) == 1, "Early advancement was not recorded");
            check(!(Boolean)call(team, "isCompleted", object(file, "1100000000000002")), "Dependency was ignored");
            submit(file, object(file, "1200000000000001"), team, owner);
            check((Boolean)call(team, "isCompleted", object(file, "1100000000000002")), "Early progress did not complete after dependency");
            report.addProperty("earlyAdvancementAndDependency", true);
            server.getPlayerList().remove(member);
            owner.getInventory().clearContent();
            owner.getInventory().setItem(0, new ItemStack(Items.PAPER, 64));
            var mixed = object(file, "1200000000000003");
            submit(file, mixed, team, owner);
            check((Long)call(team, "getProgress", mixed) == 0 && inventory(owner, Items.PAPER) == 64, "Filter consumed ordinary paper");
            owner.getInventory().clearContent();
            owner.getInventory().setItem(0, new ItemStack(Items.BREAD, 4));
            owner.getInventory().setItem(1, new ItemStack(Items.POTATO, 4));
            submit(file, mixed, team, owner);
            check((Long)call(team, "getProgress", mixed) == 8 && inventory(owner, Items.BREAD) == 0 && inventory(owner, Items.POTATO) == 0,
                    "Combined consumptive task lost or duplicated inputs");
            call(team, "claimReward", owner, teamReward, false);
            check(inventory(owner, Items.ANDESITE) == 2 && (Boolean)call(team, "isRewardClaimed", memberCookie.gameProfile().getId(), teamReward),
                    "Team supply claim was not shared with offline member");
            call(team, "claimReward", owner, personalReward, false);
            check(inventory(owner, Items.BREAD) == 1, "Personal claim did not remain available to owner");
            member = ProbePlayers.login(server, memberCookie);
            check((Boolean)call(data(member), "isCompleted", object(file, "1100000000000003")), "Offline member lost completion");
            int memberBread = inventory(member, Items.BREAD), memberAndesite = inventory(member, Items.ANDESITE);
            call(team, "claimReward", member, personalReward, false);
            call(team, "claimReward", member, teamReward, false);
            check(inventory(member, Items.BREAD) == memberBread && inventory(member, Items.ANDESITE) == memberAndesite, "Duplicate claim granted items");
            call(party, "leave", member.getUUID());
            report.addProperty("personalClaimAfterLeave", (Boolean)call(data(member), "isRewardClaimed", member.getUUID(), personalReward));
            report.addProperty("personalProgressAfterLeave", (Long)call(data(member), "getProgress", object(file, "1200000000000004")));
            call(party, "invite", owner, List.of(member.getGameProfile()));
            call(party, "join", member);
            check((Boolean)call(data(member), "isRewardClaimed", member.getUUID(), teamReward), "Rejoining reset a shared claim");
            report.addProperty("offlineSharedProgressAndClaims", true);
            report.addProperty("personalAndTeamRewardScopes", true);
            submit(file, object(file, "1200000000000005"), team, owner);
            for (int slot = 0; slot < owner.getInventory().items.size(); slot++) owner.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            int before = inventory(owner, Items.DIAMOND) + drops(owner, Items.DIAMOND);
            call(team, "claimReward", owner, overflowReward, false);
            int after = inventory(owner, Items.DIAMOND) + drops(owner, Items.DIAMOND);
            check(after - before == 2, "Overflow reward was lost or multiplied");
            call(team, "claimReward", owner, overflowReward, false);
            check(inventory(owner, Items.DIAMOND) + drops(owner, Items.DIAMOND) == after, "Overflow reward could be claimed twice");
            report.addProperty("nativeOverflowConservesReward", true);
            call(team, "saveIfChanged");
            report.addProperty("complete", true);
            Files.writeString(saved, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
        server.getPlayerList().remove(member); server.getPlayerList().remove(owner);
        call(file, "saveNow");
        report.addProperty("evidence", "Native FTB server APIs/events and GameTest-style players; no real client network/GUI claim implied");
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
