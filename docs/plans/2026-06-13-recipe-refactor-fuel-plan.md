# 合成表系统重构 + 燃料管理 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构合成表编辑器为白名单模式（14种SF内置+3种Addon），新增燃料管理标签页

**Architecture:** 后端 `RecipeApiHandler` 重构白名单 + 新建 `FuelApiHandler` 处理燃料 API；前端 `recipes.html` 添加配方类型过滤和燃料管理标签页

**Tech Stack:** Java 21, Bukkit/Spigot API, Slimefun4 API, GSON, JDK HttpServer, HTML/CSS/JS

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `RecipeApiHandler.java` | 配方 API（GET/PUT），白名单过滤，Addon 检测 |
| `FuelApiHandler.java` | **新建**，燃料 API（GET/PUT），发电机扫描 |
| `WebServer.java` | 注册 FuelApiHandler 路由 |
| `recipes.html` | 前端：标签切换 + 配方类型过滤 + 燃料管理页面 |

---

### Task 1: RecipeApiHandler 白名单重构

**Files:**
- Modify: `RecipeApiHandler.java`

- [ ] **Step 1: 重构常量集合**

将 `BUILTIN_RECIPE_TYPES` 重命名为 `EDITABLE_RECIPE_TYPES`，只保留 14 种可编辑类型。

将 `TIMED_RECIPE_TYPES` 保留不变（决定哪些类型支持加工时间）。

新增 `EDITABLE_RECIPE_TYPES` 内容：
```java
private static final Set<String> EDITABLE_RECIPE_TYPES = new LinkedHashSet<>();
static {
    // 9格工作台类
    EDITABLE_RECIPE_TYPES.add("slimefun:enhanced_crafting_table");
    EDITABLE_RECIPE_TYPES.add("slimefun:magic_workbench");
    EDITABLE_RECIPE_TYPES.add("slimefun:armor_forge");
    EDITABLE_RECIPE_TYPES.add("slimefun:ancient_altar");
    // 2格机器类
    EDITABLE_RECIPE_TYPES.add("slimefun:ore_crusher");
    EDITABLE_RECIPE_TYPES.add("slimefun:grind_stone");
    EDITABLE_RECIPE_TYPES.add("slimefun:compressor");
    EDITABLE_RECIPE_TYPES.add("slimefun:smeltery");
    EDITABLE_RECIPE_TYPES.add("slimefun:juicer");
    EDITABLE_RECIPE_TYPES.add("slimefun:heated_pressure_chamber");
    EDITABLE_RECIPE_TYPES.add("slimefun:food_fabricator");
    EDITABLE_RECIPE_TYPES.add("slimefun:food_composter");
    EDITABLE_RECIPE_TYPES.add("slimefun:refinery");
    EDITABLE_RECIPE_TYPES.add("slimefun:freezer");
}
```

新增 Addon 白名单：
```java
private static final Map<String, Integer> ADDON_RECIPE_TYPE_SLOTS = new LinkedHashMap<>();
static {
    // 运行时动态填充，这里定义期望的槽位数
}
private static boolean addonDetected = false;
```

- [ ] **Step 2: 实现 Addon 运行时检测方法**

新增 `detectAddonRecipeTypes()` 方法，在 `collectAllRecipeTypes()` 中调用：

```java
private void detectAddonRecipeTypes(Map<String, RecipeType> resolved) {
    if (addonDetected) return;
    addonDetected = true;
    for (Map.Entry<String, RecipeType> entry : resolved.entrySet()) {
        String key = entry.getKey();
        if (EDITABLE_RECIPE_TYPES.contains(key)) continue; // 已在白名单中
        // Galactifun: 星系装配台
        if (key.startsWith("galactifun:") && key.contains("assembly")) {
            EDITABLE_RECIPE_TYPES.add(key);
            ADDON_RECIPE_TYPE_SLOTS.put(key, 9); // 默认9格
            plugin.getLogger().info("[RecipeAPI] Detected Galactifun recipe type: " + key);
        }
        // InfinityExpansion: 存储单元工作台、无尽工作台
        if (key.startsWith("infinityexpansion:") && (key.contains("storage") || key.contains("infinity"))) {
            EDITABLE_RECIPE_TYPES.add(key);
            ADDON_RECIPE_TYPE_SLOTS.put(key, 9); // 默认9格
            plugin.getLogger().info("[RecipeAPI] Detected InfinityExpansion recipe type: " + key);
        }
    }
}
```

