# 合成表系统重构 + 燃料管理设计文档

## 目标

重构合成表编辑器，限定为可编辑的 RecipeType 白名单，新增燃料管理功能，支持 Addon 机器。

## 一、可编辑 RecipeType 白名单

### SF 内置可编辑类型（14 种）

| 名称 | Key | 槽位 | 有加工时间 |
|------|-----|------|----------|
| 强化工作台 | `slimefun:enhanced_crafting_table` | 9 | ❌ |
| 魔法工作台 | `slimefun:magic_workbench` | 9 | ❌ |
| 盔甲锻造台 | `slimefun:armor_forge` | 9 | ❌ |
| 远古祭坛 | `slimefun:ancient_altar` | 9 | ❌ |
| 矿石粉碎机 | `slimefun:ore_crusher` | 2 | ✅ |
| 磨石 | `slimefun:grind_stone` | 2 | ❌ |
| 压缩机 | `slimefun:compressor` | 2 | ✅ |
| 冶炼炉 | `slimefun:smeltery` | 2 | ✅ |
| 榨汁机 | `slimefun:juicer` | 2 | ✅ |
| 加热压力仓 | `slimefun:heated_pressure_chamber` | 2 | ✅ |
| 食品加工机 | `slimefun:food_fabricator` | 2 | ✅ |
| 食品堆肥机 | `slimefun:food_composter` | 2 | ✅ |
| 精炼机 | `slimefun:refinery` | 2 | ✅ |
| 冷冻机 | `slimefun:freezer` | 2 | ✅ |

### 不支持的类型（信息型 / 交互式 / 无 RecipeType）

- `slimefun:multiblock`, `slimefun:mob_drop`, `slimefun:barter_drop`, `slimefun:interact`, `slimefun:null`
- `slimefun:geo_miner`, `slimefun:oil_pump`, `slimefun:nuclear_reactor`, `slimefun:ore_washer`, `slimefun:gold_pan`, `slimefun:reactor`
- 无 RecipeType 的机器：坩埚、碳压机、搅拌机、台锯

### Addon 可编辑类型（运行时检测，3 种）

运行时检测以下 RecipeType key 是否存在，如果 Addon 已加载则自动加入白名单：

| 名称 | 来源 | 可能的 Key 格式 |
|------|------|----------------|
| 星系装配台 | Galactifun | `galactifun:*assembly*` 或运行时匹配 |
| 存储单元工作台 | InfinityExpansion | `infinityexpansion:*storage*workbench*` |
| 无尽工作台 | InfinityExpansion | `infinityexpansion:*infinity*workbench*` |

检测方式：遍历 `Slimefun.getRegistry()` 中所有运行时 RecipeType，按 key 前缀/模式匹配，匹配到的加入白名单。

## 二、电力机器自动兼容

SF 电力机器（AContainer 子类）通过 `getMachineIdentifier()` 和 `addRecipe(RecipeType, input, output)` 注册配方。修改基础机器的 RecipeType 配方后，电力机器会自动共享同一 RecipeType 的配方。

加工时间通过 SlimefunItem 的 `setProcessingTime()` 设置，是**配方级别**的——每个配方可以有不同的加工时间。

### 加工时间处理

- `RecipeType.setProcessingTime()` / `RecipeType.setRecipe()` 只在 AContainer 子类上生效
- 对于多方块机器（冶炼炉等），加工时间不适用（玩家手动操作）
- 前端在有加工时间的配方类型上显示加工时间输入框

## 三、燃料管理系统

### 后端：FuelApiHandler

新建 `FuelApiHandler`，处理 `/api/fuels` 相关请求。

#### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/fuel-types` | GET | 获取所有耗材型发电机及其燃料列表 |
| `/api/fuels` | PUT | 更新某个发电机的燃料列表 |

#### GET /api/fuel-types 响应结构

```json
{
  "generators": [
    {
      "id": "COAL_GENERATOR",
      "name": "§f煤炭发电机",
      "energyPerTick": 6,
      "fuels": [
        {
          "inputId": "minecraft:coal",
          "inputName": "煤炭",
          "burnTime": 16,
          "outputId": null,
          "outputName": null
        }
      ]
    }
  ]
}
```

