# SLB — SlashBlade Resharped 附属模组

SLB 是 **NextCombat 整合包**的拔刀剑附属模组（NeoForge 1.21.1），在 SlashBlade Resharped 的框架上扩展了一套 **数据驱动的 SE（特殊效果）系统**。

**核心能力：**
- **可组合 SE 系统** — 通过 `slb_ses.json` 定义效果组合，15 种原子效果类型（药水、伤害、闪电、吸血、虚空爆炸等），支持等级惩罚
- **纯数据驱动** — 加新刀 = 写 JSON + 放模型(OBJ) + 放贴图(PNG)，无需改 Java
- **KubeJS 桥接** — 提供 `SLBKubeJSHelper` 让 KubeJS 脚本安全读写刀属性（魂量、SE 检测等）

---

## 构建 & 快速开始

| 工具 | 版本 |
|------|------|
| JDK | 21 |
| Gradle | 8.14.5（wrapper 已内置） |
| NeoForge | 1.21.1 - 21.1.235 |
| SlashBlade Resharped | ≥ 2.0.3 |

```bash
# 克隆后直接构建
./gradlew build

# 产物在 build/libs/SLB.jar
```

`SLB.jar` 放入客户端/服务端的 `mods/` 即可使用。

---

## 添加新刀（无需改 Java）

只需添加资源文件后重新打包：

```bash
1. 把 model.obj + texture.png 放入 assets/slb/model/<刀名>/
2. 创建 data/slb/slashblade/named_blades/<刀名>.json
3. 在 lang 文件中添加名称和描述（zh_cn.json / en_us.json）
4. ./gradlew build
5. 用 build/libs/SLB.jar 替换 mods/ 下的旧文件
```

> 已有 **53 把崩坏 3** 模型文件可直接使用，见 `assets/slb/model/`（需自行编写 JSON 定义）。
> 参考模板 `_example_blank.json.example` 位于 `named_blades/` 目录下（`.example` 后缀不会被 SlashBlade 加载，避免因缺少模型文件而崩溃）。

---

## Blade JSON 字段参考

### properties（属性）

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `attack_base` | float | 基础攻击力 | `12.0` |
| `max_damage` | int | 锻造耐久上限 | `150` |
| `slash_art` | string | 右键 SA | `"slashblade:sakura_end"` |
| `sword_type` | string[] | 剑类型 | `["bewitched"]` |
| `special_effects` | string[] | SE 列表 | `["slb:swift_edge"]` |

**sword_type 可选值：** `bewitched`（妖刀/可成长）、`enchanted`（附魔光泽）、`sealed`（需解封）

**常用 slash_art：** `drive_vertical` · `drive_horizontal` · `wave_edge` · `circle_slash` · `sakura_end` · `judgement_cut`

### render（渲染）

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `model` | string | OBJ 模型路径 | `"slb:model/xxx/model.obj"` |
| `texture` | string | PNG 贴图路径 | `"slb:model/xxx/texture.png"` |
| `carry_type` | string | 携带方式 | `"katana"` |
| `summon_sword_color` | hex | 刀光颜色 | `0xFFFF69B4` |

**carry_type：** `katana`（腰间）、`ninja`（背后）、`pso2`（悬浮）

### enchantments（附魔）

```json
[
  { "id": "minecraft:sharpness", "lvl": 5 },
  { "id": "minecraft:unbreaking", "lvl": 3 }
]
```

### 语言文件

```json
{
  "item.slb.<刀名>": "刀的名称",
  "item.slb.<刀名>.desc": "§b描述文字"
}
```

---

## Special Effect（SE）系统

所有 SE 通过 `slb_ses.json` 定义，由原子效果组件组合而成，无需编写 Java 代码即可创建新的 SE。

```
slb_ses.json → RegisterEvent（动态注册）→ SLBSEEventHandler（事件分发）→ EffectHandlers（效果执行）
```

### 原子效果类型

