package com.slb.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.slb.specialeffect.CompositeSE;
import com.slb.specialeffect.EffectHandlers;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

/**
 * SLBSEEventHandler — 统一的 Special Effect 事件分发器
 *
 * 监听从 SlashBladeEvent 派发的所有事件，
 * 对刀上挂载的每个 SLB 自定义 SE，
 * 查找其对应的 CompositeSE 配置并分发给 EffectHandlers。
 *
 * 工作流程：
 *   事件触发 → 遍历刀上的 SE → 匹配 SLB 的 SE ID →
 *   获取 CompositeSE 的 effects 列表 → 按 type 分发给 EffectHandlers
 */
@EventBusSubscriber(modid = "slb")
public class SLBSEEventHandler {

    private SLBSEEventHandler() {}

    // ═══════════════════════════════════════════════════════
    //  核心工具：获取刀上所有 SLB 自定义 SE 的 effect 配置
    // ═══════════════════════════════════════════════════════

    /**
     * 获取刀上所有 SLB 自定义 SE 的 effect 配置列表
     */
    private static List<JsonObject> getActiveEffects(ISlashBladeState state) {
        if (state == null) return List.of();

        Collection<ResourceLocation> ses = state.getSpecialEffects();
        if (ses == null || ses.isEmpty()) return List.of();

        List<JsonObject> allEffects = new ArrayList<>();

        for (ResourceLocation seId : ses) {
            // 只处理 slb 命名空间的自定义 SE
            if (!"slb".equals(seId.getNamespace())) continue;

            // 从注册表获取 SE 实例
            SpecialEffect se = SpecialEffectsRegistry.REGISTRY.get(seId);
            if (!(se instanceof CompositeSE composite)) continue;

            List<JsonObject> effects = composite.getEffects();
            if (effects != null) allEffects.addAll(effects);
        }

        return allEffects;
    }

    /**
     * 获取刀上所有 SLB 自定义 SE 的 penalty 配置列表
     * 等级不足时自动对玩家施加这些负面效果
     */
    private static List<JsonObject> getActivePenalties(ISlashBladeState state) {
        if (state == null) return List.of();

        Collection<ResourceLocation> ses = state.getSpecialEffects();
        if (ses == null || ses.isEmpty()) return List.of();

        List<JsonObject> allPenalties = new ArrayList<>();

        for (ResourceLocation seId : ses) {
            if (!"slb".equals(seId.getNamespace())) continue;

            SpecialEffect se = SpecialEffectsRegistry.REGISTRY.get(seId);
            if (!(se instanceof CompositeSE composite)) continue;

            List<JsonObject> penalties = composite.getPenalties();
            if (penalties != null) allPenalties.addAll(penalties);
        }

        return allPenalties;
    }

    // ═══════════════════════════════════════════════════════
    //  获取持有者是否满足等级要求
    // ═══════════════════════════════════════════════════════

