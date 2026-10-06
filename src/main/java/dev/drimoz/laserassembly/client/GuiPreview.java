package dev.drimoz.laserassembly.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.table.AssemblyTableScreen;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Takes the store captures by itself: the table at work in the world, its screen and each tab, the
 * laser's screen and JEI - Portable Beacons' and FactoryIO's tool, for the same reason: nobody has to
 * set a scene up by hand, and every capture is taken the same way each time. Does nothing unless the
 * environment says so:
 * <pre>
 *   LASER_GUI_PREVIEW=1 ./gradlew runClient
 *   java tools/Banners.java run/screenshots/preview run/store-art
 * </pre>
 * The client loads a <b>copy</b> of the {@code New World} dev world (never the original), builds the
 * scene far from anything there, writes {@code run/screenshots/preview/*.png} with the crop of each
 * screen in {@code crops.txt}, and quits. 1920x1080 at GUI scale 2: big enough to read, and a screen
 * with its tabs open stays under the 850 pixels CurseForge allows, without rescaling a pixel.
 */
@EventBusSubscriber(modid = LaserAssembly.MOD_ID, value = Dist.CLIENT)
public final class GuiPreview {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = "1".equals(System.getenv("LASER_GUI_PREVIEW"));
    private static final String SOURCE_WORLD = "New World";
    private static final String WORLD = "GuiPreview";

    private record Step(int delay, Consumer<Minecraft> action) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static int wait;
    private static String pendingShot;
    /** The scene's floor centre, set once the server has found the ground there. */
    private static BlockPos origin;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!started && minecraft.screen instanceof TitleScreen) {
            started = true;
            minecraft.options.pauseOnLostFocus = false;
            GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), 1920, 1080);
            minecraft.options.guiScale().set(2);
            minecraft.resizeDisplay();
            copyWorld(minecraft);
            script();
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> {});
            return;
        }
        if (!started || minecraft.player == null || minecraft.getSingleplayerServer() == null
                || pendingShot != null || STEPS.isEmpty() || wait-- > 0) {
            return;
        }
        STEPS.poll().action().accept(minecraft);
        wait = STEPS.isEmpty() ? 0 : STEPS.peek().delay();
    }

    /** Taken once the frame is fully drawn, not halfway through a tick. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || pendingShot == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        File file = new File(minecraft.gameDirectory, "screenshots/preview/" + pendingShot + ".png");
        file.getParentFile().mkdirs();
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(file);
            writeCrop(minecraft, pendingShot);
            LOGGER.info("GUI preview: {}", file);
        } catch (IOException e) {
            LOGGER.error("GUI preview failed: {}", file, e);
        }
        pendingShot = null;
    }

    // ------------------------------------------------------------------ script

    /** What a player would do, in order. Tabs are clicked where they are drawn, read from the screen. */
    private static void script() {
        step(60, mc -> onServer(mc, GuiPreview::build));
        // The lasers find the table on their next retarget, within a second, then warm up.
        step(80, mc -> onServer(mc, GuiPreview::camera));
        step(40, mc -> {
            mc.options.hideGui = true;
            mc.options.fov().set(55);
        });
        step(10, mc -> shot("world"));
        step(5, mc -> mc.options.hideGui = false);

        step(5, mc -> onServer(mc, player -> {
            player.setGameMode(GameType.CREATIVE);
            player.openMenu(table(player), origin.above());
        }));
        step(20, mc -> cursorAway(mc));
        step(5, mc -> shot("table"));
        // One tab at a time: two open side by side are wider than the 850 pixels a store image may be.
        step(5, mc -> clickTab(mc, 0));
        step(20, mc -> shot("table_info"));
        step(5, mc -> clickTab(mc, 0));
        step(5, mc -> clickTab(mc, 1));
        step(20, mc -> shot("table_lasers"));
        step(5, mc -> clickTab(mc, 1));
        step(5, mc -> clickTab(mc, 2));
        step(20, mc -> shot("table_faces"));
        step(5, mc -> clickTab(mc, 2));
        step(5, mc -> clickTab(mc, 3));
        step(20, mc -> shot("table_control"));
        step(5, mc -> mc.setScreen(null));

        step(10, mc -> onServer(mc, player -> player.openMenu(laser(player, 1), origin.offset(-2, 0, 1))));
        step(20, mc -> shot("laser"));
        step(5, mc -> mc.setScreen(null));

        if (ModList.get().isLoaded("jei")) {
            step(10, mc -> dev.drimoz.laserassembly.client.compat.jei.JeiPlugin.showRecipes());
            step(20, mc -> shot("jei"));
            step(5, mc -> mc.setScreen(null));
        }
        step(10, Minecraft::stop);
    }

    /**
     * A floor far from the dev world's builds, the table on a pedestal, three lasers around it on the
     * floor, each with its own lens, and a saddle ticked - a recipe without steps, so all three fire
     * at once, each in its colour.
     */
    private static void build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        int x = 1000, z = 1000;
        level.getChunk(x >> 4, z >> 4);
        origin = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);

        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                boolean edge = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                level.setBlockAndUpdate(origin.offset(dx, -1, dz), (edge ? Blocks.STONE_BRICKS : Blocks.POLISHED_DEEPSLATE).defaultBlockState());
                for (int dy = 0; dy <= 6; dy++) level.setBlockAndUpdate(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        level.setBlockAndUpdate(origin, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        level.setBlockAndUpdate(origin.above(), ModBlocks.ASSEMBLY_TABLE.get().defaultBlockState());

        ItemStack[] lenses = {new ItemStack(ModItems.ENGRAVE_LENS.get()), new ItemStack(ModItems.CUT_LENS.get()),
                new ItemStack(ModItems.WELD_LENS.get())};
        BlockPos[] at = {origin.offset(-2, 1, -2), origin.offset(-2, 0, 1), origin.offset(1, 0, -2)};
        for (int i = 0; i < at.length; i++) {
            // The one behind sits on a pillar and faces the table, so it shows over the table's top.
            Direction facing = i == 0 ? Direction.EAST : Direction.UP;
            if (i == 0) level.setBlockAndUpdate(at[i].below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            level.setBlockAndUpdate(at[i], ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, facing));
            if (level.getBlockEntity(at[i]) instanceof LaserBlockEntity laser) {
                laser.energy().receiveEnergy(LaserBlockEntity.ENERGY_CAPACITY, false);
                laser.lens().insertItem(0, lenses[i], false);
            }
        }

        AssemblyTableBlockEntity table = table(player);
        table.toggle(LaserAssembly.id("saddle"));
        table.toggle(LaserAssembly.id("trident"));
        insert(table, Items.LEATHER, 30);
        insert(table, Items.IRON_INGOT, 12);
        insert(table, Items.STRING, 20);
        insert(table, Items.PRISMARINE_SHARD, 8);

        // The inventory the screens show: what the mod adds.
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(ModItems.CUT_LENS.get(), 4));
        player.getInventory().setItem(10, new ItemStack(ModItems.WELD_LENS.get(), 4));
        player.getInventory().setItem(11, new ItemStack(ModItems.ENGRAVE_LENS.get(), 4));
        List<ItemStack> augments = List.of(new ItemStack(ModItems.REGULATOR_AUGMENT.get()), new ItemStack(ModItems.BARREL_AUGMENT.get()),
                new ItemStack(ModItems.RANGE_AUGMENT_2.get()), new ItemStack(ModItems.EFFICIENCY_AUGMENT_2.get()));
        for (int i = 0; i < augments.size(); i++) player.getInventory().setItem(13 + i, augments.get(i));
        player.getInventory().setItem(0, new ItemStack(ModBlocks.ASSEMBLY_TABLE_ITEM.get()));
        player.getInventory().setItem(1, new ItemStack(ModBlocks.LASER_ITEM.get(), 8));
        player.teleportTo(level, origin.getX() + 4.5, origin.getY() + 3, origin.getZ() + 4.5, 135, 25);
    }

    /** From the front corner, a little above, looking at the table's top - lasers beyond it. */
    private static void camera(ServerPlayer player) {
        // A spectator does not fall: a flying creative player dropped to the floor before the capture.
        player.setGameMode(GameType.SPECTATOR);
        double eyeX = origin.getX() + 3.3, eyeY = origin.getY() + 3.9, eyeZ = origin.getZ() + 3.3;
        double dx = origin.getX() + 0.2 - eyeX, dy = origin.getY() + 1.4 - eyeY, dz = origin.getZ() + 0.5 - eyeZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.teleportTo(player.serverLevel(), eyeX, eyeY - player.getEyeHeight(), eyeZ, yaw, pitch);
        for (BlockPos pos : List.of(origin.offset(-2, 1, -2), origin.offset(-2, 0, 1), origin.offset(1, 0, -2))) {
            if (player.serverLevel().getBlockEntity(pos) instanceof LaserBlockEntity laser) {
                laser.energy().receiveEnergy(LaserBlockEntity.ENERGY_CAPACITY, false);
            }
        }
    }

    private static AssemblyTableBlockEntity table(ServerPlayer player) {
        return (AssemblyTableBlockEntity) player.serverLevel().getBlockEntity(origin.above());
    }

    private static LaserBlockEntity laser(ServerPlayer player, int index) {
        return (LaserBlockEntity) player.serverLevel().getBlockEntity(index == 1 ? origin.offset(-2, 0, 1) : origin.offset(1, 0, -2));
    }

    private static void insert(AssemblyTableBlockEntity table, net.minecraft.world.item.Item item, int count) {
        ItemStack rest = new ItemStack(item, count);
        for (int slot = 0; slot < table.inputs().getSlots() && !rest.isEmpty(); slot++) rest = table.inputs().insertItem(slot, rest, false);
    }

    // ------------------------------------------------------------------ plumbing

    private static void step(int delay, Consumer<Minecraft> action) {
        STEPS.add(new Step(delay, action));
    }

    private static void shot(String name) {
        pendingShot = name;
    }

    private static void onServer(Minecraft minecraft, Consumer<ServerPlayer> action) {
        var server = minecraft.getSingleplayerServer();
        var id = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) action.accept(player);
        });
    }

    /** Clicks the header of the table screen's tab {@code index}: Info, Lasers, Faces, Control. */
    private static void clickTab(Minecraft minecraft, int index) {
        if (minecraft.screen instanceof AssemblyTableScreen screen) {
            Rect2i tab = screen.tabAreas().get(index);
            screen.mouseClicked(tab.getX() + 6, tab.getY() + 6, 0);
        }
    }

    /** The cursor off every control, so no tooltip or highlight shows. */
    private static void cursorAway(Minecraft minecraft) {
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), 2, 2);
    }

    /**
     * The window and its tabs, in screenshot pixels, beside the captures: {@code tools/Banners.java}
     * cuts the store images from these instead of from numbers that drift with every layout change.
     */
    private static void writeCrop(Minecraft minecraft, String name) throws IOException {
        int x0, y0, x1, y1;
        double scale = minecraft.getWindow().getGuiScale();
        if (minecraft.screen instanceof AbstractContainerScreen<?> screen) {
            x0 = screen.getGuiLeft();
            y0 = screen.getGuiTop();
            x1 = x0 + screen.getXSize();
            y1 = y0 + screen.getYSize();
            if (screen instanceof AssemblyTableScreen table) {
                for (Rect2i tab : table.tabAreas()) {
                    x0 = Math.min(x0, tab.getX());
                    y0 = Math.min(y0, tab.getY());
                    x1 = Math.max(x1, tab.getX() + tab.getWidth());
                    y1 = Math.max(y1, tab.getY() + tab.getHeight());
                }
            }
        } else if (minecraft.screen != null) {
            // JEI's recipe screen: its window sits in the middle.
            int w = minecraft.getWindow().getGuiScaledWidth(), h = minecraft.getWindow().getGuiScaledHeight();
            x0 = w / 2 - 110;
            x1 = w / 2 + 110;
            y0 = 8;
            y1 = h - 8;
        } else {
            return;
        }
        int pad = 4;
        String line = String.format("%s %d %d %d %d%n", name, (int) ((x0 - pad) * scale), (int) ((y0 - pad) * scale),
                (int) ((x1 - x0 + 2 * pad) * scale), (int) ((y1 - y0 + 2 * pad) * scale));
        Path crops = minecraft.gameDirectory.toPath().resolve("screenshots/preview/crops.txt");
        Files.writeString(crops, line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private static void copyWorld(Minecraft minecraft) {
        Path saves = minecraft.gameDirectory.toPath().resolve("saves");
        Path source = saves.resolve(SOURCE_WORLD);
        Path target = saves.resolve(WORLD);
        Path preview = minecraft.gameDirectory.toPath().resolve("screenshots/preview");
        try {
            for (Path dir : List.of(target, preview)) {
                if (!Files.exists(dir)) continue;
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
            Files.createDirectories(preview);
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path path : (Iterable<Path>) walk::iterator) {
                    // The running world's lock, which the copy must not inherit.
                    if (path.getFileName().toString().endsWith(".lock")) continue;
                    Path copy = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) Files.createDirectories(copy);
                    else Files.copy(path, copy);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("GUI preview: could not copy the dev world", e);
        }
    }

    private GuiPreview() {}
}
