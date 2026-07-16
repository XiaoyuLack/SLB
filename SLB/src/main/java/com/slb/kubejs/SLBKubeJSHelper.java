package com.slb.kubejs;

import com.slb.SLB;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * SLBKubeJSHelper — KubeJS 桥接层
 *
 * ═══════════════════════════════════════════════════════════════════
 *  这个类是干什么的？
 * ═══════════════════════════════════════════════════════════════════
 *
 * SLB 的 SE（Special Effect）系统目前支持通过 slb_ses.json
 * 配置原子效果（药水、额外伤害、二连击等），但有一些进阶效果
 * 用纯 JSON 无法实现，需要运行时读取刀的状态并执行自定义逻辑，
 * 比如：
 *   • SoulEdge — 按荣耀之魂数量动态加成伤害
 *   • HunterEdge — 给目标挂标记，下次攻击增伤
 *   • StormEdge — 每 tick 检测周围敌人并造成范围伤害
 *   • MyriadEdge — 概率触发连环额外伤害
 *   • BulwarkEdge — 低血量时自动触发吸收护盾
 *   • ThunderEdge — 手持时免疫闪电伤害
 *
 * KubeJS 正好适合做这些"写一点逻辑但又不需要改模组"的事。
 * 但 KubeJS 的脚本引擎 Rhino 在处理 Java 泛型、Optional 和
 * 嵌套类型时很容易出错或写得很啰嗦。
 *
 * 这个桥接层把复杂的 Java API 调用封装成简单的静态方法，
 * KubeJS 脚本调用时就像调用普通 JS 函数一样简单。
 *
 * ═══════════════════════════════════════════════════════════════════
 *  没有 KubeJS 会怎样？
 * ═══════════════════════════════════════════════════════════════════
 *
 * 完全不影响。这个类本身是纯 Java，是 SLB 模组的一部分，
 * 不依赖 KubeJS 的任何类。没有 KubeJS 时它只是编译在 jar 里
 * 但永远不会被调用，不会触发任何异常。
 *
 * ═══════════════════════════════════════════════════════════════════
 *  null/空安全
 * ═══════════════════════════════════════════════════════════════════
 *
 * 所有方法都接受 null 或空 ItemStack，返回安全默认值
 * （false / 0 / 空列表 / 0.0f），不会抛出 NullPointerException。
 * 内部 try-catch 兜底，极端情况只打 warn 日志，不崩游戏。
 *
 * ═══════════════════════════════════════════════════════════════════
 *  KubeJS 调用示例
 * ═══════════════════════════════════════════════════════════════════
 *
 *   // ── 在 server_scripts/xxx.js 中 ──
 *
 *   const SlbApi = Java.loadClass('com.slb.kubejs.SLBKubeJSHelper');
 *
 *   // ① 检查手上刀有没有 HunterEdge
 *   if (SlbApi.hasSE(player.mainHandItem, 'hunter_edge')) {
 *       player.tell('你的刀有猎人印记效果');
 *   }
 *
 *   // ② 列出刀上所有 SLB 自定义 SE
 *   let ses = SlbApi.getSEList(player.mainHandItem);
 *   ses.forEach(name => console.log('SE: ' + name));
 *
 *   // ③ 读荣耀之魂（SoulEdge 动态增伤用）
 *   let souls = SlbApi.getProudSouls(player.mainHandItem);
 *   let bonus = Math.min(30, Math.floor(souls / 10));
 *
 *   // ④ 读基础攻击力（计算伤害百分比用）
 *   let atk = SlbApi.getBaseAttack(player.mainHandItem);
 */
public final class SLBKubeJSHelper {

    private SLBKubeJSHelper() {}

    // ══════════════════════════════════════════════════════════════
    //  内部辅助
    // ══════════════════════════════════════════════════════════════