    private static boolean isEffective(Player player, ISlashBladeState state) {
        // 检查刀上所有 SLB SE 的等级要求
        Collection<ResourceLocation> ses = state.getSpecialEffects();
        if (ses == null) return true;

        for (ResourceLocation seId : ses) {
            if (!"slb".equals(seId.getNamespace())) continue;
            SpecialEffect se = SpecialEffectsRegistry.REGISTRY.get(seId);
            if (se != null && !SpecialEffect.isEffective(se, player.experienceLevel)) {
                return false;
            }
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════
    //  冷却系统（用于 interval-based penalty）
    // ═══════════════════════════════════════════════════════

    /** 玩家 UUID → (penalty key → 上次应用的游戏刻) */
    private static final Map<UUID, Map<String, Long>> penaltyCooldowns = new HashMap<>();

    /**
     * 缓存当前玩家引用（用于 AddProudSoulEvent 等无法直接获取玩家的事件）
     * 在 onUpdate 中每 tick 更新，保证缓存值始终是当前活跃玩家
     */
    private static Player cachedPlayer = null;

    /**
     * 检查 penalty 是否在冷却中
     * 如果 config 中有 interval_ticks，则按间隔执行
     * @return true = 还在冷却，跳过本次应用
     */
    private static boolean isOnCooldown(Player player, JsonObject config, int index) {
        if (!config.has("interval_ticks")) return false;
        int interval = config.get("interval_ticks").getAsInt();
        if (interval <= 0) return false;

        UUID uuid = player.getUUID();
        long gameTime = player.level().getGameTime();
        String key = config.get("type").getAsString() + "_" + index;

        Map<String, Long> timers = penaltyCooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
        Long lastTime = timers.get(key);
        if (lastTime != null && gameTime - lastTime < interval) return true;
        timers.put(key, gameTime);
        return false;
    }

    // ═══════════════════════════════════════════════════════
    //  事件处理器
    // ═══════════════════════════════════════════════════════

    /**
     * UpdateEvent — 每 tick 手持时触发
     * 分派: potion(held), aoe_potion(held)
     */
    @SubscribeEvent
    public static void onUpdate(SlashBladeEvent.UpdateEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!(event.getEntity() instanceof Player player)) return;
        cachedPlayer = player; // 缓存玩家引用，供 AddProudSoulEvent 等使用
        if (!event.isSelected()) return;

        List<JsonObject> effects = getActiveEffects(state);
        if (effects.isEmpty()) return;

        boolean effective = isEffective(player, state);

        for (JsonObject cfg : effects) {
            String trigger = cfg.has("trigger") ? cfg.get("trigger").getAsString() : "hit";
            if (!"held".equals(trigger)) continue;

            String type = cfg.get("type").getAsString();

            switch (type) {
                case "potion" -> {
                    if (effective) EffectHandlers.handlePotion(player, cfg);
                }
                case "aoe_potion" -> {
                    // aoe_potion 默认 trigger 是 slashart，兼容 held 模式
                    if (effective) EffectHandlers.handleAoePotion(player, cfg);
                }
                // 其他 held 触发的效果可在此扩展
            }
        }

        // 等级不足时应用 penalty 效果
        if (!effective) {
            List<JsonObject> penalties = getActivePenalties(state);
            int idx = 0;
            for (JsonObject cfg : penalties) {
                String trigger = cfg.has("trigger") ? cfg.get("trigger").getAsString() : "held";
                if (!"held".equals(trigger)) continue;
                if (isOnCooldown(player, cfg, idx++)) continue;
                String type = cfg.get("type").getAsString();
                switch (type) {
                    case "potion" -> EffectHandlers.handlePotion(player, cfg);
                    case "self_ignite" -> EffectHandlers.handleSelfIgnite(player, cfg);
                    case "self_lightning" -> EffectHandlers.handleSelfLightning(player, cfg);
                }
            }
        }
    }

    /**
     * HitEvent — 击中实体时触发
     * 分派: potion_target, fire_target, damage, life_steal, void_tear,
     *       void_explosion, double_strike, potion(hit)
     */
    @SubscribeEvent
    public static void onHit(SlashBladeEvent.HitEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!(event.getUser() instanceof Player player)) return;

        LivingEntity target = event.getTarget();
        float baseDamage = state.getBaseAttackModifier();

        List<JsonObject> effects = getActiveEffects(state);
        if (effects.isEmpty()) return;

        boolean effective = isEffective(player, state);

        for (JsonObject cfg : effects) {
            String type = cfg.get("type").getAsString();

            // 不处理非 hit 触发
            String trigger = cfg.has("trigger") ? cfg.get("trigger").getAsString() : "hit";
            if (trigger.startsWith("held") || trigger.startsWith("slashart")) continue;

            if (!effective && !cfg.has("apply_when_ineffective")) continue;

            switch (type) {
                case "potion_target" -> EffectHandlers.handlePotionTarget(target, player, cfg);
                case "fire_target" -> EffectHandlers.handleFireTarget(target, player, cfg);
                case "damage" -> EffectHandlers.handleDamage(target, player, baseDamage, cfg);
                case "life_steal" -> EffectHandlers.handleLifeSteal(player, baseDamage, cfg);
                case "void_tear" -> EffectHandlers.handleVoidTear(target, player, baseDamage, cfg);
                case "void_explosion" -> EffectHandlers.handleVoidExplosion(target, player, baseDamage, cfg);
                case "double_strike" -> EffectHandlers.handleDoubleStrike(target, player, baseDamage, cfg);
                case "lightning" -> EffectHandlers.handleLightning(target, player, cfg);
                case "potion" -> EffectHandlers.handlePotion(player, cfg); // hit-triggered potion
            }
        }

        // 等级不足时应用 hit 触发的 penalty 效果
        if (!effective) {
            List<JsonObject> penalties = getActivePenalties(state);
            int idx = 0;
            for (JsonObject cfg : penalties) {
                String trigger = cfg.has("trigger") ? cfg.get("trigger").getAsString() : "hit";
                if (trigger.startsWith("held") || trigger.startsWith("slashart")) continue;
                if (isOnCooldown(player, cfg, idx++)) continue;
                String type = cfg.get("type").getAsString();
                switch (type) {
                    case "potion" -> EffectHandlers.handlePotion(player, cfg);
                    case "self_damage" -> EffectHandlers.handleSelfDamage(player, cfg);
                }
            }
        }
    }

