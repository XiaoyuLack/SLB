package com.slb.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slb.SLB;
import com.slb.specialeffect.CompositeSE;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SLB 自定义 Special Effect 注册表（数据驱动版）
 *
 * 所有 SE 通过 JSON 定义（slb_ses.json），无需编写 Java 类。
 * 每个 SE 由一组 "原子效果" 组合而成，原子效果类型在
 * EffectHandlers 中实现。
 *
 * 工作流程：
 *   1. 从 /slb_ses.json（jar 内）读取 SE 定义
 *   2. RegisterEvent 时动态注册每个 SE 为 CompositeSE
 *   3. SLBSEEventHandler 分发事件到 EffectHandlers
 *
 * 用户自定义：直接编辑 SLB.jar 内的 slb_ses.json 即可。
 * 格式示例：
 *   "my_se": {
 *     "requestLevel": 10,
 *     "isCopiable": true,
 *     "isRemovable": true,
 *     "effects": [
 *       { "type": "potion", "id": "minecraft:regeneration", "amplifier": 1, "trigger": "held" }
 *     ]
 *   }
 */
public class SLBSpecialEffectsRegistry {

    private static final Map<String, JsonObject> SE_DEFINITIONS = new LinkedHashMap<>();
    private static boolean loaded = false;

    private SLBSpecialEffectsRegistry() {}

    // ═══════════════════════════════════════════════════════
    //  SE 定义加载
    // ═══════════════════════════════════════════════════════

    /**
     * 从 jar 内的 slb_ses.json 加载 SE 定义
     */
    public static void loadDefinitions() {
        if (loaded) return;
        loaded = true;

        try (InputStream is = SLBSpecialEffectsRegistry.class.getResourceAsStream("/slb_ses.json")) {
            if (is == null) {
                SLB.LOGGER.warn("slb_ses.json not found in classpath, no custom SEs loaded.");
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            JsonObject ses = root.getAsJsonObject("ses");
            if (ses == null) {
                SLB.LOGGER.warn("slb_ses.json missing 'ses' root key.");
                return;
            }

            for (String name : ses.keySet()) {
                SE_DEFINITIONS.put(name, ses.getAsJsonObject(name));
            }

            SLB.LOGGER.info("Loaded {} SE definitions from slb_ses.json", SE_DEFINITIONS.size());
        } catch (Exception e) {
            SLB.LOGGER.error("Failed to load SE definitions from slb_ses.json", e);
        }
    }

    public static Map<String, JsonObject> getDefinitions() {
        return SE_DEFINITIONS;
    }

    // ═══════════════════════════════════════════════════════
    //  RegisterEvent 回调 — 由 SLB.java 注册到 modEventBus
    // ═══════════════════════════════════════════════════════

    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(SpecialEffect.REGISTRY_KEY)) return;

        loadDefinitions();

        int count = 0;
        for (Map.Entry<String, JsonObject> entry : SE_DEFINITIONS.entrySet()) {
            String name = entry.getKey();
            JsonObject def = entry.getValue();

            int level = def.get("requestLevel").getAsInt();
            boolean copiable = def.has("isCopiable") && def.get("isCopiable").getAsBoolean();
            boolean removable = def.has("isRemovable") && def.get("isRemovable").getAsBoolean();
            JsonArray effects = def.getAsJsonArray("effects");
            JsonArray penalties = def.has("penalty") ? def.getAsJsonArray("penalty") : null;

            if (effects == null || effects.isEmpty()) {
                SLB.LOGGER.warn("SE '{}' has no effects, skipping.", name);
                continue;
            }

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("slb", name);

            event.register(SpecialEffect.REGISTRY_KEY, helper ->
                helper.register(id, new CompositeSE(level, copiable, removable, effects, penalties))
            );

            count++;
        }

        SLB.LOGGER.info("Registered {} custom SEs via RegisterEvent", count);
    }
}
