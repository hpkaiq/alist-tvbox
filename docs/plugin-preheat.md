# 爬虫插件密文预热

解决「首次切换插件站点 / 全局搜索时逐个网络下载密文 .txt 慢」的问题:
解密本身(AES-256-GCM)是毫秒级,真正的耗时点是 Atvp.py `_load_source` 每次 init
都重新 `requests.get` 下载密文且零缓存——预热把启用插件的密文提前下载到本地缓存,
init 时缓存命中免网络。

## 链路

1. 服务端 `GET /plugin-preheat/{token}`(PluginContentController,与密文端点同款
   checkToken)返回启用插件清单 `{"plugins":[{"url","key","name"}]}`,
   常用插件排前——排序依据是 History 按 `sourceKind='spider_plugin'` 的
   sourceKey 使用次数聚合(HistoryRepository.countBySourceKey,全局口径)。
2. 订阅配置每个插件站点 ext(base64 JSON)新增 `preheatUrl` 字段;`source`
   密文地址统一经 `SubscriptionService.pluginContentUrl` 拼装并带 `?v={version}`:
   **完整地址即缓存 key**,插件更新 → 配置刷新换地址 → 旧缓存自然失效,
   由清单外清理回收。版本为空的插件不拼参数(从未版本化,无失效语义可谈)。
3. 客户端三层挂载,全部幂等(缓存文件存在即跳过):
   - **spring.jar `Init.init`**(主挂载点,jar 加载即触发):读上次持久化的清单地址
     (filesDir/plugin_preheat_identity.txt,独立于 playback_sync_identity.json——
     那个文件在无播放 token 时会被清除,不可混用);FileLock 防 jar 多 classloader
     重复跑(与 PlaybackSyncer 自启动同款)。
   - **`PyProxy.parseConfig`**:任意插件站点 init 时从 ext 拿 preheatUrl 写身份并触发
     (首页不是插件源也有 Init 挂载兜底;首次安装无身份则等这一步)。
   - **Atvp.py daemon**(Python 原生运行模式不经 jar 的兜底):`Spider.init` 起
     daemon 线程拉清单预下载,模块级标志去重。
4. Atvp.py `_load_source` remote 分支先查缓存,未命中才走网络(原行为)。

## 跨端约定(改动前必读)

| 约定 | Java(PluginPreheat) | Python(Atvp.py) |
|---|---|---|
| 缓存目录 | `Context.getCacheDir()/plugin-preheat` | `Python.getPlatform().getApplication().getCacheDir()` 拼同一子目录 |
| 缓存文件名 | md5(url).hex + ".txt" | hashlib.md5(url).hexdigest() + ".txt" |
| 临时文件 | `{hash}.tmp` | `{hash}.tmp.py`(后缀区分,防两端并发写同一 tmp) |
| 落盘 | tmp → rename | tmp → os.replace |

两端 md5 固定向量互锁测试:
`md5("http://atv.example/plugins/tok/3.txt?v=7") = 693918747f778a641aace54498767afb`
(PluginPreheatTest.cacheFileNameMatchesPythonContract /
test_Atvp_preheat.py:test_cache_file_name_matches_java_contract)。

清单 URL 与 ext 里 source 地址必须同一拼法(同走 pluginContentUrl),否则客户端缓存
永远不命中——SpiderTest 的清单测试有对齐断言。

## 语义变化

插件密文更新的传播从「每次 app 重启后首次 init 拉最新」变为「等订阅配置刷新换
? v= 地址」;TVBox 启动/手动刷新配置时站点与 spider 实例重建,URL 变了即重新下载,
通常只差一拍。介意时可加缓存命中后后台 revalidate(未实现)。

## 生效条件

- 服务端:本文所述清单端点 + ext 字段(随主程序发布)。
- 客户端:重打 spring.jar(CatVodTVSpider ./build.sh 拷回)+ Atvp.py revision
  bump(`ATVP_RUNTIME_REVISION` = preheat-v1,宿主 loader 按地址缓存重新拉取)。