- [ ] **Step 3: 修改 `collectAllRecipeTypes()` 使用新白名单**

在 `collectAllRecipeTypes()` 方法开头调用 `detectAddonRecipeTypes(resolved)`。

将 `for (String key : BUILTIN_RECIPE_TYPES)` 改为 `for (String key : EDITABLE_RECIPE_TYPES)`。

对于 Addon 类型，使用 `ADDON_RECIPE_TYPE_SLOTS.getOrDefault(key, guessSlots(key))` 获取槽数。

- [ ] **Step 4: 修改 `resolveBuiltinTypes()` 使用新白名单**

将 `BUILTIN_RECIPE_TYPES.contains(k)` 改为 `EDITABLE_RECIPE_TYPES.contains(k)`。

- [ ] **Step 5: 修改 `buildRecipesJson()` 只返回白名单中的配方**

在 `buildRecipesJson()` 中，只序列化白名单中 RecipeType 的配方。对于不在白名单中的类型，跳过序列化。

- [ ] **Step 6: 修改 `applyAllRecipes()` 只应用白名单中的配方**

在 `applyAllRecipes()` 中，跳过不在 `EDITABLE_RECIPE_TYPES` 中的类型。

- [ ] **Step 7: Build 验证**

```bash
cd d:\Users\Administrator\Desktop\Java项目\slimefun\SlimefunCustomGuide
mvn clean package -q
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: 合成表白名单重构为EDITABLE_RECIPE_TYPES(14种)+Addon检测"
```

---

### Task 2: FuelApiHandler 后端实现

**Files:**
- Create: `FuelApiHandler.java`
- Modify: `WebServer.java`

- [ ] **Step 1: 创建 FuelApiHandler.java 骨架**

新建 `src/main/java/cn/rmc/slimefunweaver/web/FuelApiHandler.java`：

```java
package cn.rmc.slimefunweaver.web;

import cn.rmc.slimefunweaver.SlimefunWeaver;
import cn.rmc.slimefunweaver.util.ColorUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class FuelApiHandler implements HttpHandler {

    private static SlimefunWeaver plugin;

    public FuelApiHandler(SlimefunWeaver plugin) {
        FuelApiHandler.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 路由分发
    }
}
```

- [ ] **Step 2: 实现 GET /api/fuel-types**

扫描所有 `SlimefunItem`，找 `AbstractEnergyProvider` 子类，读取燃料列表。

响应格式：
```json
{
  "generators": [
    {
      "id": "COAL_GENERATOR",
      "name": "§f煤炭发电机",
      "energyPerTick": 6,
      "fuels": [
        {"inputId": "minecraft:coal", "inputName": "煤炭", "burnTime": 16, "outputId": null, "outputName": null}
      ]
    }
  ]
}
```

- [ ] **Step 3: 实现 PUT /api/fuels**

解析请求 JSON，清空旧燃料列表，重新注册新燃料。

请求格式：
```json
{
  "generatorId": "COAL_GENERATOR",
  "fuels": [
    {"inputId": "minecraft:coal", "burnTime": 16, "outputId": null}
  ]
}
```

实现要点：
- `generator.getFuelTypes().clear()` 清空
- `generator.registerFuel(new MachineFuel(seconds, inputStack, outputStack))` 注册
- 需要在主线程执行（`Bukkit.isPrimaryThread()` 检查）

- [ ] **Step 4: 在 WebServer.java 注册路由**

在 `WebServer.start()` 中添加：
```java
if (recipesEditor) {
    // ... 现有代码 ...
    FuelApiHandler fuelHandler = new FuelApiHandler(handler.getPlugin());
    server.createContext("/api/fuel-types", fuelHandler);
    server.createContext("/api/fuels", fuelHandler);
}
```

- [ ] **Step 5: Build 验证**

```bash
mvn clean package -q
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: 新增FuelApiHandler燃料管理API"
```

---

### Task 3: 前端标签切换 + 配方类型过滤

**Files:**
- Modify: `recipes.html`

- [ ] **Step 1: 添加标签切换 UI**

在页面顶部（物品列表上方）添加标签切换：
```html
<div class="tab-bar">
  <button class="tab active" onclick="switchTab('recipes')">🧪 合成表</button>
  <button class="tab" onclick="switchTab('fuels')">🔥 燃料</button>
</div>
```

- [ ] **Step 2: 实现 switchTab() 函数**

