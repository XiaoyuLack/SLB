package com.slb.item;

import com.slb.SLB;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateData;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ItemTierSlashBlade;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NamedBladeItem — 命名刀独立物品
 *
 * 每把在 data/slb/slashblade/named_blades/ 中定义的命名刀对应一个独立 Item，
 * 注册在 slb:<刀名> 命名空间下。
 *
 * 继承 ItemSlashBlade，确保拔刀剑的所有渲染和战斗功能正常。
 * bladeState 数据在首次获得时通过 PlayerTickEvent 自动注入。
 */
public class NamedBladeItem extends ItemSlashBlade {

    private final ResourceLocation bladeDefinitionId;

    public NamedBladeItem(ResourceLocation bladeDefinitionId, Properties properties) {
        super(new ItemTierSlashBlade(40, 4F), 4, 0.0F, properties);
        this.bladeDefinitionId = bladeDefinitionId;
    }

    @SuppressWarnings("unused")
    public ResourceLocation getBladeDefinitionId() {
        return bladeDefinitionId;
    }

    /**
     * 从世界 registry 获取对应的 SlashBladeDefinition
     */
    @Nullable
    public SlashBladeDefinition getDefinition(@Nullable Level level) {
        if (level == null) return null;
        try {
            var registry = SlashBlade.getSlashBladeDefinitionRegistry(level);
            return registry.get(bladeDefinitionId);
        } catch (Exception e) {
            SLB.LOGGER.warn("Failed to get blade definition '{}': {}", bladeDefinitionId, e.getMessage());
            return null;
        }
    }

    /**
     * 检查 ItemStack 是否已注入 bladeState
     * BladeStateData.translationKey 不为空说明已被配置过
     */
    public static boolean isConfigured(ItemStack stack) {
        return BladeStateAccess.getData(stack)
                .map(data -> !data.translationKey().isEmpty())
                .orElse(false);
    }

    /**
     * 获取显示用翻译键（无栈版本）。
     * 直接用 bladeDefinitionId 构造 item.slb.<刀名> 翻译键，
     * 不依赖 Level / BladeStateData — JEI/render 等无 Level 上下文处也能正确工作。
     */
    @Override
    public String getDescriptionId() {
        return Util.makeDescriptionId("item", bladeDefinitionId);
    }

    /**
     * 获取显示用翻译键（ItemStack 版本）。
     * 已配置的栈优先从 BladeStateData 读取，未配置时回退到无栈版本。
     */
    @Override
    public String getDescriptionId(ItemStack stack) {
        String fromData = BladeStateAccess.getData(stack)
                .map(BladeStateData::translationKey)
                .filter(key -> !key.isEmpty())
                .orElse(null);
        if (fromData != null) return fromData;

        return getDescriptionId();
    }

    /**
     * 显示名称（ItemStack 版本）。
     * 覆写 ItemSlashBlade 的 getName，确保 JEI/F3+H 等场景显示正确的翻译键。
     */
    @Override
    public @NotNull Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack));
    }

    /**
     * 所属模组 ID。
     * 覆写 ItemSlashBlade.getCreatorModId（它通过 getBladeId 推断 namespace，
     * 可能受 BladeStateData 影响），始终返回 "slb"。
     */
    @Override
    public String getCreatorModId(ItemStack stack) {
        return SLB.MOD_ID;
    }

    /**
     * 刀 ID。覆写 ItemSlashBlade.getBladeId，直接返回注册时保存的 bladeDefinitionId
     * （slb:<刀名>），不依赖 BLADESTATE 能力系统。
     * 这个值被用于 getCreatorModId、以及其他 SlashBlade 内部逻辑。
     */
    @Override
    public ResourceLocation getBladeId(ItemStack stack) {
        return bladeDefinitionId;
    }

    /**
     * 从 SlashBladeDefinition 复制 BladeStateData 到指定的 ItemStack。
     * 用于 PlayerTickEvent 中初始化 /give 获得的空白栈。
     *
     * @return true 如果成功配置
     */
    public static boolean configureStack(ItemStack stack, Level level) {
        if (!(stack.getItem() instanceof NamedBladeItem wrapper)) return false;
        if (isConfigured(stack)) return true;

        SlashBladeDefinition def = wrapper.getDefinition(level);
        if (def == null) {
            SLB.LOGGER.debug("Blade definition '{}' not yet available, will retry", wrapper.bladeDefinitionId);
            return false;
        }

        try {
            // 用 definition 生成已配置的 blade
            var provider = level.registryAccess();
            ItemStack properBlade = def.getBlade(provider);

            // 复制 BladeStateData 数据组件（用于渲染等）
            BladeStateAccess.getData(properBlade).ifPresentOrElse(data -> {
                BladeStateAccess.setData(stack, data);
                BladeStateAccess.ensureRuntimeComponent(stack);
                SLB.LOGGER.debug("  Copied BladeStateData, translationKey='{}'", data.translationKey());
            }, () -> {
                SLB.LOGGER.warn("  No BladeStateData on properBlade, configureStack may be incomplete");
            });

            SLB.LOGGER.info("Configured blade stack: {}", wrapper.bladeDefinitionId);
            return true;
        } catch (Exception e) {
            SLB.LOGGER.warn("Failed to configure blade '{}': {}", wrapper.bladeDefinitionId, e.getMessage());
            return false;
        }
    }
}