    /**
     * 从 ItemStack 获取 ISlashBladeState。
     * <p>
     * 内部方法，所有 public 方法通过此入口获取刀状态。
     * 使用 BladeStateAccess.of() 而非旧版 capability API，
     * 兼容 SlashBlade Resharped 2.0+ 的数据组件系统。
     *
     * @param stack 物品栈（null 安全）
     * @return Optional，空物品/非拔刀剑/异常时返回 empty
     */
    private static Optional<ISlashBladeState> getState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        try {
            return BladeStateAccess.of(stack);
        } catch (Exception e) {
            // SlashBlade API 变动时降级，不崩游戏
            SLB.LOGGER.warn("SLBKubeJSHelper: getState failed for {}", stack);
            return Optional.empty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SE 检测
    // ══════════════════════════════════════════════════════════════

    /**
     * 检查拔刀剑上是否有指定的 Special Effect。
     * <p>
     * 内部使用 {@link ISlashBladeState#hasSpecialEffect(ResourceLocation)}，
     * 自动组装 "slb:" + seName 作为完整 ID。
     *
     * @param stack  拔刀剑物品栈
     * @param seName SE 短名称，不含命名空间，如 "hunter_edge"、"soul_edge"
     * @return true 如果刀上存在该 SE（且 seName 非空）
     */
    public static boolean hasSE(ItemStack stack, String seName) {
        if (seName == null || seName.isEmpty()) return false;

        Optional<ISlashBladeState> opt = getState(stack);
        if (opt.isEmpty()) return false;

        try {
            ResourceLocation seId = ResourceLocation.parse("slb:" + seName);
            return opt.get().hasSpecialEffect(seId);
        } catch (Exception e) {
            SLB.LOGGER.warn("SLBKubeJSHelper: hasSE('{}') error: {}", seName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取拔刀剑上所有 SLB 命名空间的 SE 名称列表。
     * <p>
     * 自动过滤掉非 "slb:" 命名空间的 SE（如原版 SlashBlade 内置的
     * "flammpfeil:..." 系列），只返回 SLB 自定义 SE 的名称。
     *
     * @param stack 拔刀剑物品栈
     * @return SE 名称列表，如 ["gleam_edge", "soul_edge"]，不可修改
     */
    public static List<String> getSEList(ItemStack stack) {
        Optional<ISlashBladeState> opt = getState(stack);
        if (opt.isEmpty()) return List.of();

        try {
            Collection<ResourceLocation> ses = opt.get().getSpecialEffects();
            if (ses == null || ses.isEmpty()) return List.of();

            List<String> result = new ArrayList<>();
            for (ResourceLocation se : ses) {
                if ("slb".equals(se.getNamespace())) {
                    result.add(se.getPath());
                }
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            SLB.LOGGER.warn("SLBKubeJSHelper: getSEList error: {}", e.getMessage());
            return List.of();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  刀属性读取
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取刀的荣耀之魂（Proud Souls）数量。
     * <p>
     * 用于 SoulEdge 的动态魂量增伤：
     *   增伤% = min(30, proudSouls / 10)
     *
     * @param stack 拔刀剑物品栈
     * @return 荣耀之魂数量（≥ 0）
     */
    public static int getProudSouls(ItemStack stack) {
        return getState(stack)
                .map(ISlashBladeState::getProudSoulCount)
                .orElse(0);
    }

    /**
     * 获取刀的基础攻击力（BaseAttackModifier）。
     * <p>
     * 这是刀在 JSON 中定义的 attack_base 值（含锻造加成前）。
     * 用于计算百分比伤害，如 MyriadEdge 每次 25% 面板伤害：
     *   extra = baseAtk * 0.25
     *
     * @param stack 拔刀剑物品栈
     * @return 基础攻击力（≥ 0）
     */
    public static float getBaseAttack(ItemStack stack) {
        return getState(stack)
                .map(ISlashBladeState::getBaseAttackModifier)
                .orElse(0.0f);
    }

    /**
     * 获取刀的击杀计数（Kill Count）。
     *
     * @param stack 拔刀剑物品栈
     * @return 击杀数（≥ 0）
     */
    public static int getKillCount(ItemStack stack) {
        return getState(stack)
                .map(ISlashBladeState::getKillCount)
                .orElse(0);
    }

    /**
     * 获取刀的锻造等级（Refine）。
     *
     * @param stack 拔刀剑物品栈
     * @return 锻造等级（≥ 0）
     */
    public static int getRefine(ItemStack stack) {
        return getState(stack)
                .map(ISlashBladeState::getRefine)
                .orElse(0);
    }
}
