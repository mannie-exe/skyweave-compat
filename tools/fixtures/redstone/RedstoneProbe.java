package linearreview;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.ggrgg.createredstonelinkgui.common.menu.RedstoneLinkMenu;
import com.ggrgg.createredstonelinkgui.common.network.*;
import com.simibubi.create.content.redstone.link.*;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import design.inherent.skyweavecompat.redstone.RedstoneEditEvent;

/** Native handlers and block states. The fake player does not establish client drag/drop UX. */
@Mod("linear_review_probe")
public final class RedstoneProbe {
    private int ticks;
    private final JsonObject report = new JsonObject();
    private final boolean baseline = Boolean.getBoolean("redstone.review.baseline");
    private final net.neoforged.neoforge.registries.DeferredBlock<SignalSampler> signalSampler;

    public RedstoneProbe(IEventBus bus) {
        var blocks = DeferredRegister.createBlocks("linear_review_probe");
        signalSampler = blocks.register("signal_sampler", () -> new SignalSampler(BlockBehaviour.Properties.of()));
        blocks.register(bus);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private static final class SignalSampler extends Block {
        private Runnable read;
        private SignalSampler(Properties properties) { super(properties); }
        @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            if (read != null) read.run();
            return 0;
        }
    }
    private void tick(ServerTickEvent.Post event) {
        ++ticks;
        if (ticks==20 && ModList.get().isLoaded("createredstonelinkgui")) {
            var level=event.getServer().overworld(); var pos=new BlockPos(0,100,0);
            level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(pos,block("create:redstone_link").defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP),3);
        }
        if(ticks==20 && ModList.get().isLoaded("sable") && ModList.get().isLoaded("createredstonelinkgui")) {
            try {Class.forName("linearreview.RedstoneShipProbe").getMethod("prepare",ServerLevel.class).invoke(null,event.getServer().overworld());}
            catch(Exception e) {throw new IllegalStateException(e);}
        }
        if(ticks!=40)return;
        try {
            var level = event.getServer().overworld();
            if (ModList.get().isLoaded("createredstonelinkgui")) Class.forName("linearreview.RedstoneGuiProbe").getMethod("check",ServerLevel.class,JsonObject.class,boolean.class).invoke(null,level,report,baseline);
            if (ModList.get().isLoaded("create_redstone_additions")) additions(level);
            if(!baseline && ModList.get().isLoaded("sable") && ModList.get().isLoaded("createredstonelinkgui"))
                Class.forName("linearreview.RedstoneShipProbe").getMethod("check",ServerLevel.class,JsonObject.class).invoke(null,level,report);
            report.addProperty("baseline", baseline);
            Files.writeString(Path.of(System.getProperty("linear.review.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of(System.getProperty("linear.review.output") + ".failure"), failure.toString()); } catch (Exception ignored) {}
        }
        event.getServer().halt(false);
    }
    private static Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)); }
    private void additions(ServerLevel level) {
        var pos = new BlockPos(80,100,0);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        var gate = block("create_redstone_additions:conjunctor");
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GateFixture"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(80,100,2); player.setYRot(0);
        var item = new ItemStack(gate.asItem(),2);
        player.setItemInHand(InteractionHand.MAIN_HAND,item);
        var context = new BlockPlaceContext(player,InteractionHand.MAIN_HAND,item,new BlockHitResult(pos.getCenter(),Direction.UP,pos.below(),false));
        var candidate = gate.getStateForPlacement(context);
        boolean readMutated = !level.getBlockState(pos).isAir();
        report.addProperty("conjunctorPlacementReadMutatedWorld",readMutated);
        require(!readMutated, "placement read changed the world");
        require(candidate.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis()==Direction.Axis.Z,"fixture input axis incorrect");
        require(candidate.getValues().entrySet().stream().anyMatch(e->e.getKey().getName().equals("power_type") && !e.getValue().equals(0)),"partial input missing from placement state");
        level.setBlock(pos,candidate,3);
        level.setBlock(pos.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        level.getBlockState(pos).tick(level,pos,level.random);
        require(level.getSignal(pos,Direction.NORTH)==15 || level.getSignal(pos,Direction.SOUTH)==15,"both AND inputs did not enable output");
        level.setBlock(pos.east(),Blocks.AIR.defaultBlockState(),3);
        level.getBlockState(pos).tick(level,pos,level.random);
        require(level.getSignal(pos,Direction.NORTH)==0 && level.getSignal(pos,Direction.SOUTH)==0,"partial AND input still outputs");
        report.addProperty("andTruthTableAndPartialDisplay",true);
        level.removeBlock(pos.below(),false);
        require(level.getBlockState(pos).isAir(),"AND gate survived support loss");
        report.addProperty("andSupportLoss",true);
        require(!block("create_redstone_additions:disjunctor").defaultBlockState().getValue(BlockStateProperties.POWERED),"OR default powered");
        report.addProperty("orDefaultOff",true);
        var crossing = block("create_redstone_additions:crossroad");
        require(crossing instanceof com.simibubi.create.content.equipment.wrench.IWrenchable,"crossroad missing native wrench interface");
        var cross = crossing.defaultBlockState();
        for(var property: cross.getProperties()) if(property.getName().equals("power")) cross = setInt(cross,property,15);
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(pos,cross,2);
        require(level.getDirectSignal(pos,Direction.NORTH)==14 || level.getDirectSignal(pos,Direction.SOUTH)==14,"crossroad direct signal attenuation missing");
        report.addProperty("crossroadStrongOutputAttenuates",true);
        var isolated=pos.south(20);
        level.setBlock(isolated.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(isolated.north(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(isolated.south(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(isolated,cross,2);
        level.getBlockState(isolated).tick(level,isolated,level.random);
        require(level.getSignal(isolated,Direction.NORTH)==0 && level.getSignal(isolated,Direction.SOUTH)==0,"crossroad retained own strong-block feedback");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:wrench"))));
        var facing=level.getBlockState(isolated).getValue(BlockStateProperties.HORIZONTAL_FACING);
        player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(isolated.getCenter(),Direction.UP,isolated,false)));
        require(level.getBlockState(isolated).getValue(BlockStateProperties.HORIZONTAL_FACING)!=facing,"native Create wrench did not rotate gate");
        report.addProperty("crossroadFeedbackClearsAndNativeWrenchRotates",true);
        var otherLevel = Objects.requireNonNull(level.getServer().getLevel(Level.NETHER));
        level.setBlock(isolated.north(), signalSampler.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockState sample = cross;
        int[] queried = {-1, -1};
        signalSampler.get().read = () -> {
            queried[0] = Math.max(sample.getDirectSignal(level, isolated, Direction.NORTH),
                    sample.getDirectSignal(level, isolated, Direction.SOUTH));
            queried[1] = Math.max(sample.getDirectSignal(otherLevel, isolated, Direction.NORTH),
                    sample.getDirectSignal(otherLevel, isolated, Direction.SOUTH));
        };
        try {
            cross.tick(level, isolated, level.random);
        } finally {
            signalSampler.get().read = null;
        }
        report.addProperty("crossroadSameWorldDuringRead", queried[0]);
        report.addProperty("crossroadOtherWorldDuringRead", queried[1]);
        require(queried[0] == 0, "active input read did not suppress its own strong signal: " + queried[0]);
        require(queried[1] == 14, "active input read suppressed another dimension: " + queried[1]);

        var copper=pos.east(10);
        player.setPos(copper.getX()+.5,100,copper.getZ()+2);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
        var hit=new BlockHitResult(copper.getCenter(),Direction.UP,copper,false);
        level.setBlock(copper,Blocks.EXPOSED_COPPER.defaultBlockState(),3);
        player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        require(level.getBlockState(copper).is(Blocks.COPPER_BLOCK),"native axe did not scrape exposed copper");
        var box=new AABB(copper).inflate(3);
        int yield=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).stream().filter(e->BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString().equals("create_redstone_additions:verdigris")).mapToInt(e->e.getItem().getCount()).sum();
        require(yield>=3 && yield<=6,"native successful scraping yield incorrect: "+yield);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BlockToolModificationEvent> cancel=e->e.setFinalState(null);
        NeoForge.EVENT_BUS.addListener(cancel);
        level.setBlock(copper,Blocks.EXPOSED_COPPER.defaultBlockState(),3);
        player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        require(level.getBlockState(copper).is(Blocks.EXPOSED_COPPER),"canceled scrape changed copper");
        int after=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).stream().filter(e->BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString().equals("create_redstone_additions:verdigris")).mapToInt(e->e.getItem().getCount()).sum();
        require(after==yield,"canceled scrape awarded verdigris");
        NeoForge.EVENT_BUS.unregister(cancel);
        report.addProperty("successfulNativeScrapeAndCanceledScrape",true);
        for(String name : new String[]{"diode","conjunctor","disjunctor","inverter","crossroad","golden_resistor","iron_resistor","ceramic_resistor","glass_resistor"}) {
            var tested=block("create_redstone_additions:"+name);
            var drops=Block.getDrops(tested.defaultBlockState(),level,pos,null,player,ItemStack.EMPTY);
            require(drops.size()==1 && drops.getFirst().is(tested.asItem()) && drops.getFirst().getCount()==1,"missing/duplicate native loot for "+name+": "+drops);
        }
        report.addProperty("nineNativeBlockDrops",true);
    }
    @SuppressWarnings("unchecked") private static BlockState setInt(BlockState state,net.minecraft.world.level.block.state.properties.Property<?> property,int value) { return state.setValue((net.minecraft.world.level.block.state.properties.Property<Integer>)property,value); }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