```javascript
function switchTab(tab) {
  currentTab = tab;
  // 切换标签样式
  document.querySelectorAll('.tab').forEach(function(t) { t.classList.remove('active'); });
  document.querySelector('.tab[onclick*="'+tab+'"]').classList.add('active');
  // 显示/隐藏面板
  $id('recipe-panel').style.display = tab === 'recipes' ? '' : 'none';
  $id('fuel-panel').style.display = tab === 'fuels' ? '' : 'none';
  if (tab === 'fuels') loadFuels();
}
```

- [ ] **Step 3: 修改 `renderTypeList()` 过滤配方类型**

只显示白名单中的类型，隐藏信息型：
```javascript
function renderTypeList() {
  var h = '';
  recipeTypes.forEach(function(t){
    if (isNullRecipeType(t.key)) return;
    if (!t.isEditable) return; // 只显示可编辑类型
    // ... 渲染逻辑不变 ...
  });
}
```

后端在 `RecipeTypeInfo` 中新增 `isEditable: true` 字段，前端用此字段过滤。

- [ ] **Step 4: Build 验证**

```bash
mvn clean package -q
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: 前端标签切换+配方类型白名单过滤"
```

---

### Task 4: 前端燃料管理页面

**Files:**
- Modify: `recipes.html`

- [ ] **Step 1: 添加燃料管理面板 HTML**

在 `<div id="main">` 中添加：
```html
<div id="fuel-panel" style="display:none">
  <div id="fuel-left-panel">
    <input type="text" id="fuel-search" placeholder="搜索发电机..." oninput="filterFuelGenerators()">
    <div id="fuel-generator-list"></div>
  </div>
  <div id="fuel-center-panel">
    <div id="fuel-empty" style="text-align:center;color:#484f58;padding:60px">← 选择一个发电机</div>
    <div id="fuel-editor" style="display:none">
      <div id="fuel-info"></div>
      <table id="fuel-table">
        <thead><tr><th>燃料物品</th><th>燃烧时间(秒)</th><th>产出物品</th><th>操作</th></tr></thead>
        <tbody id="fuel-tbody"></tbody>
      </table>
      <button class="btn btn-primary" onclick="addFuel()">+ 添加燃料</button>
      <button class="btn" onclick="saveFuels()">💾 保存燃料</button>
    </div>
  </div>
</div>
```

- [ ] **Step 2: 实现 loadFuels()**

```javascript
function loadFuels() {
  fetch('/api/fuel-types').then(function(r){return r.json()}).then(function(json){
    fuelGenerators = json.generators || [];
    renderFuelGeneratorList();
  });
}
```

- [ ] **Step 3: 实现 renderFuelGeneratorList()**

左侧渲染发电机列表，类似物品列表的搜索+选择模式。

- [ ] **Step 4: 实现 selectFuelGenerator()**

选中发电机后，右侧显示燃料列表表格，每行可编辑燃烧时间、删除燃料。

- [ ] **Step 5: 实现 addFuel() 和 saveFuels()**

`addFuel()` 弹出物品选择器（复用现有 `/api/recipes/materials` API）。
`saveFuels()` 调用 `PUT /api/fuels` 保存修改。

- [ ] **Step 6: Build 验证**

```bash
mvn clean package -q
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: 前端燃料管理页面（选择发电机+编辑燃料列表）"
```

---

### Task 5: 槽位一致性最终验证

**Files:**
- Verify: `RecipeApiHandler.java`, `recipes.html`

- [ ] **Step 1: 验证 guessSlots() 与 EDITABLE_RECIPE_TYPES 一致**

确认 `guessSlots()` 的 switch 中，所有 `EDITABLE_RECIPE_TYPES` 的 key 都有对应的 case。

- [ ] **Step 2: 验证前端 getTypeInfo() 使用后端数据**

确认 `getTypeInfo()` 优先使用 `recipeTypes` 数组中的 `slots` 值，不自行推断。

- [ ] **Step 3: 验证 save() 和 applyAllRecipes() 使用相同槽数**

确认 `save()` 中的截断逻辑和 `applyAllRecipes()` 中的 `guessSlots()` 返回相同值。

- [ ] **Step 4: Build 验证**

```bash
mvn clean package -q
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "verify: 槽位数量一致性检查通过"
```

---

## 执行顺序

1. **Task 1** → 白名单重构（后端核心）
2. **Task 2** → 燃料 API（后端新增）
3. **Task 3** → 前端标签+过滤（前端改造）
4. **Task 4** → 燃料管理页面（前端新增）
5. **Task 5** → 一致性验证（收尾）
