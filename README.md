# SlimefunCustomGuide

**Slimefun 粘液科技 — 自定义指南书 + 研究 Web 编辑器**

为 Slimefun4 提供可完全自定义的游戏内指南书界面，以及基于浏览器的研究配置 Web 编辑器。

---

## 功能

### 🎮 游戏内

| 功能 | 说明 |
|---|---|
| 自定义指南书布局 | 通过 `categories.yml` 自由编排分类、物品、占位符 |
| 多页分页 | 支持分页展示大量物品 |
| 物品详情回退导航 | 从物品详情页返回分类时不丢失滚动位置 |
| 占位符灰度占位 | 未解锁时显示灰度图标，解锁后展示真实物品 |
| 荧光效果 | 支持分类和物品的 glow 特效 |
| `/slimefuncustomguide reload` | 热重载 `categories.yml` |

### 🌐 Web 编辑器

#### 指南书编辑器 (`/`)
- 分类树结构编辑（添加/删除/配置分类、物品、占位符）
- 物品网格拖拽交换槽位
- 实时搜索原版和 Slimefun 物品
- 编辑显示名称/描述/图标/荧光效果/页码
- 保存自动写入 `categories.yml` 并立即生效
- 迷你键盘颜色代码拾取器（`&c &l` 格式）

#### 研究编辑器 (`/editor.html`)
- 加载全部 Slimefun 研究节点（364 条）
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
| `GET` | `/style.css` | 编辑器样式 |
| `GET` | `/editor.js` | 编辑器 JS |
| `GET` | `/editor.html` | 研究编辑器页面 |
| `GET` | `/api/categories` | 读取分类 JSON |
| `PUT` | `/api/categories` | 保存分类到 `categories.yml` |
| `GET` | `/api/materials?q=` | 搜索原版 + Slimefun 物品 |
| `GET` | `/api/item-groups` | Slimefun 物品分类列表 |
| `GET` | `/api/researches` | 读取全部研究 JSON |
| `PUT` | `/api/researches` | 保存修改到 `Researches.yml` |
| `GET` | `/api/slimefun-items?q=` | 搜索 Slimefun 物品 |

---

## 配置

`plugins/SlimefunCustomGuide/config.yml`:

```yaml
web-editor:
  enabled: true      # 启用 Web 编辑器
  bind: 127.0.0.1    # 绑定地址
  port: 8899         # 监听端口

enable-custom-guide: true  # 启用自定义指南书
```

---

## 构建

```bash
# 0. 克隆 Slimefun4 依赖（happy 魔改版）
git clone https://github.com/happy66dev/slimefun4.git
cd slimefun4 && mvn install -DskipTests && cd ..

# 1. 编译
git clone https://github.com/happy66dev/SlimefunCustomGuide.git
cd SlimefunCustomGuide
mvn package -DskipTests

# 2. JAR 位置
# target/SlimefunCustomGuide-1.0-SNAPSHOT.jar
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
src/main/java/cn/rmc/slimefuncustomguide/
├── command/         # /slimefuncustomguide 命令
├── config/          # YAML 加载 & 占位符解析
├── guide/           # 指南书渲染 (Renderer/History/Implementation)
├── listener/        # 事件监听器
├── model/           # 数据模型 (Category/Item/Icon/TreeNode)
├── settings/        # 指南书模式设置
├── util/            # 图标解析工具
├── web/             # Web 服务器 + API
│   ├── WebServer.java           # HTTP 服务器生命周期
│   ├── WebApiHandler.java       # / 指南书编辑器路由
│   ├── ResearchApiHandler.java  # /editor.html 研究编辑器路由
│   └── JsonUtil.java            # JSON 工具
└── CustomGuidePlugin.java       # 插件主类
src/main/resources/
├── web/
│   ├── index.html               # 指南书编辑器 SPA
│   ├── research-editor.html     # 研究编辑器 SPA
│   ├── editor.js / style.css
├── categories.yml               # 自定义分类默认配置
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

[![Star History Chart](https://api.star-history.com/svg?repos=happy66dev/SlimefunCustomGuide&type=date)](https://star-history.com/#happy66dev/SlimefunCustomGuide)