    /**
     * PerformSlashArtEvent — 发动 SA 时触发
     * 分派: aoe_potion(slashart), potion(slashart)
     */
    @SubscribeEvent
    public static void onPerformSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!(event.getEntityLiving() instanceof Player player)) return;

        ISlashBladeState state = event.getSlashBladeState();
        List<JsonObject> effects = getActiveEffects(state);
        if (effects.isEmpty()) return;

        boolean effective = isEffective(player, state);

        for (JsonObject cfg : effects) {
            String trigger = cfg.has("trigger") ? cfg.get("trigger").getAsString() : "hit";
            if (!"slashart".equals(trigger)) continue;

            if (!effective) continue;

            String type = cfg.get("type").getAsString();
            switch (type) {
                case "aoe_potion" -> EffectHandlers.handleAoePotion(player, cfg);
                case "potion" -> EffectHandlers.handlePotion(player, cfg);
            }
        }
    }

    /**
     * AddProudSoulEvent — 获得荣耀之魂时触发
     * 分派: proud_soul_mult
     */
    @SubscribeEvent
    public static void onAddProudSoul(SlashBladeEvent.AddProudSoulEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (state == null) return;

        boolean effective = true;
        if (cachedPlayer != null) {
            effective = isEffective(cachedPlayer, state);
        }

        // 等级达标：处理 proud_soul_mult 倍率
        if (effective) {
            List<JsonObject> effects = getActiveEffects(state);
            if (!effects.isEmpty()) {
                for (JsonObject cfg : effects) {
                    if (!"proud_soul_mult".equals(cfg.get("type").getAsString())) continue;
                    EffectHandlers.handleProudSoulMult(cfg, event.getOriginCount(), event::setNewCount);
                }
            }
        }

        // 等级不足：处理 block_ps penalty → 荣耀之魂归零
        if (!effective) {
            List<JsonObject> penalties = getActivePenalties(state);
            if (!penalties.isEmpty()) {
                for (JsonObject cfg : penalties) {
                    if (!"block_ps".equals(cfg.get("type").getAsString())) continue;
                    EffectHandlers.handleBlockPs(event::setNewCount);
                }
            }
        }
    }

    /**
     * AddKillCountEvent — 击杀计数增加时触发
     * 分派: kill_count_mult
     */
    @SubscribeEvent
    public static void onAddKillCount(SlashBladeEvent.AddKillCountEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (state == null) return;

        List<JsonObject> effects = getActiveEffects(state);
        if (effects.isEmpty()) return;

        for (JsonObject cfg : effects) {
            if (!"kill_count_mult".equals(cfg.get("type").getAsString())) continue;
            EffectHandlers.handleKillCountMult(cfg, event.getOriginCount(), event::setNewCount);
        }
    }

    /**
     * BreakEvent — 刀损坏时触发
     * 分派: explosion_break
     */
    @SubscribeEvent
    public static void onBreak(SlashBladeEvent.BreakEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (state == null) return;

        List<JsonObject> effects = getActiveEffects(state);
        if (effects.isEmpty()) return;

        // 无法直接从 BreakEvent 获取玩家，跳过
        // 用 UpdateEvent + 追踪或后期改进
    }

    /**
     * 检查标记目标死亡的回调（由外部标记系统调用）
     */
    public static void onMarkedTargetDeath(Player player, ISlashBladeState state) {
        if (player == null || state == null) return;

        List<JsonObject> effects = getActiveEffects(state);
        if (effects.isEmpty()) return;

        for (JsonObject cfg : effects) {
            if (!"sonar_pulse".equals(cfg.get("type").getAsString())) continue;
            EffectHandlers.handleSonarPulse(player, cfg);
        }
    }
}
