package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Finite file-controlled native UI checks in an explicitly marked disposable client. */
final class ClientConfigurationProbe {
    private final Path root = FMLPaths.GAMEDIR.get();
    private boolean opened;
    private int ticks, sequence = -1;
    private String lastError;
    private Boolean keyAccepted;
    private long fieldEditNanos;
    private JsonObject nativeKey;
    private boolean wayfinderObserver;
    private volatile JsonObject serverProof;
    private net.minecraft.server.MinecraftServer firstServer;
    private java.util.UUID firstActor;
    private design.inherent.skyweavecompat.configuration.SettingsPolicy.Revision firstRevision;
    private design.inherent.reform.minecraft.client.ClientEditingWorkflow observedWorkflow;
    ClientConfigurationProbe() {
        if (!Files.isRegularFile(root.resolve("config-review-fixture.json"))) throw new IllegalStateException("Configuration probe requires its disposable-fixture marker");
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, true,
                net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre.class, event -> {
                    nativeKey = new JsonObject(); nativeKey.addProperty("key", event.getKeyCode());
                    nativeKey.addProperty("modifiers", event.getModifiers()); nativeKey.addProperty("canceled", event.isCanceled());
                    nativeKey.addProperty("controlDown", net.minecraft.client.gui.screens.Screen.hasControlDown());
                });
        if (Boolean.getBoolean("skyweave_compat.probe.configuration.journal")) new ClientJournalProbe();
    }
    private void open() {
        var mc = Minecraft.getInstance();
        mc.setScreen(openScreen(mc.screen));
    }
    static net.minecraft.client.gui.screens.Screen openScreen(net.minecraft.client.gui.screens.Screen parent) {
        var mod = ModList.get().getModContainerById("skyweave_compat").orElseThrow();
        return mod.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class)
                .orElseThrow().createScreen(mod, parent);
    }
    private void serverProof() throws Exception {
        var mc=Minecraft.getInstance(); var server=mc.getSingleplayerServer();
        if (server==null || mc.player==null) throw new IllegalStateException("Expected a running integrated fixture");
        var actor=mc.player.getUUID(); int requested=sequence;

        var previousServer=firstServer; var previousActor=firstActor; var previousRevision=firstRevision;
        server.submit(() -> {
            var player=server.getPlayerList().getPlayer(actor); var result=new JsonObject();
            try {
                var actual=design.inherent.skyweavecompat.wayfinder.Wayfinder.readPolicy(player.createCommandSourceStack());
                result.addProperty("sequence",requested);result.addProperty("serverIdentity",System.identityHashCode(server));
                result.addProperty("revisionIdentity",System.identityHashCode(actual.revision()));
                result.add("wayfinder",new GsonBuilder().create().toJsonTree(actual.value()));
                if (previousServer!=null && previousServer!=server) {
                    result.addProperty("previousStopped",previousServer.isStopped());result.addProperty("sameActorUuid",actor.equals(previousActor));
                    boolean refused=false;
                    try { design.inherent.skyweavecompat.wayfinder.Wayfinder.applyPolicy(player.createCommandSourceStack(),previousRevision,actual.value()); }
                    catch (IllegalStateException expected) {refused=true;}
                    result.addProperty("oldEffectiveRevisionRefused",refused);

                }
                if (previousServer==null) mc.execute(() -> {firstServer=server;firstActor=actor;firstRevision=actual.revision();});
            } catch (Exception failure) {result.addProperty("error",failure.toString());}
            serverProof=result;
        });
    }
    private void world(int slot) { world(slot, false); }
    private void world(int slot, boolean environment) {
        var mc=Minecraft.getInstance();
        if (slot<1 || slot>2 || mc.getSingleplayerServer()!=null || mc.level!=null) throw new IllegalStateException("Expected a disconnected marked client and slot1/2");
        String name="config-review-world-"+slot;
        var rules=new net.minecraft.world.level.GameRules();
        rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,null);
        rules.getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
        mc.createWorldOpenFlows().createFreshLevel(name,new net.minecraft.world.level.LevelSettings(name,
                net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,rules,
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42L,false,false),
                registries -> {
                    var flat = registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions();
                    if (!environment) return flat;
                    if (!Files.isRegularFile(root.resolve("environment-review-fixture.json")))
                        throw new IllegalStateException("Expected marked environment resource fixture");
                    var dimensions = new java.util.LinkedHashMap<>(flat.dimensions());
                    var type = registries.registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).getHolderOrThrow(
                            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE,
                                    net.minecraft.resources.ResourceLocation.parse("skyweave_compat:islands_v1")));
                    dimensions.put(net.minecraft.world.level.dimension.LevelStem.OVERWORLD,
                            new net.minecraft.world.level.dimension.LevelStem(type, flat.overworld()));
                    return new net.minecraft.world.level.levelgen.WorldDimensions(dimensions);
                },new TitleScreen());
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (++ticks % 10 != 0 || mc.getOverlay() != null) return;
        try {
            if (!opened && !Boolean.getBoolean("skyweave_compat.probe.branding") && !Boolean.getBoolean("skyweave_compat.probe.misc") && !Boolean.getBoolean("skyweave_compat.probe.resources") && !Boolean.getBoolean("skyweave_compat.probe.redstone") && !Boolean.getBoolean("skyweave_compat.probe.configuration.journal") && mc.screen instanceof TitleScreen) { opened = true; open(); }
            Path control = root.resolve("config-review-control.json");
            if (Files.isRegularFile(control) && Files.size(control) < 16384) {
                var command = JsonParser.parseString(Files.readString(control)).getAsJsonObject();
                int seq = command.get("sequence").getAsInt();
                if (seq != sequence) {
                    sequence = seq; lastError = null;
                    switch (command.get("operation").getAsString()) {
                        case "branding-proof" -> ClientMainMenuProbe.observe(root, mc, command);
                        case "branding-reopen" -> {
                            if (!Boolean.getBoolean("skyweave_compat.probe.branding") || mc.level != null)
                                throw new IllegalStateException("Expected a disconnected branding fixture");
                            mc.setScreen(new TitleScreen(true));
                        }
                        case "branding-reload" -> {
                            if (!Boolean.getBoolean("skyweave_compat.probe.branding") || mc.level != null)
                                throw new IllegalStateException("Expected a disconnected branding fixture");
                            mc.reloadResourcePacks();
                        }
                        case "open" -> open();
                        case "server-proof" -> serverProof();
                        case "world" -> world(command.get("slot").getAsInt());
                        case "environment-world" -> world(command.get("slot").getAsInt(), true);
                        case "environment-proof" -> ClientPresentationProbe.observe(root, mc);
                        case "hoofprint-proof" -> hoofprint(mc);
                        case "reform-receipt" -> reformReceipt(mc);
                        case "reform-batch-proof" -> reformBatchProof(mc);
                        case "reform-catalog-proof" -> reformCatalogProof(mc);
                        case "reform-task-proof" -> {
                            var workflow = reformWorkflow(mc);
                            var proof = new JsonObject();
                            proof.addProperty("conflict", workflow.conflictInspection().isPresent());
                            proof.addProperty("batch", workflow.batchInspection().isPresent());
                            proof.addProperty("import", workflow.importInspection().isPresent());
                            proof.addProperty("operation", workflow.operationInspection().isPresent());
                            proof.addProperty("root", System.identityHashCode(mc.screen));
                            ProbeStateFiles.write(root.resolve("reform-task-proof.json"), proof.toString());
                        }
                        case "reform-artifact-proof" -> {
                            var proof = new JsonObject();
                            for (String id : java.util.List.of("skyweave_compat", "reform")) {
                                var file = ModList.get().getModFileById(id).getFile().getFilePath();
                                long bytes = Files.size(file);
                                if (!Files.isRegularFile(file) || bytes > 32L * 1024 * 1024)
                                    throw new IllegalStateException("Expected a bounded actual mod archive: " + id);
                                var row = new JsonObject(); row.addProperty("path", file.toString()); row.addProperty("bytes", bytes);
                                row.addProperty("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(file))));
                                Class<?> type = id.equals("reform") ? design.inherent.reform.minecraft.client.ClientEditingWorkflow.class
                                        : design.inherent.skyweavecompat.SkyweaveCompat.class;
                                row.addProperty("classOrigin", type.getProtectionDomain().getCodeSource().getLocation().toExternalForm());
                                proof.add(id, row);
                            }
                            ProbeStateFiles.write(root.resolve("reform-artifact-proof.json"), proof.toString());
                        }
                        case "reform-home" -> reformWorkflow(mc).showHome();
                        case "reform-show-placement" -> reformWorkflow(mc).showPlacement(
                                design.inherent.reform.definition.DocumentId.parse(command.get("id").getAsString()));
                        case "reform-reveal-operation" -> revealOperation(mc, command);
                        case "reform-focus-field" -> {
                            var target = command.has("document") ? reformWorkflow(mc).editor(
                                    design.inherent.reform.definition.DocumentId.parse(command.get("document").getAsString())).form() : reformForm(mc);
                            if (!target.focusField(reformField(command)))
                                throw new IllegalStateException("The actual selected field is not mounted");
                        }
                        case "reform-field-click" -> {
                            var widget = reformForm(mc).fieldControl(reformField(command)).orElseThrow();
                            double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
                            if (!visibleTarget(mc.screen, widget, x, y)) throw new IllegalStateException("Actual field is outside its native viewport");
                            try { if (!mc.screen.mouseClicked(x, y, 0)) throw new IllegalStateException("Native field click refused"); }
                            finally { if (mc.screen != null) mc.screen.mouseReleased(x, y, 0); }
                        }
                        case "reform-information-proof" -> reformInformationProof(mc);
                        case "reform-lifetime-proof" -> {
                            if (observedWorkflow == null) throw new IllegalStateException("No actual workspace was observed");
                            ProbeStateFiles.write(root.resolve("reform-lifetime-proof.json"), "{\"open\":" + observedWorkflow.isOpen() + "}");
                        }
                        case "reform-capture" -> {
                            String name = command.get("name").getAsString();
                            if (!name.matches("reform-[a-z0-9-]+\\.png") || Files.exists(root.resolve("screenshots").resolve(name)))
                                throw new IllegalArgumentException("Choose a fresh owned re:form screenshot name");
                            net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {});
                        }
                        case "native-preferences-proof" -> nativePreferencesProof();
                        case "native-owner-readers" -> nativeOwnerReaders();
                        case "reform-editor-proof" -> reformEditorProof(mc);
                        case "reform-layout-proof" -> ProbeStateFiles.write(root.resolve("reform-layout-proof.json"),
                                ClientLayoutProof.inspect(mc.screen).toString());
                        case "reform-field-proof" -> reformFieldProof(mc, command);
                        case "reform-reference-proof" -> reformReferenceProof(mc);
                        case "modifier-proof" -> ClientModifierProbe.observe(root, mc);
                        case "modifier-effortless" -> ClientModifierProbe.effortless(root, mc);
                        case "effortless-build" -> ClientEffortlessBuildProbe.act(root, mc, command.get("action").getAsString(),
                                command.has("configured") && command.get("configured").getAsBoolean());
                        case "powergrid-setup" -> ClientPowerGridInputProbe.setup(root, mc);
                        case "powergrid-open" -> ClientPowerGridInputProbe.open(root, mc);
                        case "powergrid-editor" -> ClientPowerGridInputProbe.editor(root, mc);
                        case "powergrid-input-proof" -> ClientPowerGridInputProbe.observe(root, mc);
                        case "powergrid-seed-wire" -> ClientPowerGridInputProbe.seedWire(root, mc);
                        case "powergrid-wire-proof" -> ClientPowerGridInputProbe.wire(root, mc);
                        case "powergrid-held" -> ClientPowerGridInputProbe.held(root, mc, command.get("down").getAsBoolean());
                        case "powergrid-server-proof" -> ClientPowerGridInputProbe.serverState(root, mc);
                        case "modifier-persistence" -> ClientModifierProbe.persistence(root, mc, command.get("save").getAsBoolean());
                        case "modifier-reload" -> {
                            if (!Files.isRegularFile(root.resolve("modifier-review-fixture.json")))
                                throw new IllegalStateException("Expected a disposable modifier fixture");
                            mc.reloadResourcePacks();
                        }
                        case "reference-command" -> {
                            if (!Files.isRegularFile(root.resolve("reference-review-fixture.json")) || mc.player == null
                                    || mc.getSingleplayerServer() == null)
                                throw new IllegalStateException("Expected a marked integrated reference fixture");
                            String action = command.get("action").getAsString();
                            String nativeCommand = switch (action) {
                                case "reload" -> "reload";
                                case "enable" -> "datapack enable \"file/reference-review\"";
                                default -> throw new IllegalArgumentException("Unknown reference fixture command");
                            };
                            mc.player.connection.sendCommand(nativeCommand);
                        }
                        case "reference-tag-proof" -> {
                            if (!Files.isRegularFile(root.resolve("reference-review-fixture.json")) || mc.level == null)
                                throw new IllegalStateException("Expected a marked joined reference fixture");
                            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                                    net.minecraft.resources.ResourceLocation.parse("skyweave_review:empty"));
                            var values = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTag(tag);
                            var proof = new JsonObject(); proof.addProperty("exists", values.isPresent());
                            proof.addProperty("matches", values.map(net.minecraft.core.HolderSet.Named::size).orElse(-1));
                            ProbeStateFiles.write(root.resolve("reference-tag-proof.json"), proof.toString());
                        }
                        case "reform-select-section" -> {
                            if (!reformForm(mc).selectSection(reformField(command)))
                                throw new IllegalStateException("The actual selected section is unavailable");
                        }
                        case "reform-operation-proof" -> {
                            design.inherent.reform.operation.OperationSnapshot<?> current;
                            if (mc.screen instanceof design.inherent.reform.minecraft.client.OperationScreen<?> screen)
                                current = screen.observedOperation().orElseThrow();
                            else current = reformWorkflow(mc).operationInspection().orElseThrow().observedOperation().orElseThrow();
                            var result = new JsonObject(); result.addProperty("operation", current.operation().toString());
                            result.addProperty("retainedRoot", !(mc.screen instanceof design.inherent.reform.minecraft.client.OperationScreen<?>));
                            result.addProperty("state", current.state().name()); result.addProperty("terminal", current.terminal());
                            result.addProperty("report", current.report().toString());
                            result.addProperty("hasResult", current.result().isPresent());
                            current.result().ifPresent(value -> {
                                if (value instanceof design.inherent.reform.minecraft.client.OperationScreen.Outcome outcome) {
                                    var projection = new JsonObject(); projection.addProperty("summary", outcome.summary().getString());
                                    var rows = new JsonArray(); outcome.details().forEach(detail -> {
                                        var row = new JsonObject(); row.addProperty("label", detail.label().getString());
                                        row.addProperty("value", detail.value().getString()); rows.add(row);
                                    }); projection.add("details", rows); result.add("result", projection);
                                } else result.add("result", new GsonBuilder().create().toJsonTree(value));
                            });
                            ProbeStateFiles.write(root.resolve("reform-operation-proof.json"), result.toString());
                        }
                        case "journal-shortcut" -> {
                            if (!OptionalIntegrations.enabled(Feature.QUESTLOG_INTERFACE)) throw new IllegalStateException("Expected the admitted journal owner");
                            var action = switch (command.get("action").getAsString()) {
                                case "images" -> design.inherent.skyweavecompat.questlog.client.JournalPreferences.toggleImages();
                                case "symbols" -> design.inherent.skyweavecompat.questlog.client.JournalPreferences.toggleSymbols();
                                case "notifications" -> design.inherent.skyweavecompat.questlog.client.JournalPreferences.cycleNotifications();
                                default -> throw new IllegalArgumentException("Unknown journal shortcut");
                            };
                            action.whenCompleteAsync((ignored, failure) -> {
                                var result = new JsonObject(); result.addProperty("complete", true);
                                if (failure != null) result.addProperty("error", failure.toString());
                                try { ProbeStateFiles.write(root.resolve("journal-shortcut.json"), result.toString()); }
                                catch (java.io.IOException failed) { throw new java.io.UncheckedIOException(failed); }
                            }, mc);
                        }
                        case "hoofprint" -> {
                            if (!OptionalIntegrations.enabled(Feature.HOOFPRINT_ZOOM) || mc.player == null)
                                throw new IllegalStateException("Expected an admitted Hoofprint connection");
                            mc.setScreen((net.minecraft.client.gui.screens.Screen) Class.forName(
                                    "garden.hestia.hoofprint.HoofprintScreen").getConstructor().newInstance());
                        }
                        case "load-world" -> {
                            int slot = command.get("slot").getAsInt();
                            if (slot < 1 || slot > 2 || mc.level != null || mc.getSingleplayerServer() != null
                                    || !Files.isRegularFile(root.resolve("saves/config-review-world-" + slot + "/level.dat")))
                                throw new IllegalStateException("Expected a stopped saved review world1/2");
                            mc.createWorldOpenFlows().openWorld("config-review-world-" + slot, () -> mc.setScreen(new TitleScreen()));
                        }
                        case "disconnect" -> { if (mc.level!=null) mc.level.disconnect(); mc.disconnect(new TitleScreen()); mc.setScreen(new TitleScreen()); }

                        case "wayfinder" -> {
                            if(!wayfinderObserver || mc.player==null
                                    || !Files.isRegularFile(root.resolve("misc-review-fixture.json")))
                                throw new IllegalStateException("Expected a connected marked Wayfinder fixture");
                            WayfinderFixture.apply(mc,command);
                        }
                        case "connect" -> {
                            String address=command.get("address").getAsString();
                            if(!Files.isRegularFile(root.resolve("misc-review-fixture.json"))
                                    || !address.matches("127\\.0\\.0\\.1:[0-9]{1,5}"))
                                throw new IllegalArgumentException("Expected a marked loopback fixture");
                            var target=net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address);
                            if(target.getPort()<1 || target.getPort()>65535)throw new IllegalArgumentException("Invalid loopback port");
                            if(!wayfinderObserver){WayfinderFixture.register();wayfinderObserver=true;}
                            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(),mc,target,
                                    new net.minecraft.client.multiplayer.ServerData("Wayfinder loopback",address,
                                            net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
                        }
                        case "click" -> {
                            var found = widgets(mc.screen).stream().filter(w -> w.getMessage().getString().equals(command.get("label").getAsString())).toList();
                            int index = command.has("index") ? command.get("index").getAsInt() : 0;
                            var widget = found.get(index);
                            double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
                            if (!visibleTarget(mc.screen,widget,x,y)) throw new IllegalStateException("Target outside current viewport; scroll first: " + command.get("label"));
                            boolean accepted;
                            try { accepted = mc.screen.mouseClicked(x,y,0); }
                            finally { if (mc.screen != null) mc.screen.mouseReleased(x,y,0); }
                            if (!accepted) throw new IllegalStateException("Native click was refused: " + command.get("label"));
                        }
                        case "list-click" -> {
                            var lists = mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.ObjectSelectionList<?>).toList();
                            var list = (net.minecraft.client.gui.components.ObjectSelectionList<?>) lists.getFirst();
                            int index = command.get("index").getAsInt();
                            if (index < 0 || index >= list.children().size()) throw new IllegalArgumentException("Choice index outside current view");
                            var top = net.minecraft.client.gui.components.AbstractSelectionList.class.getDeclaredMethod("getRowTop", int.class); top.setAccessible(true);
                            var bottom = net.minecraft.client.gui.components.AbstractSelectionList.class.getDeclaredMethod("getRowBottom", int.class); bottom.setAccessible(true);
                            int y = ((int) top.invoke(list, index) + (int) bottom.invoke(list, index)) / 2;
                            double x = list.getX() + list.getWidth() / 2d;
                            boolean accepted;
                            try { accepted = mc.screen.mouseClicked(x,y,0); }
                            finally { if (mc.screen != null) mc.screen.mouseReleased(x,y,0); }
                            if (!accepted) throw new IllegalStateException("Native list click refused");
                        }
                        case "field" -> {
                            var fields = widgets(mc.screen).stream().filter(w -> w instanceof net.minecraft.client.gui.components.EditBox).toList();
                            long start = System.nanoTime();
                            ((net.minecraft.client.gui.components.EditBox) fields.get(command.get("index").getAsInt())).setValue(command.get("value").getAsString());
                            fieldEditNanos = System.nanoTime() - start;
                        }
                        case "key" -> keyAccepted = mc.screen.keyPressed(command.get("key").getAsInt(), 0, command.has("modifiers") ? command.get("modifiers").getAsInt() : 0);
                        case "scroll" -> mc.screen.mouseScrolled(command.get("x").getAsDouble(), command.get("y").getAsDouble(), 0, command.get("amount").getAsDouble());
                        case "drag" -> {
                            double x = command.get("x").getAsDouble(), y = command.get("y").getAsDouble();
                            double end = command.get("endY").getAsDouble();
                            if (!mc.screen.mouseClicked(x, y, 0)) throw new IllegalStateException("Native drag press refused");
                            mc.screen.mouseDragged(x, end, 0, 0, end - y); mc.screen.mouseReleased(x, end, 0);
                        }
                        case "language" -> {
                            String language = command.get("value").getAsString();
                            mc.options.languageCode = language; mc.getLanguageManager().setSelected(language); mc.reloadResourcePacks();
                        }
                        case "scale" -> { mc.options.guiScale().set(command.get("value").getAsInt()); mc.resizeDisplay(); }
                        case "resize" -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), command.get("width").getAsInt(), command.get("height").getAsInt());
                        case "text" -> { for (char character : command.get("value").getAsString().toCharArray()) mc.screen.charTyped(character, 0); }
                        case "snapshot" -> {}
                        case "focus-window" -> org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                        case "narration" -> narration(mc);
                        case "journal-preferences-proof" -> journalPreferences();
                        case "journal-settings" -> {
                            if (!OptionalIntegrations.enabled(Feature.QUESTLOG_INTERFACE)) throw new IllegalStateException("Expected the admitted journal owner");
                            mc.setScreen(new design.inherent.skyweavecompat.questlog.client.JournalSettingsScreen(mc.screen,
                                    org.infernalstudios.questlog.client.gui.QuestlogGuiSet.DEFAULT));
                        }
                        case "quit" -> mc.stop();
                        default -> throw new IllegalArgumentException("Unknown configuration fixture action");
                    }
                }
            }
            write(lastError);
        } catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Configuration fixture action failed", failure);
            lastError = failure.toString(); write(lastError);
        }
    }
    private void journalPreferences() throws Exception {
        if (!OptionalIntegrations.enabled(Feature.QUESTLOG_INTERFACE)) throw new IllegalStateException("Expected the admitted journal owner");
        var result=new JsonObject();
        result.addProperty("images",design.inherent.skyweavecompat.questlog.client.JournalPreferences.ITEM_IMAGES.get());
        result.addProperty("symbols",design.inherent.skyweavecompat.questlog.client.JournalPreferences.CONDITION_SYMBOLS.get());
        result.addProperty("notifications",design.inherent.skyweavecompat.questlog.client.JournalPreferences.NOTIFICATIONS.get().name());
        ProbeStateFiles.write(root.resolve("journal-preferences-observation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
    private void narration(Minecraft mc) throws Exception {
        if (mc.screen == null) throw new IllegalStateException("Expected a configuration screen");
        var result = new JsonArray();
        class Output implements net.minecraft.client.gui.narration.NarrationElementOutput {
            private final int depth;
            Output(int depth) { this.depth = depth; }
            @Override public void add(net.minecraft.client.gui.narration.NarratedElementType type,
                    net.minecraft.client.gui.narration.NarrationThunk<?> value) {
                value.getText(text -> { var entry = new JsonObject(); entry.addProperty("depth",depth);
                    entry.addProperty("type",type.name()); entry.addProperty("text",text); result.add(entry); });
            }
            @Override public net.minecraft.client.gui.narration.NarrationElementOutput nest() { return new Output(depth + 1); }
        }
        var focused = mc.screen.getFocused();
        if (!(focused instanceof net.minecraft.client.gui.narration.NarratableEntry entry)) throw new IllegalStateException("Expected a focused narratable control");
        entry.updateNarration(new Output(0));
        ProbeStateFiles.write(root.resolve("configuration-narration.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
    private void hoofprint(Minecraft mc) throws Exception {
        if (!OptionalIntegrations.enabled(Feature.HOOFPRINT_ZOOM) || mc.screen==null
                || !mc.screen.getClass().getName().equals("garden.hestia.hoofprint.HoofprintScreen"))
            throw new IllegalStateException("Expected the admitted real Hoofprint screen");
        var type=mc.screen.getClass();var proof=new JsonObject();
        var scale=type.getDeclaredMethod("getScaleFactor");scale.setAccessible(true);
        proof.addProperty("nativeScaleFactor",((Number)scale.invoke(mc.screen)).doubleValue());
        for (String name:java.util.List.of("centreX","centreZ","guiScale","dim","skyweave$scale")) {
            var field=type.getDeclaredField(name);field.setAccessible(true);var value=field.get(mc.screen);
            if (value instanceof Number number)proof.addProperty(name,number);else proof.addProperty(name,String.valueOf(value));
        }
        for (String name:java.util.List.of("screenXToWorldX","screenYToWorldZ")) {
            var method=type.getDeclaredMethod(name,double.class);method.setAccessible(true);
            proof.addProperty(name,((Number)method.invoke(mc.screen,name.contains("WorldX")?100d:120d)).doubleValue());
        }
        proof.addProperty("windowGuiScale",mc.getWindow().getGuiScale());
        proof.add("preferences",new GsonBuilder().create().toJsonTree(
                design.inherent.skyweavecompat.client.HoofprintPreferences.current()));
        proof.addProperty("connectionIdentity",System.identityHashCode(mc.getConnection()));
        proof.addProperty("screenIdentity",System.identityHashCode(mc.screen));
        ProbeStateFiles.write(root.resolve("hoofprint-observation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
    }
    static design.inherent.reform.minecraft.client.ClientEditingWorkflow reformWorkflow(Minecraft mc) throws Exception {
        if (!mc.screen.getClass().getName().equals("design.inherent.reform.minecraft.client.ClientEditingWorkflow$Overview"))
            throw new IllegalStateException("Expected the actual shared workspace");
        var owner = mc.screen.getClass().getDeclaredField("this$0"); owner.setAccessible(true);
        return (design.inherent.reform.minecraft.client.ClientEditingWorkflow) owner.get(mc.screen);
    }
    static design.inherent.reform.minecraft.client.SettingsForm<?> reformForm(Minecraft mc) throws Exception {
        if (mc.screen instanceof design.inherent.reform.minecraft.client.SettingsForm<?> form) return form;
        var workflow = reformWorkflow(mc);
        var field = mc.screen.getClass().getDeclaredField("actionRoute"); field.setAccessible(true);
        var route = (design.inherent.reform.definition.DocumentId) field.get(mc.screen);
        if (route == null || workflow.documents().stream().noneMatch(state -> state.document().equals(route) && state.opened()))
            throw new IllegalStateException("The selected actual editor has not opened");
        return workflow.editor(route).form();
    }
    /** Fixture-only UI traversal: select the declared native group and focus its real row button. */
    private void revealOperation(Minecraft mc, JsonObject command) throws Exception {
        var workflow = reformWorkflow(mc);
        var layoutField = workflow.getClass().getDeclaredField("layout"); layoutField.setAccessible(true);
        var layout = (design.inherent.reform.minecraft.client.ClientWorkflowLayout) layoutField.get(workflow);
        var id = design.inherent.reform.definition.DocumentId.parse(command.get("id").getAsString());
        var group = layout.groups().stream().filter(value -> value.actions().stream().anyMatch(action -> action.id().equals(id)))
                .findFirst().orElseThrow();
        var category = group.path().getLast().id();
        var declared = mc.screen.getClass().getDeclaredMethod("navigationEntries"); declared.setAccessible(true);
        for (var entry : (java.util.List<?>) declared.invoke(mc.screen)) {
            var keyMethod = entry.getClass().getDeclaredMethod("key"); keyMethod.setAccessible(true);
            var key = keyMethod.invoke(entry);
            var identity = key.getClass().getDeclaredMethod("placement"); identity.setAccessible(true);
            if (!category.equals(identity.invoke(key))) continue;
            var select = entry.getClass().getDeclaredMethod("select"); select.setAccessible(true);
            ((Runnable) select.invoke(entry)).run();
            break;
        }
        var panelField = mc.screen.getClass().getDeclaredField("panel"); panelField.setAccessible(true);
        var panel = panelField.get(mc.screen);
        var rowsField = panel.getClass().getDeclaredField("rows"); rowsField.setAccessible(true);
        for (var row : (java.util.List<?>) rowsField.get(panel)) {
            var keyMethod = row.getClass().getDeclaredMethod("key"); keyMethod.setAccessible(true);
            if (!id.toString().equals(keyMethod.invoke(row))) continue;
            var controls = row.getClass().getDeclaredMethod("controls"); controls.setAccessible(true);
            var buttons = ((java.util.List<?>) controls.invoke(row)).stream()
                    .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).toList();
            var button = command.has("again") && command.get("again").getAsBoolean()
                    ? buttons.stream().filter(value -> value.getMessage().getString().equals("Run again…")).findFirst().orElseThrow()
                    : buttons.getFirst();
            if (!focus(mc.screen, button)) throw new IllegalStateException("Operation button is not in the native focus tree");
            return;
        }
        throw new IllegalStateException("The declared operation row is not mounted");
    }
    private static boolean focus(net.minecraft.client.gui.components.events.ContainerEventHandler parent,
            net.minecraft.client.gui.components.events.GuiEventListener target) {
        for (var child : parent.children()) {
            if (child == target) {
                parent.setFocused(child);
                return true;
            }
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested && widgets(nested).contains(target)) {
                parent.setFocused(child);
                return focus(nested, target);
            }
        }
        return false;
    }
    private static design.inherent.reform.operation.OperationHandle<?> reformCommit(Minecraft mc) throws Exception {
        Object form = reformForm(mc);
        if (form instanceof design.inherent.reform.minecraft.client.SettingsScreen<?>) {
            var retained = form.getClass().getDeclaredField("view"); retained.setAccessible(true); form = retained.get(form);
        }
        var field = form.getClass().getDeclaredField("lastCommit"); field.setAccessible(true);
        return (design.inherent.reform.operation.OperationHandle<?>) field.get(form);
    }
    private void reformReceipt(Minecraft mc) throws Exception {
        var operation = reformCommit(mc);
        if (operation == null || !(operation.snapshot().result().orElse(null)
                instanceof design.inherent.reform.owner.EditorActor.Commit<?> commit))
            throw new IllegalStateException("Expected a settled re:form settings commit");
        var receipt = commit.receipt().orElseThrow();
        var evidence = new JsonObject();
        evidence.addProperty("persistence", receipt.persistence().name());
        evidence.addProperty("application", receipt.application().name());
        evidence.addProperty("savedRevision", receipt.savedRevision().orElseThrow());
        evidence.addProperty("source", receipt.observation().orElseThrow().provenance().location().orElseThrow());
        ProbeStateFiles.write(root.resolve("reform-receipt.json"), evidence.toString());
    }
    private void reformCatalogProof(Minecraft mc) throws Exception {
        if (!mc.screen.getClass().getName().equals("design.inherent.reform.minecraft.client.ClientEditingWorkflow$Overview"))
            throw new IllegalStateException("Expected the real shared workflow overview");
        var owner = mc.screen.getClass().getDeclaredField("this$0"); owner.setAccessible(true);
        var workflow = (design.inherent.reform.minecraft.client.ClientEditingWorkflow) owner.get(mc.screen);
        observedWorkflow = workflow;
        var layout = workflow.getClass().getDeclaredField("layout"); layout.setAccessible(true);
        var declared = (design.inherent.reform.minecraft.client.ClientWorkflowLayout) layout.get(workflow);
        var proof = new JsonObject();
        var presentationField = workflow.getClass().getDeclaredField("presentation"); presentationField.setAccessible(true);
        var presentation = (design.inherent.reform.minecraft.client.ClientWorkspacePresentation) presentationField.get(workflow);
        proof.addProperty("workspace", presentation.identity().id().toString());
        proof.addProperty("workspaceTitle", presentation.identity().title().getString());
        proof.addProperty("workspaceContext", presentation.identity().subtitle().getString());
        proof.addProperty("homeSelected", workflow.navigation().current().orElse(null)
                instanceof design.inherent.reform.minecraft.client.ClientDestination.Home);
        var featured = new JsonArray();
        presentation.home().orElseThrow().featured().forEach(id -> featured.add(id.toString()));
        proof.add("homeLinks", featured);
        proof.addProperty("groups", declared.groups().size());
        proof.addProperty("placements", declared.groups().stream().mapToLong(group -> group.placements().size()).sum());
        proof.addProperty("documents", workflow.documents().size());
        var placements = new JsonArray();
        declared.groups().forEach(group -> group.placements().forEach(placement -> placements.add(placement.id().toString())));
        proof.add("placementIds", placements);
        var composition = new JsonArray();
        declared.groups().forEach(group -> group.placements().forEach(placement -> {
            var row = new JsonObject();
            row.addProperty("placement", placement.id().toString());
            row.addProperty("route", placement.route().toString());
            row.addProperty("field", placement.field().map(Object::toString).orElse(""));
            row.addProperty("placementHelp", placement.help().getString());
            placement.section().ifPresent(section -> {
                row.addProperty("section", section.id().toString());
                row.addProperty("title", section.title().getString());
                row.addProperty("eyebrow", section.eyebrow().getString());
                row.addProperty("help", section.help().getString());
            });
            composition.add(row);
        }));
        proof.add("composition", composition);
        var actions = new JsonArray();
        declared.groups().forEach(group -> group.actions().forEach(action -> {
            var row = new JsonObject(); row.addProperty("id", action.id().toString());
            row.addProperty("title", action.title().getString()); actions.add(row);
        })); proof.add("actions", actions);
        var expected = new JsonArray();
        if (presentation.identity().id().path().equals("workspace/running_server")) {
            expected.add("skyweave_compat:wayfinder"); expected.add("skyweave_compat:search");
        } else {
            design.inherent.skyweavecompat.configuration.SettingsCatalog.FILES.forEach(entry -> expected.add(entry.id()));
            expected.add("skyweave_compat:controls");
        }
        proof.add("expectedRoutes", expected);
        var documents = new JsonArray();
        workflow.documents().forEach(state -> {
            var row = new JsonObject(); row.addProperty("route", state.document().toString());
            row.addProperty("owner", state.ownerDocument().toString()); row.addProperty("available", state.available());
            row.addProperty("opened", state.opened()); row.addProperty("summary", state.summary().getString()); documents.add(row);
        });
        proof.add("owners", documents);
        ProbeStateFiles.write(root.resolve("reform-catalog-proof.json"), new GsonBuilder().setPrettyPrinting().create().toJson(proof));
    }
    private void reformInformationProof(Minecraft mc) throws Exception {
        reformWorkflow(mc);
        var field = mc.screen.getClass().getDeclaredField("information"); field.setAccessible(true);
        var holder = field.get(mc.screen);
        var observation = holder.getClass().getDeclaredMethod("observation"); observation.setAccessible(true);
        var value = (java.util.Optional<?>) observation.invoke(holder);
        var result = new JsonObject(); result.addProperty("available", value.isPresent());
        if (value.orElse(null) instanceof design.inherent.reform.operation.FieldInformation captured) {
            result.addProperty("document", captured.target().document().toString());
            result.addProperty("field", captured.path().toString());
            result.addProperty("source", captured.source().location().orElse(""));
            var metadata = holder.getClass().getDeclaredField("metadata"); metadata.setAccessible(true);
            var details = (design.inherent.reform.minecraft.client.FormInformation.Subject) metadata.get(holder);
            result.addProperty("purpose", details.purpose().getString());
            result.addProperty("key", details.key().orElse(""));
            var facts = new JsonArray(); details.facts().forEach(fact -> {
                var row = new JsonObject(); row.addProperty("label", fact.label().getString());
                row.addProperty("value", fact.value().getString()); facts.add(row);
            }); result.add("facts", facts);
            result.addProperty("related", details.related().size());
        }
        ProbeStateFiles.write(root.resolve("reform-information-proof.json"), result.toString());
    }
    private void reformBatchProof(Minecraft mc) throws Exception {
        design.inherent.reform.operation.DocumentBatch.Review review;
        if (mc.screen instanceof design.inherent.reform.minecraft.client.BatchReviewScreen screen)
            review = screen.reviewedInput().orElseThrow();
        else review = reformWorkflow(mc).batchInspection().orElseThrow().reviewedInput().orElseThrow();
        var evidence = new JsonObject(); var entries = new JsonObject();
        review.entries().forEach(entry -> entries.addProperty(entry.document().toString(), entry.intent().name()));
        evidence.add("intents", entries);
        evidence.addProperty("valid", review.valid());
        ProbeStateFiles.write(root.resolve("reform-batch-proof.json"), evidence.toString());
    }

    private void nativePreferencesProof() throws Exception {
        var evidence = new JsonObject();
        var client = Class.forName("com.ggrgg.createredstonelinkgui.ClientConfig");
        var common = Class.forName("com.ggrgg.createredstonelinkgui.Config");
        var click = (net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>) client.getField("CLICK_MODE").get(null);
        var range = (net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>) common.getField("MOVE_RANGE").get(null);
        evidence.addProperty("clientClickMode", String.valueOf(click.get()));
        evidence.addProperty("commonMoveRange", ((Number) range.get()).intValue());
        ProbeStateFiles.write(root.resolve("native-preferences-proof.json"), evidence.toString());
    }

    private void nativeOwnerReaders() throws java.io.IOException {
        var result = new JsonObject();
        var keys = new JsonObject();
        for (var key : Minecraft.getInstance().options.keyMappings) if (key.getName().startsWith("key.skyweave_compat."))
            keys.addProperty(key.getName(), key.saveString());
        result.add("skyweave_compat:controls", keys);
        for (var kind : java.util.List.of(net.neoforged.fml.config.ModConfig.Type.CLIENT, net.neoforged.fml.config.ModConfig.Type.COMMON)) {
            for (var config : net.neoforged.fml.config.ModConfigs.getConfigSet(kind)) {
                var entry = design.inherent.skyweavecompat.configuration.SettingsCatalog.FILES.stream()
                        .filter(file -> design.inherent.skyweavecompat.configuration.ConfigLocations.registeredName(file.filename()).equals(config.getFileName()))
                        .findFirst();
                if (entry.isEmpty() || !(config.getSpec() instanceof net.neoforged.neoforge.common.ModConfigSpec spec)) continue;
                var fields = new JsonObject();
                design.inherent.skyweavecompat.configuration.NativeFields.collect(spec).forEach(field ->
                        fields.add(field.key(), new GsonBuilder().create().toJsonTree(field.binding().get())));
                result.add(entry.orElseThrow().id(), fields);
            }
        }
        ProbeStateFiles.write(root.resolve("native-owner-readers.json"), result.toString());
    }

    private void reformEditorProof(Minecraft mc) throws Exception {
        var actions = reformForm(mc).editingActions();
        var view = actions.view();
        var result = new JsonObject();
        result.addProperty("document", actions.definition().id().toString());
        result.addProperty("context", actions.context().toString());
        result.addProperty("dirty", view.dirty());
        result.addProperty("valid", view.validation().valid());
        result.addProperty("availability", view.availability().name());
        result.addProperty("busy", actions.hasActiveOperations());
        result.addProperty("validation", view.validation().toString());
        var operation = reformCommit(mc);
        if (operation != null) {
            var attempt = operation.snapshot();
            result.addProperty("attemptTerminal", attempt.terminal());
            result.addProperty("attemptReport", attempt.report().toString());
            boolean refused = !attempt.report().valid();
            if (attempt.result().orElse(null) instanceof design.inherent.reform.owner.EditorActor.Commit<?> commit) {
                result.addProperty("commitReport", commit.editorReport().toString());
                commit.receipt().ifPresent(receipt -> {
                    result.addProperty("persistence", receipt.persistence().name());
                    result.addProperty("persistenceReport", receipt.persistenceReport().toString());
                    result.addProperty("applicationReport", receipt.applicationReport().toString());
                });
                refused |= !commit.editorReport().valid() || commit.receipt().filter(receipt ->
                        receipt.persistence() == design.inherent.reform.owner.CommitReceipt.Persistence.REFUSED).isPresent();
            }
            result.addProperty("attemptRefused", refused);
        }
        view.snapshot().flatMap(snapshot -> snapshot.draft().inspect(actions.definition(),
                new design.inherent.reform.serialization.JsonLimits(262144, 64, 16384, 32768, 128, 4096)).candidate())
                .ifPresent(candidate -> result.add("draft", JsonParser.parseString(candidate.authoredJson())));
        result.addProperty("controls", widgets(mc.screen).size());
        ProbeStateFiles.write(root.resolve("reform-editor-proof.json"), result.toString());
    }
    private void reformFieldProof(Minecraft mc, JsonObject command) throws Exception {
        var form = reformForm(mc);
        var path = reformField(command);
        var actions = form.editingActions();
        var result = new JsonObject();
        result.addProperty("document", form.document().toString());
        result.addProperty("path", path.toString());
        var type = design.inherent.reform.edit.DraftTypes.declaredAt(actions.definition().type(), path);
        result.addProperty("shape", type.shape().toString());
        var widget = form.fieldControl(path);
        result.addProperty("mounted", widget.isPresent());
        widget.ifPresent(control -> {
            result.addProperty("active", control.active); result.addProperty("visible", control.visible);
            result.addProperty("class", control.getClass().getName());
            result.addProperty("label", control.getMessage().getString());
            if (control instanceof net.minecraft.client.gui.components.EditBox field) {
                result.addProperty("text", field.getValue()); result.addProperty("focused", field.isFocused());
                result.addProperty("canConsumeInput", field.canConsumeInput());
                try {
                    var editable = net.minecraft.client.gui.components.EditBox.class.getDeclaredField("isEditable"); editable.setAccessible(true);
                    result.addProperty("editable", editable.getBoolean(field));
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native EditBox eligibility changed", failure); }
            }
        });
        var view = actions.view();
        view.snapshot().ifPresent(snapshot -> {
            var node = snapshot.draft().at(path);
            var observed = new JsonObject(); observed.addProperty("kind", node.getClass().getSimpleName());
            if (node instanceof design.inherent.reform.edit.DraftNode.Scalar scalar) {
                observed.addProperty("raw", scalar.raw());
                scalar.unitInput().ifPresent(unit -> observed.addProperty("unitInput", unit.toString()));
                if (type.shape() instanceof design.inherent.reform.value.ValueShape.Scalar shape && shape.numericDisplay().isPresent()) {
                    var numeric = design.inherent.reform.presentation.NumericInput.open(shape, scalar);
                    observed.addProperty("displayText", numeric.text());
                    observed.addProperty("displayUnit", numeric.unit());
                    observed.addProperty("hasSlider", numeric.position().isPresent());
                    numeric.caption().ifPresent(caption -> observed.addProperty("caption", caption.fallback()));
                    // Observe the public presentation without applying an edit or moving the thumb.
                    observed.addProperty("rawAfterThumbRead", numeric.expected().raw());
                }
            } else observed.addProperty("retained", node.toString());
            result.add("node", observed);
        });
        result.add("issues", new GsonBuilder().create().toJsonTree(view.validation().issues()));
        ProbeStateFiles.write(root.resolve("reform-field-proof.json"), result.toString());
    }
    private void reformReferenceProof(Minecraft mc) throws Exception {
        if (!mc.screen.getClass().getName().equals("design.inherent.reform.minecraft.client.CollectionScreen")
                && !mc.screen.getClass().getName().equals("design.inherent.reform.minecraft.client.ReferenceScreen"))
            throw new IllegalStateException("Expected the actual native reference/collection browser");
        var field = mc.screen.getClass().getDeclaredField("browser"); field.setAccessible(true);
        var browser = (design.inherent.reform.reference.ReferenceBrowser<?>) field.get(mc.screen);
        var result = new JsonObject(); result.addProperty("bound", browser != null);
        if (browser != null) {
            result.addProperty("current", browser.current());
            browser.page().ifPresent(page -> {
                var observed = new JsonObject(); observed.addProperty("stamp", page.stamp().toString());
                observed.addProperty("availability", page.availability().name()); observed.addProperty("reason", page.reason());
                observed.addProperty("examined", page.examined()); observed.addProperty("exhausted", page.exhausted());
                observed.add("choices", new GsonBuilder().create().toJsonTree(page.choices())); result.add("page", observed);
            });
            browser.resolution().ifPresent(resolution -> {
                var observed = new JsonObject(); observed.addProperty("status", resolution.status().name());
                observed.addProperty("reason", resolution.reason());
                observed.add("preview", new GsonBuilder().create().toJsonTree(resolution.preview()));
                resolution.value().ifPresent(value -> observed.add("value", new GsonBuilder().create().toJsonTree(value)));
                result.add("resolution", observed);
            });
        }
        ProbeStateFiles.write(root.resolve("reform-reference-proof.json"), result.toString());
    }
    private static design.inherent.reform.definition.FieldPath reformField(JsonObject command) {
        var path = design.inherent.reform.definition.FieldPath.ROOT;
        var field = command.get("field");
        if (field.isJsonArray()) for (var segment : field.getAsJsonArray()) path = path.field(segment.getAsString());
        else path = path.field(field.getAsString());
        return path;
    }
    private static boolean visibleTarget(GuiEventListener listener, AbstractWidget target, double x, double y) {
        if (listener == target) return target.visible && target.active;
        if (listener instanceof ContainerEventHandler container) for (var child : container.children())
            if (visibleTarget(child,target,x,y))
                return listener instanceof net.minecraft.client.gui.screens.Screen || listener.isMouseOver(x,y);
        return false;
    }
    static ArrayList<AbstractWidget> widgets(GuiEventListener listener) {
        var found = new ArrayList<AbstractWidget>();
        if (listener instanceof AbstractWidget widget) found.add(widget);
        if (listener instanceof ContainerEventHandler container) for (var child : container.children()) found.addAll(widgets(child));
        return found;
    }
    private void write(String error) {
        try {
            var mc = Minecraft.getInstance(); var report = new JsonObject();
            report.addProperty("pid", ProcessHandle.current().pid()); report.addProperty("sequence", sequence); report.addProperty("screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
            if (mc.screen != null) report.addProperty("screenTitle", mc.screen.getTitle().getString());
            if(wayfinderObserver && mc.player!=null)report.add("wayfinder",WayfinderFixture.state(mc));
            report.addProperty("width", mc.getWindow().getGuiScaledWidth()); report.addProperty("height", mc.getWindow().getGuiScaledHeight()); report.addProperty("scale", mc.options.guiScale().get());
            int[] windowWidth = new int[1], windowHeight = new int[1];
            org.lwjgl.glfw.GLFW.glfwGetWindowSize(mc.getWindow().getWindow(), windowWidth, windowHeight);
            report.addProperty("windowWidth", windowWidth[0]); report.addProperty("windowHeight", windowHeight[0]);
            report.addProperty("actualGuiScale", mc.getWindow().getGuiScale());
            if (error != null) report.addProperty("error", error);
            report.addProperty("controlDown", net.minecraft.client.gui.screens.Screen.hasControlDown());
            report.addProperty("fieldEditNanos", fieldEditNanos);
            report.addProperty("keyAccepted", keyAccepted);
            if (nativeKey != null) report.add("nativeKey", nativeKey);
            if (serverProof!=null) report.add("serverProof",serverProof);
            report.addProperty("integrated",mc.getSingleplayerServer()!=null);
            report.addProperty("windowActive", mc.isWindowActive());
            report.addProperty("powergridTableReady", ClientPowerGridInputProbe.ready(mc));
            if (Files.isRegularFile(root.resolve("modifier-review-fixture.json"))
                    && ModList.get().isLoaded("powergrid")) report.addProperty("powergridWireReady", ClientPowerGridInputProbe.wireReady(mc));
            for (var mapping : mc.options.keyMappings) if (mapping.getName().equals("key.skyweave_compat.wayfinder")) {
                var binding = new JsonObject(); binding.addProperty("key", mapping.getKey().getName());
                binding.addProperty("defaultKey", mapping.getDefaultKey().getName());
                binding.addProperty("modifier", mapping.getKeyModifier().name());
                report.add("wayfinderBinding", binding);
            }
            report.addProperty("playerReady",mc.player!=null);
            report.add("loadedMods",new GsonBuilder().create().toJsonTree(ModList.get().getMods().stream().map(mod -> mod.getModId()+"@"+mod.getVersion()).sorted().toList()));
            if (mc.screen != null) report.addProperty("focusOwner", String.valueOf(mc.screen.getFocused()));
            if (mc.screen != null) report.addProperty("screenFocusAttached", mc.screen.getFocused() == null || mc.screen.children().contains(mc.screen.getFocused()));
            var rows = new JsonArray();
            if (mc.screen != null) for (var widget : widgets(mc.screen)) {
                var row = new JsonObject();
                row.addProperty("label", widget.getMessage().getString());
                row.addProperty("bold", widget.getMessage().getStyle().isBold());
                row.addProperty("x", widget.getX()); row.addProperty("y", widget.getY());
                row.addProperty("width", widget.getWidth()); row.addProperty("height", widget.getHeight());
                row.addProperty("active", widget.active); row.addProperty("focused", widget.isFocused());
                row.addProperty("reachable", visibleTarget(mc.screen, widget,
                        widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0));
                row.addProperty("font", widget.getMessage().getStyle().getFont().toString());
                if (widget instanceof net.minecraft.client.gui.components.EditBox input) {
                    row.addProperty("value", input.getValue()); row.addProperty("selection", input.getHighlighted());
                    row.addProperty("cursor", input.getCursorPosition()); row.addProperty("identity", System.identityHashCode(input));
                    row.addProperty("innerWidth", input.getInnerWidth()); row.addProperty("textWidth", mc.font.width(input.getValue()));
                    // Read-only fixture observation; product code uses only the native public caret API.
                    var display = net.minecraft.client.gui.components.EditBox.class.getDeclaredField("displayPos");
                    display.setAccessible(true); row.addProperty("displayStart", display.getInt(input));
                }
                rows.add(row);
            }
            report.add("widgets", rows);
            if (mc.screen != null && mc.screen.getClass().getName().equals("design.inherent.skyweavecompat.client.SettingsChoiceScreen")) {
                var choiceState = new JsonObject();
                for (String name : java.util.List.of("query", "selectedKey", "focus")) {
                    var field = mc.screen.getClass().getDeclaredField(name); field.setAccessible(true); choiceState.addProperty(name, String.valueOf(field.get(mc.screen)));
                }
                var field = mc.screen.getClass().getDeclaredField("list"); field.setAccessible(true);
                var list = (net.minecraft.client.gui.components.ObjectSelectionList<?>) field.get(mc.screen); var choices = new JsonArray();
                for (Object entry : list.children()) {
                    var value = entry.getClass().getDeclaredField("choice"); value.setAccessible(true); var choice = value.get(entry);
                    var key = choice.getClass().getDeclaredMethod("key"); key.setAccessible(true);
                    choices.add((String) key.invoke(choice));
                }
                choiceState.add("keys", choices); choiceState.addProperty("scroll", list.getScrollAmount()); report.add("chooser", choiceState);
            }

            var active = new JsonArray(); for (Feature feature : OptionalIntegrations.selection().active()) active.add(feature.key); report.add("active", active);
            ProbeStateFiles.write(root.resolve("config-review-state.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
