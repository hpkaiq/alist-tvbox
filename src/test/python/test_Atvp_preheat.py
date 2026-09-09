import hashlib
import json
import tempfile
import unittest
from importlib.machinery import SourceFileLoader
from pathlib import Path
from unittest.mock import Mock


ROOT = Path(__file__).resolve().parents[3]
MODULE = SourceFileLoader(
    "atvp_preheat",
    str(ROOT / "src/main/resources/static/Atvp.py"),
).load_module()
Spider = MODULE.Spider


class Response:
    def __init__(self, text="", status_code=200):
        self.status_code = status_code
        self.text = text


class TestAtvpPreheat(unittest.TestCase):
    def setUp(self):
        Spider._instance = None
        MODULE._preheat_daemon_started = False
        self.spider = Spider()
        self.tmp = tempfile.TemporaryDirectory()
        # 直接给已解析形态的目录(非空字符串缓存生效;空串=非 Chaquopy 环境关闭缓存)
        self.spider._preheat_cache_dir = str(Path(self.tmp.name))

    def tearDown(self):
        Spider._instance = None
        MODULE._preheat_daemon_started = False
        self.tmp.cleanup()

    def cache_file(self, url):
        digest = hashlib.md5(url.encode("utf-8")).hexdigest()
        return Path(self.tmp.name) / (digest + ".txt")

    def test_load_source_prefers_preheat_cache(self):
        url = "http://atv.example/plugins/tok/3.txt?v=7"
        self.cache_file(url).write_text("//@cached-payload", encoding="utf-8")
        self.spider.fetch = Mock(side_effect=AssertionError("cache hit must skip network"))

        self.assertEqual(self.spider._load_source(url), "//@cached-payload")

    def test_load_source_falls_back_to_network_on_miss(self):
        self.spider.fetch = Mock(return_value=Response("//@remote-payload", 200))

        self.assertEqual(
            self.spider._load_source("http://atv.example/plugins/tok/4.txt?v=8"),
            "//@remote-payload",
        )

    def test_blank_cache_entry_is_ignored(self):
        url = "http://atv.example/plugins/tok/5.txt?v=9"
        self.cache_file(url).write_text("   ", encoding="utf-8")
        self.spider.fetch = Mock(return_value=Response("//@remote", 200))

        self.assertEqual(self.spider._load_source(url), "//@remote")

    def test_preheat_worker_downloads_manifest_into_cache(self):
        url = "http://atv.example/plugins/tok/6.txt?v=10"
        manifest_url = "http://atv.example/plugin-preheat/tok"

        def fake_fetch(target, timeout=5, **kwargs):
            if target == manifest_url:
                return Response(json.dumps({"plugins": [{"url": url, "key": "k"}]}), 200)
            return Response("//@cipher", 200)

        self.spider.fetch = Mock(side_effect=fake_fetch)
        self.spider._preheat_worker(manifest_url)

        self.assertEqual(self.cache_file(url).read_text(encoding="utf-8"), "//@cipher")

    def test_preheat_worker_skips_cached_plugins(self):
        url = "http://atv.example/plugins/tok/7.txt?v=11"
        manifest_url = "http://atv.example/plugin-preheat/tok"
        self.cache_file(url).write_text("//@already", encoding="utf-8")

        def fake_fetch(target, timeout=5, **kwargs):
            assert target == manifest_url, "cached plugin must not be re-downloaded"
            return Response(json.dumps({"plugins": [{"url": url}]}), 200)

        self.spider.fetch = Mock(side_effect=fake_fetch)
        self.spider._preheat_worker(manifest_url)

        self.assertEqual(self.cache_file(url).read_text(encoding="utf-8"), "//@already")

    def test_preheat_daemon_starts_only_once(self):
        original_thread = MODULE.threading.Thread
        try:
            MODULE.threading.Thread = Mock(return_value=Mock())
            payload = {"preheatUrl": "http://atv.example/plugin-preheat/tok"}
            self.spider._start_preheat_daemon(payload)
            self.spider._start_preheat_daemon(payload)

            self.assertEqual(MODULE.threading.Thread.call_count, 1)
            MODULE.threading.Thread.assert_called_once_with(
                target=self.spider._preheat_worker,
                args=("http://atv.example/plugin-preheat/tok",),
                daemon=True,
            )
        finally:
            MODULE.threading.Thread = original_thread

    def test_preheat_daemon_ignores_payload_without_url(self):
        original_thread = MODULE.threading.Thread
        MODULE.threading.Thread = Mock(return_value=Mock())
        try:
            self.spider._start_preheat_daemon({})
            self.spider._start_preheat_daemon(None)
            self.spider._start_preheat_daemon({"preheatUrl": "not-a-url"})
            MODULE.threading.Thread.assert_not_called()
        finally:
            MODULE.threading.Thread = original_thread

    def test_cache_file_name_matches_java_contract(self):
        # 与 spring.jar PluginPreheat.cacheFileName 的 md5 约定一致(固定向量互锁)
        url = "http://atv.example/plugins/tok/3.txt?v=7"
        self.assertTrue(
            self.spider._preheat_cache_file(url).endswith("693918747f778a641aace54498767afb.txt")
        )

    def test_cache_dir_unavailable_disables_cache(self):
        self.spider._preheat_cache_dir = ""
        self.spider.fetch = Mock(return_value=Response("//@remote", 200))

        self.assertEqual(self.spider._read_preheat_cache("http://x/1.txt"), None)
        self.assertEqual(self.spider._load_source("http://x/1.txt"), "//@remote")


if __name__ == "__main__":
    unittest.main()
