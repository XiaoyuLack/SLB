# 如何添加一把新刀

1. 在 `assets/slb/model/` 下新建文件夹（如 `my_sword/`），放入 `model.obj` + `texture.png`
2. 在此目录新建 `<刀名>.json`，参考 `_example_blank.json` 格式
3. 在 `assets/slb/lang/zh_cn.json` 添加翻译：`"item.slb.<刀名>": "名称"`
4. 保存 jar，进游戏即可

> ⚠️ 这个目录只放 .json 文件。此 .md 文件会被 SlashBlade 忽略并报 `Invalid path` 警告，不影响使用。
