package com.slb.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SLB 全局配置文件 (config/slb.json5)
 *
 * 控制所有 SLB 注册的命名刀的全局参数。
 * 修改后需重启游戏。
 */
public class SLBConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    /** 全局伤害倍率 (1.0 = 100%) */
    public static final ModConfigSpec.DoubleValue globalDamageMultiplier;

    /** 所有 SLB 刀默认是否可被妖化 */
    public static final ModConfigSpec.BooleanValue enableBewitchedByDefault;

    /** 是否在日志中列出所有发现的 SLB 命名刀 */
    public static final ModConfigSpec.BooleanValue logDiscoveredBlades;

    static {
        // ── 伤害系统 ──────────────────────────────────────
        BUILDER.comment("═══ Damage Settings ═══")
                .push("damage");

        globalDamageMultiplier = BUILDER
                .comment(" Global damage multiplier for all SLB blades.",
                        " 2.0 = double damage, 0.5 = half damage.",
                        " This is applied ON TOP of the blade's base_attack.",
                        " Range: 0.1 ~ 10.0, Default: 1.0")
                .defineInRange("globalDamageMultiplier", 1.0, 0.1, 10.0);

        BUILDER.pop();

        // ── 剑类型 ────────────────────────────────────────
        BUILDER.comment("═══ Sword Type Defaults ═══")
                .push("sword_type");

        enableBewitchedByDefault = BUILDER
                .comment(" If true, SLB blades without explicit sword_type",
                        " will default to ['bewitched'] (妖刀).",
                        " If false, they default to ['none'].")
                .define("enableBewitchedByDefault", true);

        BUILDER.pop();

        // ── 调试 ──────────────────────────────────────────
        BUILDER.comment("═══ Debug / Logging ═══")
                .push("debug");

        logDiscoveredBlades = BUILDER
                .comment(" Log all discovered SLB named blades to console on startup.")
                .define("logDiscoveredBlades", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