| type | 说明 | 关键参数 |
|------|------|---------|
| `potion` | 对持有者施加状态效果 | `id`, `amplifier`, `duration`, `trigger` |
| `potion_target` | 对目标施加状态效果（命中时） | `id`, `amplifier`, `duration`, `chance` |
| `fire_target` | 点燃目标 | `seconds`, `chance` |
| `damage` | 额外伤害（支持条件判断） | `ratio`, `condition`, `chance` |
| `lightning` | 召唤闪电 | `chance`, `double_in_rain` |
| `life_steal` | 吸血（造成伤害的百分比） | `ratio` |
| `heal_on_kill` | 击杀回复固定生命 | `amount` |
| `double_strike` | 概率二连击 | `chance`, `ratio` |
| `void_tear` | 虚空撕裂（真实伤害） | `chance`, `damage_ratio` |
| `void_explosion` | 虚空爆炸（范围真实伤害） | `chance`, `damage_ratio`, `radius` |
| `aoe_potion` | 范围状态效果 | `id`, `amplifier`, `duration`, `radius` |
| `sonar_pulse` | 声呐脉冲 | `range`, `duration` |
| `proud_soul_mult` | 荣耀之魂获取倍率 | `multiplier` |
| `kill_count_mult` | 击杀计数倍率 | `multiplier` |
| `explosion_break` | 刀损坏时爆炸 | `damage_ratio`, `power`, `radius` |

#### trigger（触发时机）

| 值 | 说明 |
|----|------|
| `held` | 手持刀时每 tick |
| `hit` | 击中目标时（默认） |
| `slashart` | 发动 SA 时 |
| `kill` | 击杀时 |
| `ps` | 获得荣耀之魂时 |

#### condition（触发条件，仅 damage 类型可用）

| 值 | 效果 |
|----|------|
| `always` | 总是触发（默认） |
| `target_on_fire` | 目标燃烧时 |
| `target_frozen` | 目标冰冻时 |
| `full_health` | 目标满血时 |

### 惩罚效果（Penalty）

等级不足时（玩家经验 < `requestLevel`），除了 SE 自身效果不生效外，还可以通过 `penalty` 数组自动施加负面效果。格式与 `effects` 完全一致：

```json
"swift_edge": {
  "requestLevel": 8,
  "effects": [
    { "type": "potion", "id": "minecraft:speed", "trigger": "held" }
  ],
  "penalty": [
    { "type": "potion", "id": "minecraft:slowness", "amplifier": 0, "duration": 100, "trigger": "held" }
  ]
}
```

#### penalty 专用效果类型

| type | 效果 | 参数 |
|:-----|:-----|:-----|
| `potion` | 对玩家施加负面药水 | `id`, `amplifier`, `duration`, `trigger` |
| `self_ignite` | 点燃玩家自身 | `seconds`(默认3), `interval_ticks`, `trigger` |
| `self_lightning` | 雷劈玩家自身 | `interval_ticks`, `trigger` |
| `self_damage` | 攻击时自伤 | `amount`(默认1.0), `trigger` |
| `block_ps` | 阻止获得荣耀之魂 | `trigger` |

> `interval_ticks`：设置间隔刻数（20刻=1秒），避免每 tick 重复触发。如 `interval_ticks: 60` = 每3秒触发一次。
> `block_ps` 仅支持 `trigger: "ps"`。

### 自定义 SE 示例

在 `slb_ses.json` 的 `ses` 对象中添加：

```json
"my_heal_se": {
  "requestLevel": 5,
  "isCopiable": true,
  "isRemovable": true,
  "effects": [
    { "type": "potion", "id": "minecraft:regeneration", "amplifier": 1, "trigger": "held" },
    { "type": "potion", "id": "minecraft:resistance", "amplifier": 0, "trigger": "held" },
    { "type": "life_steal", "ratio": 0.05, "trigger": "hit" }
  ],
  "penalty": [
    { "type": "potion", "id": "minecraft:weakness", "amplifier": 0, "trigger": "held" }
  ]
}
```

然后在 blade JSON 中引用 `"special_effects": ["slb:my_heal_se"]`。

### 内置 SE

源码 `slb_ses.json` 仅含 `_example` 占位。完整 15 个 SE 定义在 `整合包研究/设计文档/slb_ses.json`，通过 `scripts/add_ses.py` 注入 jar 后生效。

SE 描述文字通过语言文件定义，格式为 `se.slb.<se_name>`。

---

## 项目结构

```
SLB/
├── build.gradle                          ← NeoGradle 7.1.38
├── settings.gradle                       ← 项目名 + 插件仓库
├── gradle.properties                     ← 版本号常量
├── gradlew / gradlew.bat                ← Gradle wrapper
├── libs/                                 ← SlashBlade Resharped jar（编译依赖）
└── src/main/
    ├── java/com/slb/
    │   ├── SLB.java                      ← @Mod 主类
    │   ├── config/SLBConfig.java         ← config/slb.json5
    │   ├── kubejs/
    │   │   └── SLBKubeJSHelper.java      ← KubeJS 桥接层（SE检测 + 刀属性读取）
    │   ├── registry/
    │   │   ├── SLBSpecialEffectsRegistry.java  ← SE 数据加载 + 动态注册
    │   │   └── SLBSEEventHandler.java         ← SE 事件统一分发
    │   └── specialeffect/
    │       ├── CompositeSE.java           ← 通用 SE 类（效果组件容器）
    │       └── EffectHandlers.java        ← 原子效果类型实现
    └── resources/
        ├── slb_ses.json                  ← SE 定义（用户可编辑）
        ├── META-INF/neoforge.mods.toml
        ├── pack.mcmeta
        ├── assets/slb/
        │   ├── lang/{zh_cn,en_us}.json   ← 语言文件
        │   └── model/                    ← OBJ + PNG 模型
        └── data/slb/slashblade/named_blades/
            ├── _example_blank.json.example  ← 参考模板
            └── HOW_TO_ADD_A_BLADE.md       ← 使用说明
```

