package com.slb;

import com.mojang.logging.LogUtils;
import com.slb.config.SLBConfig;
import com.slb.registry.SLBClientHandler;
import com.slb.registry.SLBItems;
import com.slb.registry.SLBSpecialEffectsRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

import java.nio.file.Path;

/**
 * SLB — SlashBlade Resharped Addon
 *
 * ════════════════════════════════════════════════════════════
 *  核心设计：数据驱动
 * ════════════════════════════════════════════════════════════
 *
 * 每把命名刀 = 1个JSON + 1个OBJ + 1个PNG，互相独立。
 *
 * 【添加新刀】三步走：
 *   1. 把 OBJ 和 PNG 扔到 assets/slb/model/<刀名>/ 下
 *   2. 在 data/slb/slashblade/named_blades/<刀名>.json 中定义参数
 *   3. (可选) 在 data/slb/recipe/ 中添加合成配方
 *
 * 【外部自定义】不重新编译的方法：
 *   • 数据包：把 data/slb/ 打包成 datapack 放到 world/datapacks/
 *   • KubeJS: 用 ServerEvents.generateData 注入 JSON
 *   • 资源包：覆盖 assets/slb/model/ 的 OBJ/PNG
 *   • 全局配置：编辑 config/slb.json5
 */
@Mod(SLB.MOD_ID)
public class SLB {
    public static final String MOD_ID = "slb";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SLB(IEventBus modEventBus, ModContainer modContainer) {
        // 注册 NeoForge 配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, SLBConfig.SPEC, "slb.json5");

        // 从 ModContainer 获取 Mod JAR 路径
        Path jarPath = null;
        try {
            var owningFile = modContainer.getModInfo().getOwningFile();
            if (owningFile != null) {
                jarPath = owningFile.getFile().getFilePath();
            }
        } catch (Exception e) {
            LOGGER.debug("Could not get mod jar path from ModContainer: {}", e.getMessage());
        }

        // 注册命名刀独立物品（slb:<刀名>）— 自动从 named_blades 目录发现
        SLBItems.init(jarPath);
        SLBItems.register(modEventBus);

        // 注册自定义 Special Effect（数据驱动，从 slb_ses.json 加载）
        modEventBus.addListener(SLBSpecialEffectsRegistry::onRegister);

        // 注册完成后打印所有命名刀的注册键和类名，用于调试
        modEventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey() == Registries.ITEM) {
                SLBItems.getNamedBlades().forEach(item -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    SLB.LOGGER.info("=== SLB Blade Registry Check ===");
                    SLB.LOGGER.info("  Blade ID : {}", item.getBladeDefinitionId());
                    SLB.LOGGER.info("  Registry  : {}", key);
                    SLB.LOGGER.info("  Item class: {}", item.getClass().getName());
                    SLB.LOGGER.info("  Is Named  : {}",
                            item instanceof com.slb.item.NamedBladeItem);
                });
            }
        });

        modEventBus.addListener(this::commonSetup);

        // 注册客户端模型处理器（仅在客户端环境下注册，避免引用 BladeModel 类）
        if (FMLEnvironment.dist.isClient()) {
            SLBClientHandler.register(modEventBus);
        }

        LOGGER.info("SLB loaded! Ready for data-driven blades with custom SEs.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("SLB common setup complete.");
        LOGGER.info("Global damage multiplier: {}", SLBConfig.globalDamageMultiplier.get());
    }
}
