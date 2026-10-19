# Day 8 · 星期八

> **An eighth day for everything that didn't fit into seven.**  
> 给一周七天装不下的计划，多留一天。

**Windows x64 · Java 25 · JavaFX · SQLite · Local-first**  
**Version / 版本：** `v0.2.0` · Portable

Day 8 is a small desktop planner connecting **what you plan**, **what you actually spend time on**, and **what you finish**. They aren't necessarily the same thing.

星期八是一款本地桌面时间管理工具，把**计划安排、实际投入与任务完成情况**分开记录，再联系起来。毕竟，排了两小时 ≠ 工作了两小时 ≠ 完成了任务。

## ✨ What it does / 主要功能

| Feature | 功能 |
| --- | --- |
| **Tasks & Steps** — Checklists, estimated durations, categories | **任务与步骤**：清单、预计时长、分类 |
| **Day Plan** — 24-hour timeline with linked tasks, steps, and fixed events | **日程规划**：24 小时时间轴，关联任务、步骤与固定活动 |
| **Projects** — Organise work and follow weighted progress | **项目管理**：归类任务，跟踪加权完成进度 |
| **Focus Sessions** — Start, pause, resume, and stop real work tracking | **专注记录**：开始、暂停、恢复及结束，记录实际投入 |
| **Mini Widget** — Compact clock and Focus controls | **迷你窗口**：时钟与专注控制，可独立于主窗口显示 |
| **Insights** — Work hours and observed completion trends, day/week/month/year | **统计趋势**：工作时长与历史完成率，支持日／周／月／年 |
| **SQLite** — Local storage, with no sign-in or cloud dependency | **本地数据**：SQLite 持久保存，无须账号或云端服务 |

## 🚀 Getting started / 如何使用

**Windows Portable:** Get `Day8-v0.2.0-win-x64-portable.zip` from Releases once published, extract the **whole folder**, and double-click **`Day 8.exe`**. The runtime is bundled—no separate Java or Maven installation is needed.

**Windows 便携版：** 下载发布后的 ZIP，**完整解压**，双击 `Day 8.exe`。程序已附带 Java 运行环境，不需要另装 JDK 或 Maven。

**Workflow / 建议操作：**

1. Create a **Task**, optionally divide it into **Steps**. / 创建任务，按需拆分步骤。
2. Put it on the **Day Plan** timeline. / 将任务或步骤安排到日程中。
3. Press **Focus** in To-Do, Day Plan, or Mini Widget; pause when you stop working. / 从任务、日程或迷你窗口启动专注；中断时暂停。
4. Check off finished steps and review **Insights**. / 勾选已完成步骤，在统计页复盘。

## 💾 Your data / 数据存储

All data lives in an `agenda.db` SQLite file **outside the application folder**. Replacing the portable app does not replace the database.

数据保存在程序目录之外的 `agenda.db` 文件中；更新便携版不会自动覆盖任务数据。

- **Default / 默认：** `%LOCALAPPDATA%\PersonalAgenda\agenda.db` (new Windows installations).
- **Legacy / 旧数据兼容：** Existing `F:\AgendaData\agenda.db` remains in use when found.
- **Change location / 修改路径：** **Window → Data Location…** → choose a folder → **fully restart** the app. An empty folder creates a **new empty database**; it does **not** move existing data.
- **Backup / 备份：** Fully exit the app, then copy `agenda.db` and any associated SQLite journal/WAL files.


## 🛠️ Build from source / 从源码运行

Requires **JDK 25** and **Maven** on Windows. / 开发环境：Windows、JDK 25、Maven。

```powershell
mvn clean verify
mvn javafx:run
```

Build the portable app / 打包便携版：

```powershell
.\scripts\package-windows.ps1
```

No Windows installer in `v0.2.0`; the Portable ZIP is the supported distribution. / 当前版本仅发布 Windows 便携版。

## 🤖 How it was made / 开发方式

**Half vibe coding, half learning to tell the code what it actually needs to do.** AI helped with sketches, examples, and debugging; design choices, hands-on Java/SQL learning, testing, and endless little UI fixes were part of the process too.

**半 Vibe Coding，半认真学 Java / SQL。** 用 AI 辅助构思、实现与排错，也把它当作练习工程设计、Git 和真实用户体验的小项目。不宣称完全手写，也不把 AI 生成代码等同于经过验证的软件。

## 🗺️ Status & next / 当前限制与后续

- Windows x64 only; **no cloud sync or multi-device editing**. / 仅支持 Windows x64，暂无云同步或多端写入。
- Completion history starts when observation tracking was enabled; older rates cannot be reconstructed exactly. / 完成率历史从启用记录后开始积累，无法精确补回更早的数据。
- Planned: improved tests and backups; a future **read-only web companion**. / 后续计划：完善测试、备份，以及只读 Web 查看端。

**Made to use, built to learn. / 为日常使用而做，也为持续学习而做。**