---

## 源码说明

### SLB.java（主类）

- `@Mod("slb")` — NeoForge 入口
- 构造阶段注册 `SLBConfig`（`ModConfig.Type.COMMON` → `config/slb.json5`）
- 注册 `SLBSpecialEffectsRegistry::onRegister` 监听 RegisterEvent，从 `slb_ses.json` 动态注册自定义 SE
- 所有命名刀由 SlashBlade Resharped 通过 datapack 自动发现，无需 Java 注册

### SLBConfig.java

使用 NeoForge 的 `ModConfigSpec` 实现，首次运行后生成 `config/slb.json5`：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `damage.globalDamageMultiplier` | double | 1.0 | 全局伤害倍率（0.1~10.0） |
| `sword_type.enableBewitchedByDefault` | boolean | true | 默认是否妖刀 |
| `debug.logDiscoveredBlades` | boolean | true | 启动时列出所有发现的 SLB 刀 |

---

## 调试与排错

### JSON 格式常见错误

修改 Blade JSON 或 SE 定义时，格式错误会导致游戏崩溃：

| # | ❌ 错误写法 | 问题 | ✅ 正确写法 |
|---|------------|------|------------|
| 1 | `"a": 1,`<br>`"b": 2,` | 尾随逗号 | `"a": 1,`<br>`"b": 2` |
| 2 | `"a": 1`<br>`"b": 2` | 缺少逗号 | `"a": 1,`<br>`"b": 2` |
| 3 | `'key': 'value'` | 用了单引号 | `"key": "value"` |
| 4 | `key: "value"` | key 没加引号 | `"key": "value"` |
| 5 | `"count": "5"` | 数字写成字符串 | `"count": 5` |
| 6 | `"path\\file.obj"` | 反斜杠 | `"path/file.obj"` |
| 7 | `"type": slashblade:drive` | 字符串漏引号 | `"type": "slashblade:drive"` |
| 8 | `"val": 12,0` | 逗号当小数点 | `"val": 12.0` |
| 9 | `// comment` | JSON 不支持注释 | 删除注释 |
| 10 | `{}` 不配对 | 花括号数不一致 | 检查 `{` `}` 数量 |

