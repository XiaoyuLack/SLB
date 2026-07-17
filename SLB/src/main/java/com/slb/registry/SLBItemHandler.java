package com.slb.registry;

import com.slb.SLB;
import com.slb.item.NamedBladeItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * SLBItemHandler — 命名刀物品初始化处理器
 *
 * 在玩家 tick 时检测未初始化的 NamedBladeItem 栈，
 * 从 SlashBladeDefinition registry 读取定义并写入 BladeStateData 组件。
 *
 * 解决 /give @s slb:xxx 拿到空白刀的问题。
 */
@EventBusSubscriber(modid = SLB.MOD_ID)
public class SLBItemHandler {

    @SubscribeEvent
    public static void onPlayerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        var inventory = player.getInventory();
        for (int idx = 0; idx < inventory.getContainerSize(); idx++) {
            var stack = inventory.getItem(idx);
            if (stack.isEmpty()) continue;

            if (!(stack.getItem() instanceof NamedBladeItem)) continue;
            if (NamedBladeItem.isConfigured(stack)) continue;

            if (NamedBladeItem.configureStack(stack, player.level())) {
                SLB.LOGGER.info("Configured NamedBladeItem for player {}",
                        player.getName().getString());
            }
        }
    }
}
