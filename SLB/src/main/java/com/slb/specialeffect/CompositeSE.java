package com.slb.specialeffect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * CompositeSE — 通用组合型 SpecialEffect
 *
 * 所有通过 JSON 定义的自定义 SE 都使用本类。
 * 其行为由 effects 列表中的原子效果组件决定。
 * 具体效果由 EffectHandlers 分派执行。
 */
public class CompositeSE extends SpecialEffect {

    private final List<JsonObject> effects = new ArrayList<>();
    private final List<JsonObject> penalties = new ArrayList<>();

    public CompositeSE(int requestLevel, boolean isCopiable, boolean isRemovable,
                       JsonArray effectsArray, JsonArray penaltyArray) {
        super(requestLevel, isCopiable, isRemovable);
        if (effectsArray != null) {
            for (JsonElement elem : effectsArray) {
                effects.add(elem.getAsJsonObject());
            }
        }
        if (penaltyArray != null) {
            for (JsonElement elem : penaltyArray) {
                penalties.add(elem.getAsJsonObject());
            }
        }
    }

    public List<JsonObject> getEffects() {
        return effects;
    }

    public List<JsonObject> getPenalties() {
        return penalties;
    }
}
