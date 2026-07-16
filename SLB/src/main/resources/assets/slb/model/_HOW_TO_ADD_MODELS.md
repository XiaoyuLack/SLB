# 如何添加模型

每把刀一个目录，目录名 = 刀名，内含一个 .obj 和一个 .png：

```
assets/slb/model/
├── example_blank/
│   ├── model.obj        ← Wavefront OBJ 格式
│   └── texture.png      ← PNG 贴图（建议 512x512 或 1024x1024）
├── sakura_ren/
│   ├── model.obj
│   └── texture.png
└── shadow_fang/
    ├── model.obj
    └── texture.png
```

然后在 JSON 中引用路径为：
- `"model": "slb:model/刀名/model.obj"`
- `"texture": "slb:model/刀名/texture.png"`

---

## OBJ 文件注意事项

1. 坐标轴：Minecraft 使用 Y-UP 坐标系
2. 材质引用：OBJ 文件中的 .mtl 引用会被忽略，贴图由 JSON 中的 `texture` 字段控制
3. 面朝向：确保面法线朝外，否则可能在游戏中看不到模型
4. 不宜过大：一个 OBJ 文件控制在 500KB 以内比较合适

## PNG 贴图注意事项

1. 尺寸建议为 2 的幂：256x256, 512x512, 1024x1024
2. 背景透明：刀身以外的部分请保留透明背景
3. 颜色空间：sRGB 即可
