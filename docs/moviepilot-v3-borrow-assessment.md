# MoviePilot V3 借鉴评估(2026-09-13)

对象:`/home/harold/workspace/MoviePilot`,v3.0.1 正式版(git `v2.15.4-1454-g2edf20433`,迁移链已到 3_0_33,比 release 新)。
上一次评估见 [moviepilot-borrow-assessment.md](moviepilot-borrow-assessment.md)(2026-09-01,V2 时代,A 级 4 项已实现 commit 7f515c74)。
本次聚焦 V2→V3 官方宣称的八大升级,逐项判定;**结论:无 A 级(无值得立即动手的项),B 级留触发 5 项(其中 2 项拿到了代码级参考蓝图),其余已等价覆盖或场景不匹配**。

## 一、结论速览

| V3 升级项 | 判定 | 依据 |
|---|---|---|
| 统一媒体身份 media_source+media_id | C 级不抄 | MP 自己也无持久映射表(运行时名称/年份重匹配),atv 元数据快照+localDoubanId 绑定+年份/季号门禁已等价 |
| 任务可靠性与事件恢复 | 大盘已等价 | atv outbox 已有 attempts 平方退避+超限 FAILED+每分钟 sweep(重启≤1 分钟捞起),MP 的 lease/attempt CAS fencing 是多进程需求,atv 单实例口径不需要 |
| 版本化分类策略 | C 级不抄 | atv 无规则引擎场景(用户定规:过滤规则组表达式引擎不抄) |
| 备份管理 | 大盘已等价 | atv 已有模块化 JSON zip 备份/恢复(`DatabaseBackupService`,流式、id 生成器重建、网页导入导出);MP 增量仅剩「迁移前自动备份+定时+verify」,见 B5 |
| 音乐自动化 / Agent / 插件分身 / PWA / 发布链路 | C 级不抄 | 场景不匹配(TvBox 生态无音乐流、无 AI agent、插件体系不同) |
| AList 集成 | 反向 | atv 本体就是 AList 聚合,MP 是把 AList 当存储后端(秒传头/网盘内 copy/move 整理),对 atv 无新意 |

## 二、上次 B 级 5 项在 V3 的现状(重点收获)

上次留触发的 5 项,V3 给出了 2 项完整实现,触发时照抄坐标如下:

### B1. 订阅级替换词(V3 已有完整实现,参考价值最大)

- **MP 实现**:`subscribe.custom_words` 列(`app/db/models/subscribe.py:112`);匹配时命中订阅词则用 custom_words 重新解析+重识别(`app/chain/subscribe/match.py:553-598`);搜索时传入(`app/chain/subscribe/search.py:758`)。
- **词法三规则**(`app/domain/meta/words.py:100` WordsMatcher.prepare):①屏蔽词②`被替换词 => 替换词`③`前定位词 <> 后定位词 >> 集数偏移(EP)`(偏移是白名单 AST 安全求值)。全局存量于 SystemConfig `CustomIdentifiers`,API `/api/v1/identifiers`(乐观锁 409)。
- **atv 触发条件(维持用户定规)**:下个混淆剧名目录案(夸克混淆剧名目录形态)。规则③的集数偏移对 atv 集号错位资源也有用。

### B2. TMDB 季感知年份(V3 已有完整实现)

- **MP 实现**:匹配计划第一步带 `season_year=meta.year, season_number=meta.begin_season`(`app/modules/themoviedb/__init__.py:562-595`);校验用候选 `seasons[].air_date[:4]` 对比季首播年(`app/modules/themoviedb/tmdbapi.py:628-671 _season_year_matches/_season_candidate_matches`)——多季长篇用「季首播年」而非剧集首播年;命中条件=标题/别名匹配且(首播年==season_year 或 该季首播年==season_year);每季年份回写 `mediainfo.season_years`(`__init__.py:670-691`)供下游使用。
- **atv 触发条件(维持)**:下个多季年份差误拒案(记忆:多季长篇年份差是已知三形态之一)。

### B3. 多解析器投票 —— V3 也没有,降格

V3 无跨解析器投票:模块调度是 priority 排序+FIRST_NON_EMPTY 短路(`app/runtime/extensions/module/dispatcher.py:460-474`),唯一"择优"在 TMDB 模块内部对同名 TV/电影候选打分(`app/modules/themoviedb/__init__.py:509-548 _match_score`)。atv 的豆瓣桥接"整词同名+年份门禁"已是同思路,维持留触发不升格。

### B4. 完结判定前强刷总集数 —— atv 已等价覆盖

MP 的 `__refresh_total_episode_before_completion`(`app/chain/subscribe/refresh.py:497-553`)+回落保护 `__resolve_total_episode_decrease`(:336-404),与 atv 上次已实现的 effectiveTotalEpisodes+clampTotalShrink 回落保护(commit 7f515c74)同构。atv 另有第三路 shouldAutoEnd(本季播完且集齐)。**无需动作**。

