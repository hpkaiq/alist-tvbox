import base64
import json
import unittest
from importlib.machinery import SourceFileLoader
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
MODULE = SourceFileLoader(
    "atvp_resume_fallback",
    str(ROOT / "src/main/resources/static/Atvp.py"),
).load_module()
Spider = MODULE.Spider

VOD_ID = "7683145110548122686"


def inner_source(play_from, play_url):
    detail = {
        "list": [
            {
                "vod_id": VOD_ID,
                "vod_name": "我在古代，靠召唤系统逍遥自在",
                "vod_play_from": play_from,
                "vod_play_url": play_url,
            }
        ]
    }
    return (
        "DETAIL = "
        + json.dumps(detail, ensure_ascii=False)
        + "\n\n\nclass Spider:\n"
        "    def init(self, extend=''):\n"
        "        return None\n\n"
        "    def detailContent(self, ids):\n"
        "        return DETAIL\n"
    )


class TestAtvpResumeFallback(unittest.TestCase):
    def setUp(self):
        Spider._instance = None
        self.spider = Spider()

    def build_ext(self):
        payload = {
            "loader": "https://atv.example/Atvp.py",
            "api": "https://atv.example",
            "source": "https://atv.example/plugins/demo/7.py",
            "raw": True,
            "token": "demo",
        }
        return base64.b64encode(json.dumps(payload, separators=(",", ":")).encode()).decode()

    def init_inner(self, source):
        with (
            patch.object(Spider, "_load_source", return_value=source),
            patch.object(Spider, "_request_bad_links", return_value=set()),
        ):
            self.spider.init(self.build_ext())

    def test_non_drive_resume_falls_back_to_inner_detail_with_line_reordered(self):
        self.init_inner(inner_source("其他片源$$$红果片源", "第1集$a1#第2集$a2$$$第1集$b1#第2集$b2"))
        resume_id = self.spider._encode_resume_id(
            {"id": VOD_ID, "playlist": 0, "subgroup": 1, "subgroupName": "红果片源"}
        )

        result = self.spider.detailContent([resume_id])

        vod = result["list"][0]
        self.assertEqual("红果片源$$$其他片源", vod["vod_play_from"])
        self.assertEqual("第1集$b1#第2集$b2$$$第1集$a1#第2集$a2", vod["vod_play_url"])

    def test_stale_resume_coordinates_fall_back_to_inner_detail(self):
        self.init_inner(inner_source("其他片源$$$红果片源", "第1集$a1#第2集$a2$$$第1集$b1#第2集$b2"))
        # playlist 2 超出平铺目标数,选集定位失败也要退回详情而不是抛错
        resume_id = self.spider._encode_resume_id({"id": VOD_ID, "playlist": 2})

        result = self.spider.detailContent([resume_id])

        self.assertEqual(VOD_ID, result["list"][0]["vod_id"])
        self.assertEqual("其他片源$$$红果片源", result["list"][0]["vod_play_from"])

    def test_drive_share_target_still_goes_through_parse(self):
        self.init_inner(inner_source("115", "第1集$https://115cdn.com/s/abc#第2集$https://115cdn.com/s/def"))
        resume_id = self.spider._encode_resume_id({"id": VOD_ID, "playlist": 0})
        sentinel = {"list": [{"vod_id": "parsed"}]}

        with patch.object(Spider, "_parse", return_value=sentinel) as parse:
            result = self.spider.detailContent([resume_id])

        self.assertIs(sentinel, result)
        parse.assert_called_once_with(
            "https://115cdn.com/s/abc", {"id": VOD_ID, "playlist": 0}
        )

    def test_missing_detail_still_raises(self):
        self.init_inner("class Spider:\n    def init(self, extend=''):\n        return None\n\n    def detailContent(self, ids):\n        return {'list': []}\n")
        resume_id = self.spider._encode_resume_id({"id": VOD_ID, "playlist": 0})

        with self.assertRaises(ValueError):
            self.spider.detailContent([resume_id])


if __name__ == "__main__":
    unittest.main()
