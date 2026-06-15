# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
# Requires Slimefun4 (happy66dev fork) available via JitPack
mvn clean package -DskipTests

# Output: target/SlimefunWeaver-0.1Alpha.jar
```

Java 8 target. No formatter plugin configured.

## What This Plugin Does

SlimefunCustomGuide (artifact: SlimefunWeaver) replaces Slimefun4's default guide book with a fully customizable implementation driven by `categories.yml`. It also embeds an HTTP server serving a browser-based editor for guide categories, recipes, and researches.

## In-Game Commands

- `/scg reload` — hot-reloads `categories.yml` without restart.

## Web Editor

Enabled via `config.yml` (`web-editor.enabled: true`). Default port `8899`. Token required for non-localhost bind.

| URL | Purpose |
|---|---|
| `/` | Guide category editor SPA |
| `/recipes.html` | Recipe editor |
| `/editor.html` | Research dependency graph editor |

REST API endpoints: `GET/PUT /api/categories`, `GET /api/recipes`, `GET/PUT /api/researches`, `GET /api/materials?q=`, `GET /api/slimefun-items?q=`.

## Architecture

```
cn.rmc.slimefunweaver/
├── api/          SlimefunWeaverAPI soft-dependency interface for SF4
├── command/      /scg command handler
├── config/       YAML loading, placeholder resolution
├── guide/        Guide rendering: Renderer, History, Implementation
├── listener/     Bukkit events + NBT persistence for navigation history
├── model/        Category / Item / Icon / TreeNode data models
├── settings/     Guide mode settings
├── util/         Icon parsing, vanilla material localization
└── web/          HTTP server + all API route handlers
    ├── WebServer.java           HTTP lifecycle
    ├── WebApiHandler.java       Guide editor routes
    ├── RecipeApiHandler.java    Recipe editor routes
    ├── ResearchApiHandler.java  Research editor routes
    ├── WebSecurity.java         Token auth
    └── JsonUtil.java
```

Static web assets (HTML/CSS/JS) are bundled inside `src/main/resources/web/`.

### Navigation History (NBT Persistence)
The guide book item stores the full navigation path in NBT as a pipe-delimited string:
```
"1|机器:1|发电机:3|ITEM:COAL_GENERATOR"
```
Right-clicking the book resumes from the last position. Left-click returns one level; Shift+Left-click jumps to the SCG root.

### categories.yml Structure
Drives the entire guide layout. Supports item types: `VANILLA`, `SLIMEFUN`, `PLACEHOLDER`, `REFERENCE`. Reference nodes (`type: REFERENCE`) link to another category by `ref` key and support `copy` or `custom` mode.

### Guide Implementation
Replaces the default `SlimefunGuideImplementation`. Registered as the active guide via Slimefun4's guide API during `onEnable`.
