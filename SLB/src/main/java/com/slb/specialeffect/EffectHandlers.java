package com.slb.specialeffect;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

/**
 * EffectHandlers — 原子效果类型的实现仓库
 *
 * 每种 "type" 对应一个静态方法，在此统一处理。
 * 所有方法都是无状态的（不保存任何持久数据），
 * 由 SLBSEEventHandler 按事件类型分派调用。
 */
public final class EffectHandlers {

    private EffectHandlers() {}

    // ═══════════════════════════════════════════════════════════════
    //  读取工具（带默认值）
    // ═══════════════════════════════════════════════════════════════

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        return obj.has(key) ? obj.get(key).getAsFloat() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }

    /**
     * 解析状态效果 ID → Holder<MobEffect>
     * Minecraft 1.21.1 使用 Holder 引用 MobEffect
     */
    private static Holder<MobEffect> resolveEffect(String id) {
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(id));
        return holder.orElse(null);
    }

    /** 概率判定 */
    private static boolean rollChance(JsonObject config, Player player) {
        double chance = getDouble(config, "chance", 1.0);
        return player.getRandom().nextDouble() < chance;
    }

    /** 获取实体的边界框 */
    private static AABB getBounds(LivingEntity entity, double radius) {
        return entity.getBoundingBox().inflate(radius);
    }

    // ═══════════════════════════════════════════════════════════════
    //  效果实现
    // ═══════════════════════════════════════════════════════════════

    // ──────────────────────────────────────────────
    //  potion — 对持有者施加状态效果 (trigger: held)
    // ──────────────────────────────────────────────
    public static void handlePotion(Player player, JsonObject config) {
        Holder<MobEffect> effect = resolveEffect(config.get("id").getAsString());
        if (effect == null) return;
        int amplifier = getInt(config, "amplifier", 0);
        int duration = getInt(config, "duration", 100);
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false));
    }

    // ──────────────────────────────────────────────
    //  potion_target — 对目标施加状态效果 (trigger: hit)
    // ──────────────────────────────────────────────
    public static void handlePotionTarget(LivingEntity target, Player player, JsonObject config) {
        if (!rollChance(config, player)) return;
        Holder<MobEffect> effect = resolveEffect(config.get("id").getAsString());
        if (effect == null) return;
        int amplifier = getInt(config, "amplifier", 0);
        int duration = getInt(config, "duration", 100);
        target.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    // ──────────────────────────────────────────────
    //  fire_target — 点燃目标 (trigger: hit)
    //  Minecraft 1.21.1: setRemainingFireTicks(ticks)
    // ──────────────────────────────────────────────
    public static void handleFireTarget(LivingEntity target, Player player, JsonObject config) {
        if (!rollChance(config, player)) return;
        int seconds = getInt(config, "seconds", 5);
        target.setRemainingFireTicks(seconds * 20);
    }

    // ──────────────────────────────────────────────
    //  damage — 额外伤害 (trigger: hit, 支持 condition)
    // ──────────────────────────────────────────────
    public static void handleDamage(LivingEntity target, Player player, float baseDamage, JsonObject config) {
        if (!rollChance(config, player)) return;

        String condition = getString(config, "condition", "always");
        if (!checkCondition(condition, target)) return;

        float ratio = getFloat(config, "ratio", 0.0f);
        boolean isTrueDamage = "true".equals(getString(config, "type", "physical"));

        float extraDamage = baseDamage * ratio;
        if (extraDamage <= 0) return;

        if (isTrueDamage) {
            target.hurt(target.damageSources().magic(), extraDamage);
        } else {
            target.hurt(target.damageSources().playerAttack(player), extraDamage);
        }
    }

    private static boolean checkCondition(String condition, LivingEntity target) {
        return switch (condition) {
            case "always" -> true;
            case "target_on_fire" -> target.isOnFire();
            case "target_frozen" -> target.isFullyFrozen();
            case "full_health" -> target.getHealth() >= target.getMaxHealth();
            default -> true;
        };
    }

    // ──────────────────────────────────────────────
    //  lightning — 召唤闪电 (trigger: hit)
    // ──────────────────────────────────────────────
    public static void handleLightning(LivingEntity target, Player player, JsonObject config) {
        if (!rollChance(config, player)) return;

        boolean doubleInRain = getBool(config, "double_in_rain", false);
        if (doubleInRain && player.level().isRaining()) {
            // 雷暴时概率翻倍：再判定一次
            if (!rollChance(config, player)) return;
        }

        Level level = target.level();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setPos(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(false);
            level.addFreshEntity(bolt);
        }
    }

    // ──────────────────────────────────────────────
    //  life_steal — 吸血 (trigger: hit)
    // ──────────────────────────────────────────────
    public static void handleLifeSteal(Player player, float damageDealt, JsonObject config) {
        float ratio = getFloat(config, "ratio", 0.03f);
        float heal = damageDealt * ratio;
        if (heal > 0) player.heal(heal);
    }

    // ──────────────────────────────────────────────
    //  heal_on_kill — 击杀回复
    // ──────────────────────────────────────────────
    public static void handleHealOnKill(Player player, JsonObject config) {
        float amount = getFloat(config, "amount", 2.0f);
        player.heal(amount);
    }

    // ──────────────────────────────────────────────
    //  proud_soul_mult — 荣耀之魂倍率
    // ──────────────────────────────────────────────
    public static void handleProudSoulMult(JsonObject config, int originalCount, Consumer<Integer> setter) {
        float multiplier = getFloat(config, "multiplier", 1.5f);
        int newCount = (int) Math.ceil(originalCount * multiplier);
        setter.accept(newCount);
    }

    // ──────────────────────────────────────────────
    //  kill_count_mult — 击杀计数倍率
    // ──────────────────────────────────────────────
    public static void handleKillCountMult(JsonObject config, int originalCount, Consumer<Integer> setter) {
        float multiplier = getFloat(config, "multiplier", 1.5f);
        int newCount = (int) Math.ceil(originalCount * multiplier);
        setter.accept(newCount);
    }

    // ──────────────────────────────────────────────
    //  void_tear — 虚空撕裂 (trigger: hit)
    // ──────────────────────────────────────────────
    public static void handleVoidTear(LivingEntity target, Player player, float baseDamage, JsonObject config) {
        if (!rollChance(config, player)) return;
        float damageRatio = getFloat(config, "damage_ratio", 2.0f);
        float trueDamage = baseDamage * damageRatio;
        target.hurt(target.damageSources().magic(), trueDamage);
    }

    // ──────────────────────────────────────────────
    //  void_explosion — 虚空爆炸 (trigger: hit)
    // ──────────────────────────────────────────────
    public static void handleVoidExplosion(LivingEntity target, Player player, float baseDamage, JsonObject config) {
        if (!rollChance(config, player)) return;

        float damageRatio = getFloat(config, "damage_ratio", 0.8f);
        double radius = getDouble(config, "radius", 3.0);
        float explosionDamage = baseDamage * damageRatio;
        AABB area = getBounds(target, radius);

        target.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive())
            .forEach(e -> e.hurt(e.damageSources().magic(), explosionDamage));
    }

    // ──────────────────────────────────────────────
    //  sonar_pulse — 声呐脉冲
    // ──────────────────────────────────────────────
    public static void handleSonarPulse(Player player, JsonObject config) {
        double range = getDouble(config, "range", 15.0);
        int duration = getInt(config, "duration", 100);

        Holder<MobEffect> glowing = resolveEffect("minecraft:glowing");
        if (glowing == null) return;

        AABB area = getBounds(player, range);
        player.level().getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)
            .forEach(e -> e.addEffect(new MobEffectInstance(glowing, duration, 0)));
    }

    // ──────────────────────────────────────────────
    //  aoe_potion — 范围状态效果 (trigger: slashart)
    // ──────────────────────────────────────────────
    public static void handleAoePotion(Player player, JsonObject config) {
        Holder<MobEffect> effect = resolveEffect(config.get("id").getAsString());
        if (effect == null) return;
        int amplifier = getInt(config, "amplifier", 0);
        int duration = getInt(config, "duration", 60);
        double radius = getDouble(config, "radius", 5.0);
        AABB area = getBounds(player, radius);

        player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive())
            .forEach(e -> e.addEffect(new MobEffectInstance(effect, duration, amplifier)));
    }

    // ──────────────────────────────────────────────
    //  double_strike — 概率二连击 (trigger: hit)
    // ──────────────────────────────────────────────
    public static void handleDoubleStrike(LivingEntity target, Player player, float baseDamage, JsonObject config) {
        if (!rollChance(config, player)) return;
        float ratio = getFloat(config, "ratio", 0.6f);
        float extraDamage = baseDamage * ratio;
        if (extraDamage > 0) {
            target.hurt(target.damageSources().playerAttack(player), extraDamage);
        }
    }

    // ──────────────────────────────────────────────
    //  explosion_break — 刀损坏爆炸 (trigger: break)
    // ──────────────────────────────────────────────
    public static void handleExplosionBreak(Player player, float baseDamage, JsonObject config) {
        float damageRatio = getFloat(config, "damage_ratio", 1.0f);
        float power = getFloat(config, "power", 3.0f);

        player.level().explode(null, player.getX(), player.getY(), player.getZ(),
                power, Level.ExplosionInteraction.NONE);

        double radius = getDouble(config, "radius", 4.0);
        float dmg = baseDamage * damageRatio;
        AABB area = getBounds(player, radius);
        player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive())
            .forEach(e -> e.hurt(e.damageSources().explosion(null, player), dmg));
    }

    // ──────────────────────────────────────────────
    //  self_ignite — 点燃自己 (penalty, trigger: held)
    //  配合 interval_ticks 使用（如每 60tick 点燃 3秒）
    // ──────────────────────────────────────────────
    public static void handleSelfIgnite(Player player, JsonObject config) {
        int seconds = getInt(config, "seconds", 3);
        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), seconds * 20));
    }

    // ──────────────────────────────────────────────
    //  self_lightning — 雷劈自己 (penalty, trigger: held)
    //  配合 interval_ticks 使用（如每 600tick 一道雷）
    // ──────────────────────────────────────────────
    public static void handleSelfLightning(Player player, JsonObject config) {
        Level level = player.level();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setPos(player.getX(), player.getY(), player.getZ());
            bolt.setVisualOnly(false);
            level.addFreshEntity(bolt);
        }
    }

    // ──────────────────────────────────────────────
    //  self_damage — 攻击时自伤 (penalty, trigger: hit)
    // ──────────────────────────────────────────────
    public static void handleSelfDamage(Player player, JsonObject config) {
        float amount = getFloat(config, "amount", 1.0f);
        player.hurt(player.damageSources().magic(), amount);
    }

    // ──────────────────────────────────────────────
    //  block_ps — 阻止获得荣耀之魂 (penalty, trigger: ps)
    // ──────────────────────────────────────────────
    public static void handleBlockPs(java.util.function.Consumer<Integer> setter) {
        setter.accept(0);
    }
}
