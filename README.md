# SlimefunWeaver

**Slimefun 粘液科技 — 自定义指南书 + 合成表编辑器 + 研究编辑器**

为 Slimefun4 提供可完全自定义的游戏内指南书界面、NBT 持久化导航历史、以及基于浏览器的合成表与研究 Web 编辑器。

---

## 功能

### 🎮 游戏内指南书

| 功能 | 说明 |
|---|---|
| 自定义指南书布局 | 通过 `categories.yml` 自由编排分类、物品、占位符 |
| 多页分页 | 支持分页展示大量物品 |
| **导航历史持久化** | NBT 记录完整浏览路径（分类→物品→嵌套物品），关闭重开直接恢复 |
| **逐级返回导航** | 物品详情链中左键逐级回退（G→E→C），Shift+左键直接跳回 SCG 分类 |
| **分类引用** | 引用节点（↳）点击直接跳转到目标分类，支持 copy/custom 两种模式 |
| 物品详情嵌套追踪 | 在物品合成表中点击配方物品自动记录到导航链 |
| 占位符灰度占位 | 未解锁时显示灰度图标，解锁后展示真实物品 |
| 荧光效果 | 支持分类和物品的 glow 特效 |
| **Debug 模式** | `config.yml` 中 `debug: true`，公屏输出导航全链路日志 |
| `/scg reload` | 热重载 `categories.yml` |

### 🌐 Web 编辑器

#### 指南书编辑器 (`/`)
- 分类树结构编辑（添加/删除/配置分类、物品、占位符、**引用**）
- 物品网格拖拽交换槽位
- 实时搜索原版和 Slimefun 物品
- 编辑显示名称/描述/图标/荧光效果/页码
- **引用节点**: copy 模式复制目标属性并禁用编辑，custom 模式自由修改
- 保存自动写入 `categories.yml` 并立即生效
- 迷你键盘颜色代码拾取器（`&c &l` 格式）

#### 合成表编辑器 (`/recipes.html`)
- 加载全部 Slimefun 物品合成表
- 表格视图编辑每个槽位的物品
- 编辑合成类型（RecipeType）和输出物品
- 保存自动写入 `categories.yml`

#### 研究编辑器 (`/editor.html`)
- 加载全部 Slimefun 研究节点
- **图形化依赖关系图** — 可拖拽平移/缩放
- **路径追踪着色** — 红=直接前置，黄=间接前置，绿=直接后继，天蓝=间接后继
- 拖拽连线创建新依赖
- 编辑等级消耗/金钱消耗/启用状态/AuraSkills 技能加值
- 保存自动写入 `Researches.yml`

---

## REST API

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/` | 指南书编辑器页面 |
| `GET` | `/recipes.html` | 合成表编辑器页面 |
| `GET` | `/editor.html` | 研究编辑器页面 |
| `GET` | `/style.css` | 编辑器样式 |
| `GET` | `/editor.js` | 编辑器 JS |
| `GET` | `/api/categories` | 读取分类 JSON |
| `PUT` | `/api/categories` | 保存分类到 `categories.yml` |
| `GET` | `/api/recipes` | 读取全部合成表 |
| `GET` | `/api/materials?q=` | 搜索原版 + Slimefun 物品 |
| `GET` | `/api/item-groups` | Slimefun 物品分类列表 |
| `GET` | `/api/researches` | 读取全部研究 JSON |
| `PUT` | `/api/researches` | 保存修改到 `Researches.yml` |
| `GET` | `/api/slimefun-items?q=` | 搜索 Slimefun 物品 |

---

## 配置

`plugins/SlimefunWeaver/config.yml`:

```yaml
enable-custom-guide: true  # 启用自定义指南书
debug: false               # Debug 模式（公屏输出导航日志）

web-editor:
  enabled: false
  bind: 127.0.0.1
  port: 8899
  token: ""              # 非本地 bind 必须设置
  editors:
    categories: false
    recipes: false
    researches: false
  auto-load-recipes: true
  auto-load-categories: true
```

---

## 分类引用 (Reference)

引用节点提供在分类中快捷跳转到另一个分类的能力：

```yaml
categories:
  机器:
    display: "&e机器设备"
    page: 1
    items:
      - type: REFERENCE
    ref: 机器/发电机       # 目标分类的层级路径 (父/子)
    mode: custom           # copy=复制属性 / custom=手动编辑
    display: "&6↳ 查看发电机"
        icon: {type: VANILLA, id: ARROW}
        slot: 5
