package com.slb.registry;

import com.slb.SLB;
import com.slb.item.NamedBladeItem;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * SLBClientHandler — 客户端模型注册
 *
 * 确保所有 NamedBladeItem 使用 BladeModel（SlashBlade 的自定义 OBJ 渲染模型），
 * 覆盖 Minecraft 默认的 builtin/entity 渲染，使 BladeStateData 中指定的 model / texture 正确显示。
 *
 * 核心原理：
 *   SlashBlade 的 ClientHandler.bakeBlade() 只注册了 5 个默认物品，
 *   且 onRegisterClientExtensions 也只给 5 个默认物品注册了 SlashBladeTEISR，
 *   我们的 NamedBladeItem 需要自己注册 Both BladeModel 包装器和 TEISR。
 *
 * 此类的 register 方法由 SLB.java 在客户端环境下调用（通过 modEventBus）。
 */
public class SLBClientHandler {

    // 延迟初始化 SlashBladeTEISR（需要客户端环境就绪后才能创建）
    private static BlockEntityWithoutLevelRenderer teisr;

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SLBClientHandler::onModelBake);
        modEventBus.addListener(SLBClientHandler::onRegisterClientExtensions);
    }

    /**
     * 为每个 NamedBladeItem 注册 IClientItemExtensions（SlashBladeTEISR），
     * 使 builtin/entity 模型能触发 OBJ 自定义渲染。
     *
     * 这是修复 JEI/GUI 中显示为透明的关键步骤。
     */
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        int count = 0;
        for (NamedBladeItem item : SLBItems.getNamedBlades()) {
            event.registerItem(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (teisr == null) {
                        Minecraft mc = Minecraft.getInstance();
                        teisr = new SlashBladeTEISR(
                            mc.getBlockEntityRenderDispatcher(),
                            mc.getEntityModels()
                        );
                    }
                    return teisr;
                }
            }, item);
            count++;
        }
        if (count > 0) {
            SLB.LOGGER.info("Registered SlashBladeTEISR for {} NamedBladeItem(s)", count);
        }
    }

    /**
     * 模型烘焙完成后，为每个 NamedBladeItem 注册 BladeModel 包装器。
     *
     * 注意：即使刀已经有物品模型 JSON（assets/slb/models/item/<刀名>.json），
     * 也需要用 BladeModel 替换，否则 builtin/entity 无法触发 OBJ 渲染。
     */
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        int count = 0;
        for (NamedBladeItem item : SLBItems.getNamedBlades()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            ModelResourceLocation modelLoc = ModelResourceLocation.inventory(itemId);

            // 获取现有的 baked model（来自物品模型 JSON，如 yamazakura_aoi.json）
            BakedModel existingModel = event.getModels().get(modelLoc);
            if (existingModel == null) {
                SLB.LOGGER.warn("No baked model for '{}' — missing assets/slb/models/item/{}.json?", itemId, itemId.getPath());
                continue;
            }

            // 用 BladeModel 包装（SlashBlade 的 OBJ 渲染器）
            event.getModels().put(modelLoc, new BladeModel(existingModel, event.getModelBakery()));
            count++;
        }

        if (count > 0) {
            SLB.LOGGER.info("Registered BladeModel wrappers for {} NamedBladeItem(s)", count);
        }
    }
}
