# GPU Stream Computing (gpusc)

> 能力感知的**分布式区块预生成**模组 —— 服务器自己识别机器性能，**只把任务分给够强的机器**，弱的直接拒绝。

## 这是什么

Minecraft（NeoForge 1.21.1）主服里跑的模组，它做四件事：

1. **扫描世界** → 算出哪些区域还没生成（按 region 文件 `r.x.z.mca` 判定）
2. **能力评分** → Worker 注册时上报硬件（CPU 核数 / 内存 / 空闲磁盘 / Java21），**不达标者被拒绝**
3. **任务分发** → 达标的机器领任务（区域坐标 + 方块范围），生成完回传 `.mca`
4. **结果合并** → 主服把回传的 region 文件并入 `world/region/`

## 角色（同一个 jar，两种模式）

`config/gpusc.json`：

| 字段 | 说明 |
|---|---|
| `role` | `coordinator`（主服）或 `worker`（强机） |
| `port` | 协调者 HTTP 端口（默认 8765） |
| `token` | 鉴权 token（**必须改**） |
| `minCores` / `minRamMb` / `minDiskGb` | 能力门槛（默认 4 / 8000 / 20） |
| `centerX` / `centerZ` / `radius` | 预生成目标范围 |
| `coordinatorUrl` / `workerName` | 工人模式用 |

## 游戏内命令（OP）

```
/pgc status     集群与任务概览
/pgc workers    已注册机器（含评分与拒绝原因）
/pgc scan       扫描世界并生成任务队列
/pgc jobs       任务列表（前 10 条）
```

## HTTP API（协调者）

带 `X-Gpusc-Token` 头：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/register?name=&cores=&ram=&disk=&java=` | 注册/心跳，返回是否达标 |
| GET | `/claim?name=` | 领任务（**不达标直接拒绝**） |
| POST | `/submit?id=` | 上传 `r.x.z.mca` 二进制（body） |
| GET | `/status` | 集群总览 JSON |

## 构建

```bash
gradle build          # 需要 JDK 21
# 产物: build/libs/gpusc-0.1.0.jar
```

CI：`.github/workflows/build.yml`（push 后自动编译并上传 artifact）

## 路线图

- [x] 阶段① 协调者：能力门槛 + 任务队列 + HTTP API + 命令
- [ ] 阶段② 工人模式：领任务 → 用 MC 自身区块生成器生成区域 → 回传
- [ ] 阶段③ 自动合并 / 重试 / 超时回收 / 有人在线时自动暂停

## 许可

MIT
