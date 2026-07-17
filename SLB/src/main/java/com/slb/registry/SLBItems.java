package com.slb.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slb.SLB;
import com.slb.item.NamedBladeItem;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateData;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeDataComponents;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SLBItems — 命名刀物品自动注册
 *
 * 自动扫描 data/slb/slashblade/named_blades/ 下的所有 .json 文件，
 * 为每把命名刀注册独立物品 slb:<刀名>。
 *
 * 注册时会自动：
 *   - 从刀定义 JSON 中读取 render.model 和 render.texture，
 *     设为物品默认 BladeStateData 组件，确保 JEI 中正确显示模型和贴图
 *   - 在 assets/slb/models/item/<刀名>.json 生成物品模型文件，
 *     引用 slb_blade.json（builtin/entity 模型），避免紫黑块
 *
 * 使用方式：
 *   1. 在 data/slb/slashblade/named_blades/<刀名>.json 中定义刀
 *   2. 重新构建即可
 *   3. 游戏内 /give @s slb:<刀名> 获得已配置的刀
 */
public class SLBItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SLB.MOD_ID);

    private static final List<Supplier<NamedBladeItem>> NAMED_BLADES = new ArrayList<>();

    private static boolean initialized = false;

    /**
     * 记录从 JSON 中提取的刀渲染数据
     */
    private record BladeEntry(String name, ResourceLocation model, ResourceLocation texture) {}

    public static void init(Path jarFilePath) {
        if (initialized) return;
        initialized = true;

        Set<BladeEntry> entries = new LinkedHashSet<>();

        if (jarFilePath != null && jarFilePath.toFile().isFile()) {
            entries.addAll(scanJarFile(jarFilePath.toFile()));
        }

        if (entries.isEmpty()) {
            entries.addAll(classLoaderScan());
        }

        if (entries.isEmpty()) {
            SLB.LOGGER.warn("No named blades discovered. Registering default 'example_blank' only.");
            registerNamedBlade(new BladeEntry("example_blank", null, null));
        } else {
            List<String> names = entries.stream().map(BladeEntry::name).toList();
            SLB.LOGGER.info("Auto-discovered {} named blade(s): {}", names.size(), names);
            entries.forEach(SLBItems::registerNamedBlade);

        }
    }

    // ── JAR 扫描等已有方法 ──

    private static Set<BladeEntry> scanJarFile(File jarFile) {
        Set<BladeEntry> entries = new LinkedHashSet<>();
        String prefix = "data/slb/slashblade/named_blades/";

        try (ZipFile zf = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> zipEntries = zf.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith(".json") && !entry.isDirectory()) {
                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    if (!fileName.startsWith("_")) {
                        String bladeName = fileName.replace(".json", "");
                        BladeEntry bladeEntry = parseBladeJson(zf.getInputStream(entry), bladeName);
                        if (bladeEntry != null) {
                            entries.add(bladeEntry);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SLB.LOGGER.debug("JAR scan failed: {}", e.getMessage());
        }

        return entries;
    }

    private static Set<BladeEntry> classLoaderScan() {
        Set<BladeEntry> entries = new LinkedHashSet<>();

        try {
            ClassLoader cl = SLBItems.class.getClassLoader();
            Enumeration<URL> dirs = cl.getResources("data/slb/slashblade/named_blades/");

            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();

                if ("file".equals(protocol)) {
                    File dir = new File(url.toURI());
                    File[] jsons = dir.listFiles((d, fname) -> fname.endsWith(".json") && !fname.startsWith("_"));
                    if (jsons != null) {
                        for (File f : jsons) {
                            String bladeName = f.getName().replace(".json", "");
                            BladeEntry bladeEntry = parseBladeJsonFile(f, bladeName);
                            if (bladeEntry != null) {
                                entries.add(bladeEntry);
                            }
                        }
                    }

                } else if ("jar".equals(protocol)) {
                    String spec = url.toURI().getSchemeSpecificPart();
                    int bangIdx = spec.indexOf("!/");
                    if (bangIdx >= 0) {
                        String jarPart = spec.substring(0, bangIdx);
                        File jarFile = new File(new URI(jarPart));
                        if (jarFile.isFile()) {
                            entries.addAll(scanJarFile(jarFile));
                        }
                    }
                }
            }
        } catch (Exception e) {
            SLB.LOGGER.debug("ClassLoader scan failed: {}", e.getMessage());
        }

        return entries;
    }

    private static BladeEntry parseBladeJson(InputStream inputStream, String bladeName) {
        try (var reader = new InputStreamReader(inputStream)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return extractBladeEntry(json, bladeName);
        } catch (Exception e) {
            SLB.LOGGER.debug("Failed to parse blade JSON '{}': {}", bladeName, e.getMessage());
            return new BladeEntry(bladeName, null, null);
        }
    }

    private static BladeEntry parseBladeJsonFile(File file, String bladeName) {
        try (var reader = new java.io.FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return extractBladeEntry(json, bladeName);
        } catch (Exception e) {
            SLB.LOGGER.debug("Failed to parse blade JSON '{}': {}", bladeName, e.getMessage());
            return new BladeEntry(bladeName, null, null);
        }
    }

    private static BladeEntry extractBladeEntry(JsonObject json, String bladeName) {
        if (!json.has("render")) {
            return new BladeEntry(bladeName, null, null);
        }
        JsonObject render = json.getAsJsonObject("render");
        if (!render.has("model") || !render.has("texture")) {
            return new BladeEntry(bladeName, null, null);
        }
        try {
            ResourceLocation model = ResourceLocation.parse(render.get("model").getAsString());
            ResourceLocation texture = ResourceLocation.parse(render.get("texture").getAsString());
            return new BladeEntry(bladeName, model, texture);
        } catch (Exception e) {
            SLB.LOGGER.debug("Invalid render data in blade '{}': {}", bladeName, e.getMessage());
            return new BladeEntry(bladeName, null, null);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerNamedBlade(BladeEntry entry) {
        ResourceLocation bladeId = ResourceLocation.fromNamespaceAndPath(SLB.MOD_ID, entry.name);

        Supplier<NamedBladeItem> itemSupplier = () -> {
            Item.Properties props = new Item.Properties();

            if (entry.model != null && entry.texture != null) {
                try {
                    DataComponentType<BladeStateData> componentType =
                            (DataComponentType<BladeStateData>) SlashBladeDataComponents.BLADE_STATE_DATA.get();

                    BladeStateData defaultData = new BladeStateData(
                            "",    // translationKey — 留空，由 PlayerTickEvent 的 configureStack 填入
                            4.0f, // baseAttackModifier — 同 DEFAULT
                            0,    // proudSoul
                            0,    // killCount
                            0,    // refine
                            false, // broken
                            false, // sealed
                            ResourceLocation.fromNamespaceAndPath("slashblade", "judgement_cut"), // slashArts
                            false, // defaultBewitched
                            ResourceLocation.fromNamespaceAndPath("slashblade", "standby"),      // comboRoot
                            CarryType.PSO2,  // carryType
                            -13421569,       // effectColor
                            false,           // effectColorInverse
                            Vec3.ZERO,       // adjust
                            Optional.of(entry.texture),
                            Optional.of(entry.model),
                            List.of()        // specialEffects
                    );

                    props.component(componentType, defaultData);
                } catch (Exception e) {
                    SLB.LOGGER.warn("Could not set default BladeStateData for '{}': {}", entry.name, e.getMessage());
                }
            }

            return new NamedBladeItem(bladeId, props);
        };

        Supplier<NamedBladeItem> registered = ITEMS.register(entry.name, itemSupplier);
        NAMED_BLADES.add(registered);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static List<NamedBladeItem> getNamedBlades() {
        return NAMED_BLADES.stream()
                .map(Supplier::get)
                .toList();
    }
}