### B5. JobSpec 调度目录 —— V3 有,atv 维持留触发

V3 声明式 JobSpec/JobCatalog/JobRecoveryPolicy(NEXT_SCHEDULE/DURABLE_QUEUE/MANUAL_ONLY,`app/application/scheduling.py:38-118`)+集中目录投影 APScheduler(`app/scheduler/catalog.py:169-502`)。纯代码整理型重构,不解决用户痛点,维持留触发(优先级最低)。

## 三、新发现的可借鉴项

### B6. doctor 式一键诊断报告(2026-09-13 已实现)

- **MP 实现**:`app/doctor/`(CLI `moviepilot doctor`+Agent 工具),10 项检查:runtime_paths/config/进程拓扑/端口/依赖/数据库连通+结构/备份/前端资源/日志错误聚合/Docker(`checks.py:132 default_checks`),产出结构化 DoctorReport(severity/status/recommendation/fixable);fix=True 仅执行白名单安全修复。
- **atv 实现**(照此思路落地,未抄其 CLI/fixable 形态):`service/diagnostics/DiagnosticsService` + `GET /api/diagnostics/report`(ADMIN)+ 网页「关于」页诊断报告卡片。区块:系统(版本/运行时/内存/磁盘)/数据库(产品+Flyway 失败迁移数)/存储(AList 运行态+x_storages 挂载异常计数+网盘账号分布)/追剧(订阅状态分布+通知 outbox 积压)/搜索源(退避快照)/日志(app.log+AList logrus 日志尾部采样,logger+归一化消息聚类 TopN);每区块独立容错,区块查询失败降级为 ERROR finding 不拖垮整份报告;输出文本统一脱敏(ANSI 剥离、URL 凭证参数与 Bearer 打码)。AList 日志格式= logrus ForceColors 短等级码(ERRO[时间]),与 app.log 的 Spring Boot 格式分两套解析。

### B7. 迁移前文件级备份(新,低 B)

- **MP 实现**:alembic upgrade 前若检测到有迁移则自动 `create_backup()`(`app/startup/initializers/database.py:92-128`);制品=sqlite online backup API 快照/pg_dump custom 格式;verify(内容级:PRAGMA integrity_check / pg_restore --list)通过才 `os.replace` 原子发布;保留 30 份/30 天(`app/application/backup.py`、`app/adapters/system/backup/database.py`)。
- **atv 现状**:备份是 JSON 模块级(运行时、依赖新代码的 handler),无法在 Flyway 迁移前执行;迁移前备份须走文件级(直接复制 atv.mv.db——H2 无独立 WAL,迁移前库刚打开且无写入,副本一致)。atv 有 H2 活库手术 runback 的存在,升级翻车痛点真实。价值直接但技术路径独立,低 B。

### B8. 搜索批次持久化(新,低 B)

- **MP 实现**:订阅搜索任务落库(SubscriptionSearchBatch/Task,state/phase/`pending_site_ids`/lease),"重试只保留尚未完成的站点,跨重启不重复请求成功站点"(`app/db/models/subscriptionsearch.py`)。
- **atv 现状**:补搜轮次制 gapSearchRounds 是内存态,重启清零后整季词从头重搜,浪费盘链按视频/天配额。但 atv 重启频率低+SearchSourceThrottle 退避闸门部分兜底,收益有限。

## 四、明确不抄清单(本次新增理由)

- **统一身份两列贯通**:六张表 media_source+media_id 两列+CHECK+联合索引(`app/db/models/_constraints.py:12-26`),跨来源转换靠运行时三阶段名称/年份重匹配(`app/chain/media/projection.py:392-409 convert_media_identity`)而非映射表——即 MP 的"统一身份"底层仍是名称匹配,与 atv 豆瓣名称桥接同构;改表结构成本>收益。
- **版本化分类策略**:整体快照+revision CAS+影响预览(≤200 近期样本估算)+回滚发布为新版本(`app/application/classification/`)——工程上漂亮,但 atv 无用户可编辑规则引擎场景。
- **UoW/lease fencing/声明式生命周期组件**:单实例口径过度工程。
- **音乐(MusicBrainz/acoustid/lrclib/Navidrome)、Agent 权限确认/上下文压缩、插件虚拟分身、钉钉通知、独立站点资源通道、PWA**:场景不匹配或既有定规(多通知渠道不抄)。
- **AList 存储适配器**(秒传上传头/网盘内服务端整理/远程目录快照监控):atv 本体即 AList 聚合,能力反向覆盖。

## 五、坑

- 本地副本 version.py 标 v3.0.1 但迁移链到 3_0_33、git describe `v2.15.4-1454-g…`,代码比官方 release 提前;引用坐标以本副本为准,升级副本后行号会漂。
- MP 的六表 CHECK 约束+mapper 事件归一半对身份——若 atv 未来真做身份列,这个"半对清洗"设计值得带上(订阅表 doubanId 半空值问题同源)。