```

| 属性 | 说明 |
|---|---|
| `type` | 固定 `REFERENCE` |
| `ref` | 目标分类的 key |
| `mode` | `copy` — 复制目标属性（禁用编辑）; `custom` — 自行配置 |
| `display` | 显示名称 |
| `icon` | 点击图标，默认 ARROW |
| `lore` | 描述文本 |
| `page`/`slot` | 所在页数/槽位 |

导航链自动支持引用跳转：A→B→C(ref:F)→D→E→F

---

## 导航历史 (NBT 持久化)

指南书物品通过 NBT 记录完整浏览路径：

```
NBT = "1|机器:1|发电机:3|ITEM:COAL_GENERATOR|ITEM:IRON_INGOT"
       ↑              ↑            ↑                    ↑
    主菜单第1页    分类:页码      物品ID              嵌套物品ID
```

| 操作 | 效果 |
|---|---|
| 右键指南书 | 直接恢复到最后一个物品详情 |
| 左键返回 | 逐级回退到上一个物品/分类 |
| Shift+左键返回 | 直接跳回 SCG 分类页 |
| ESC 关闭再打开 | 恢复到关闭前位置 |

---

## 构建

```bash
# 0. 克隆 Slimefun4 依赖（happy 魔改版）
git clone https://github.com/happy66dev/slimefun4.git
cd slimefun4 && mvn install -DskipTests && cd ..

# 1. 编译
git clone https://github.com/happy66dev/SlimefunWeaver.git
cd SlimefunWeaver
mvn package -DskipTests

# 2. JAR 位置
# target/SlimefunWeaver-1.0-SNAPSHOT.jar
```

---

## 依赖

| 依赖 | 版本 |
|---|---|
| Spigot/Paper | 1.16.5+ |
| [Slimefun4 (happy66dev)](https://github.com/happy66dev/slimefun4) | via JitPack |
| AuraSkills | 任意版本 |

---

## 项目结构

```
src/main/java/cn/rmc/slimefunweaver/
├── api/             # SlimefunWeaverAPI (SF4 软依赖接口)
├── command/         # /scg 命令
├── config/          # YAML 加载 & 占位符解析
├── guide/           # 指南书渲染 (Renderer/History/Implementation)
├── listener/        # 事件监听 & NBT 持久化
├── model/           # 数据模型 (Category/Item/Icon/TreeNode)
├── settings/        # 指南书模式设置
├── util/            # 图标解析 & 原版材质本地化
├── web/             # Web 服务器 + API
│   ├── WebServer.java           # HTTP 服务器生命周期
│   ├── WebApiHandler.java       # / 指南书编辑器路由
│   ├── RecipeApiHandler.java    # /recipes.html 合成表编辑器
│   ├── ResearchApiHandler.java  # /editor.html 研究编辑器
│   ├── WebSecurity.java        # 安全校验
│   └── JsonUtil.java           # JSON 工具
└── SlimefunWeaver.java         # 插件主类
src/main/resources/
├── web/
│   ├── index.html               # 指南书编辑器 SPA
│   ├── recipes.html             # 合成表编辑器
│   ├── research-editor.html     # 研究编辑器 SPA
│   ├── editor.js / style.css
├── categories.yml               # 自定义分类默认配置
├── zh_cn.json                   # 中文汉化
├── config.yml / plugin.yml
```

---

## 许可

GNU General Public License v3.0 — 详见 [LICENSE](LICENSE)

Copyright (C) 2025 **happy** (k666kkk666k@163.com)

---

## 致谢

- [TheBusyBiscuit/Slimefun4](https://github.com/SlimefunGuguProject/Slimefun4) — 原始粘液科技项目
- [SlimefunGuguProject](https://github.com/SlimefunGuguProject) — 汉化维护分支
- [JitPack](https://jitpack.io) — Maven 依赖分发

---

## :star: Star History

[![Star History Chart](https://api.star-history.com/svg?repos=happy66dev/SlimefunWeaver&type=date)](https://star-history.com/#happy66dev/SlimefunWeaver)