#### PUT /api/fuels 请求结构

```json
{
  "generatorId": "COAL_GENERATOR",
  "fuels": [
    {"inputId": "minecraft:coal", "burnTime": 16, "outputId": null},
    {"inputId": "slimefun:uranium", "burnTime": 1200, "outputId": "slimefun:neptunium"}
  ]
}
```

#### 实现机制

1. 运行时扫描所有 `SlimefunItem`，找 `AbstractEnergyProvider` 子类
2. 通过 `getFuelTypes()` 读取燃料列表
3. 修改燃料时：
   - 清空原有燃料列表：`generator.getFuelTypes().clear()`
   - 重新注册：`generator.registerFuel(new MachineFuel(seconds, input, output))`
4. 变更立即生效（无需重启）

### 前端：燃料管理标签页

在合成表编辑器顶部添加标签切换：
- **标签1**: 合成表管理（现有功能）
- **标签2**: 燃料管理（新增）

#### 燃料管理页面布局

- **左侧**：发电机列表（类似物品列表的搜索+选择）
- **右侧**：
  - 发电机信息（名称、电量/tick）
  - 燃料列表表格（输入物品、燃烧时间秒数、产出物品）
  - 每行可编辑燃烧时间、删除燃料
  - 底部"添加燃料"按钮（弹出物品选择器，复用现有物品搜索 API）

## 四、槽位数量一致性保障

后端 `guessSlots()` 和前端 `getTypeInfo()` 必须返回完全相同的槽位数量。实现方式：

1. 后端 `buildRecipeTypesJson()` 在返回 `RecipeTypeInfo` 时，`slots` 字段由 `guessSlots()` 决定
2. 前端 `getTypeInfo()` 直接使用后端返回的 `recipeTypes` 数组中的 `slots` 值，不自行推断
3. `applyAllRecipes()` 使用 `guessSlots(typeKey)` 决定 `inputStacks` 数组长度
4. `saveCurrentRecipeState()` 在保存前按 `getTypeInfo(currentTypeKey).slots` 截断
5. `save()` 时再次按类型槽数截断/补全 `input` 数组
6. `defaultRecipeJson()` 按 `guessSlots(rtKey)` 截断序列化

这确保从前端保存到后端应用的全链路槽位数一致。

## 五、前端改造

### 合成表编辑器

1. **配方类型过滤**：只显示白名单中的类型 + 发现的 Addon 类型
2. **加工时间**：只在 `hasTime=true` 的类型上显示加工时间输入框
3. **加工时间粒度**：每个配方独立设置，存储在 `recipesData[idx].processingTime`

### 燃料管理页面

- 新增标签切换组件
- 复用现有的物品搜索弹窗（`/api/recipes/materials`）
- 保存按钮调用 `PUT /api/fuels`

## 六、数据流

### 合成表数据流（保持不变）

```
前端 save() → PUT /api/recipes → RecipeApiHandler.saveRecipesFromJson()
→ parseRecipeSavePayload() → applyAllRecipes() → SF 内部注册
```

### 燃料数据流（新增）

```
前端 loadFuels() → GET /api/fuel-types → FuelApiHandler.buildFuelsJson()
→ 扫描 AbstractEnergyProvider 子类 → 返回 JSON

前端 saveFuels() → PUT /api/fuels → FuelApiHandler.saveFuelsFromJson()
→ 清空+重新注册 MachineFuel → 立即生效
```

## 六、修改的文件清单

| 文件 | 改动 |
|------|------|
| `RecipeApiHandler.java` | 重构 `BUILTIN_RECIPE_TYPES` → `EDITABLE_RECIPE_TYPES` + 运行时 Addon 检测 |
| `FuelApiHandler.java` | **新建**，处理燃料管理 API |
| `WebApiHandler.java` | 注册 `FuelApiHandler` 的路由 |
| `recipes.html` | 添加标签切换、配方类型过滤、燃料管理页面 |

## 七、不做的事情

- 不支持无 RecipeType 的机器（坩埚、碳压机、搅拌机、台锯）
- 不修改信息型配方（mob_drop、barter_drop、interact）
- Addon 只支持指定的三个机器，不做全量自动发现