**校验工具：** 编辑后粘贴到 [JSONLint](https://jsonlint.com/) 检查。

---

## 外部扩展方式（无需重新编译）

- **数据包：** 将 `data/slb/` 打包放入 `world/datapacks/`
- **KubeJS：** `ServerEvents.generateData` 注入 Blade JSON
- **资源包：** 覆盖 `assets/slb/model/` 下的 OBJ/PNG

---

## KubeJS 桥接层（SLBKubeJSHelper）

### 为什么需要桥接层？

SLB 的 SE 系统通过 `slb_ses.json` 的原子效果组合实现了大部分功能，但有一些进阶效果超出了纯 JSON 的能力范围：

| SE | 需要运行时逻辑 | 纯 JSON 做不到的原因 |
|:---|:---------------|:--------------------|
| **SoulEdge** 动态魂量增伤 | 实时读取刀的荣耀之魂数量 | JSON 没有"读变量"的能力 |
| **HunterEdge** 猎人印记 | 给目标挂标记、刷新持续时间、伤害前拦截加成 | 需要跨实体状态追踪 |
| **StormEdge** 风暴领域 | 每 tick 检测附近敌人并造成范围伤害 | 需要循环 + 范围判定 |
| **MyriadEdge** 三千连击 | 概率触发连环额外伤害 | 需要循环计数 |
| **BulwarkEdge** 紧急吸收 | 低血量时触发、冷却计时 | 需要条件分支 + 计时器 |
| **ThunderEdge** 闪电免疫 | 检测伤害来源类型 | 需要事件拦截 |

这些逻辑用 **KubeJS**（`PlayerEvents.tick`、`EntityEvents.hurt` 等）写起来正合适。但 KubeJS 的脚本引擎 **Rhino** 在处理 Java 泛型、`Optional`、嵌套集合时容易出问题。

`SLBKubeJSHelper` 就是解决这个问题的——把 SLB 和 SlashBlade 的 Java API 封装成简单直接的静态方法，KubeJS 脚本一行调用即可。

### 类路径

```
com.slb.kubejs.SLBKubeJSHelper
```

在 KubeJS 中加载：

```javascript
const SlbApi = Java.loadClass('com.slb.kubejs.SLBKubeJSHelper');
```

### 方法一览

#### SE 检测

| 方法 | 返回 | 说明 |
|:-----|:-----|:-----|
| `hasSE(stack, seName)` | `boolean` | 检查刀上是否有指定 SE（自动加 `slb:` 前缀） |
| `getSEList(stack)` | `List<String>` | 获取刀上所有 SLB 命名空间的 SE 名称列表 |

**KubeJS 示例：**

```javascript
const SlbApi = Java.loadClass('com.slb.kubejs.SLBKubeJSHelper');

// 检查手上刀有没有 HunterEdge
EntityEvents.hurt(event => {
    let { entity, source, damage } = event;
    let player = source.player;
    if (!player) return;

    if (SlbApi.hasSE(player.mainHandItem, 'hunter_edge')) {
        // 给目标挂猎人印记：记录标记时间 + 标记来源
        entity.persistentData.hunterMark = {
            source: player.uuid,
            time: player.level.gameTime,
            duration: 100 // 5 秒
        };
    }
});

// 检查刀上有什么 SE（调试用）
PlayerEvents.tick(event => {
    let { player } = event;
    let ses = SlbApi.getSEList(player.mainHandItem);
    if (ses.length > 0) {
        console.log('Current SEs: ' + ses.join(', '));
    }
});
```

#### 刀属性读取

| 方法 | 返回 | 用途 |
|:-----|:-----|:-----|
| `getProudSouls(stack)` | `int` | 读取荣耀之魂数量（SoulEdge 动态增伤） |
| `getBaseAttack(stack)` | `float` | 读取基础攻击力（计算百分比伤害） |
| `getKillCount(stack)` | `int` | 读取击杀计数 |
| `getRefine(stack)` | `int` | 读取锻造等级 |

**KubeJS 示例：**

```javascript
const SlbApi = Java.loadClass('com.slb.kubejs.SLBKubeJSHelper');

// SoulEdge：每 10 魂提供 +1% 伤害，上限 30%
PlayerEvents.tick(event => {
    let { player } = event;
    let souls = SlbApi.getProudSouls(player.mainHandItem);
    let bonus = Math.min(30, Math.floor(souls / 10));
    // bonus 就是增伤百分比
});

// MyriadEdge：20% 概率触发 3×25% 面板伤害
EntityEvents.hurt(event => {
    let { entity, source } = event;
    let player = source.player;
    if (!player) return;

    if (!SlbApi.hasSE(player.mainHandItem, 'myriad_edge')) return;
    if (Math.random() > 0.2) return;

    let baseAtk = SlbApi.getBaseAttack(player.mainHandItem);
    let extraDmg = baseAtk * 0.25;
    for (let i = 0; i < 3; i++) {
        entity.hurt('slb:myriad_edge', extraDmg);
    }
});
```

### 安全保证

| 场景 | 行为 |
|:-----|:-----|
| `stack` 为 `null` | 返回安全默认值（`false` / `0` / `0.0f` / `List.of()`） |
| `stack` 为空物品 | 同上 |
| 非拔刀剑物品 | 同上（`BladeStateAccess.of()` 返回 empty） |
| SlashBlade API 异常 | 捕获异常，打 warn 日志，返回安全默认值 |
| 没有安装 KubeJS | 完全不受影响——类编译在 SLB.jar 内，无人调用即无事发生 |

### 与纯 JSON SE 协作

桥接层和 JSON 定义的 SE **互不冲突**。同一把刀上的 SE 可以部分由 JSON 处理、部分由 KubeJS 处理：

```
刀上 SE 列表：
  ├─ gleam_edge     ← JSON atomic type 处理（damage + potion）
  ├─ soul_edge      ← JSON 处理 proud_soul_mult 倍率
  └─ soul_edge      ← KubeJS 处理 动态魂量增伤（通过桥接层读魂量）
                     ↑
                 两者并存，各管各的逻辑
```

JSON 负责"简单、高频、通用"的效果，KubeJS + 桥接层负责"需要条件判断/状态追踪"的进阶效果。

---

## 许可

All Rights Reserved. 仅供学习交流。
