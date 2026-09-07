package cn.har01d.alist_tvbox.service.metadata;

import cn.har01d.alist_tvbox.dto.EpisodeAirDate;
import cn.har01d.alist_tvbox.dto.MetadataDetails;
import cn.har01d.alist_tvbox.entity.SettingRepository;
import cn.har01d.alist_tvbox.service.TmdbEndpoint;
import cn.har01d.alist_tvbox.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TMDB 播出日程收录口径:昨日/今日已播的分集仍进 upcoming —— 时间轴「昨天/今天」分组靠它,
 * 只收严格未来会把刚播出的集在播出日当天的元数据刷新时从 schedule 快照里洗掉。
 * 已播判定按播出时刻(air_date 当日 20:00)而非日期粒度:播出日当天 20:00 前刷新即算已播,
 * 点映礼 N 集同日上架的剧已播虚高(线上 28 被记成 33)。
 */
class TmdbMetadataProviderScheduleTest {
    private static final ZoneId ZONE = ZoneId.of(Constants.ZONE_ID);

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final TmdbMetadataProvider provider;

    TmdbMetadataProviderScheduleTest() {
        MetadataHttp metadataHttp = Mockito.mock(MetadataHttp.class);
        Mockito.when(metadataHttp.create()).thenReturn(restTemplate);
        provider = new TmdbMetadataProvider(new TmdbEndpoint(Mockito.mock(SettingRepository.class)), metadataHttp, new MetadataHealth(), null, null,
                null, null);
    }

    @Test
    void upcomingKeepsYesterdayAiredEpisodes() {
        LocalDate today = LocalDate.now(ZONE);
        String key = Constants.TMDB_API_KEY;
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/9521?language=zh-CN&append_to_response=images&api_key=" + key))
                .andRespond(withSuccess("{\"id\":9521,\"name\":\"慕兰之战\",\"status\":\"Returning Series\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/9521/alternative_titles?api_key=" + key))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/9521/credits?language=zh-CN&api_key=" + key))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/9521/season/1?language=zh-CN&api_key=" + key))
                .andRespond(withSuccess(seasonBody(today), MediaType.APPLICATION_JSON));

        MetadataDetails details = provider.details("9521", 1);

        assertEquals(2, details.getAiredEpisodes(), "7天前/昨日已播(当日集的已播口径墙钟相关,直测覆盖)");
        assertEquals(List.of(11, 13),
                details.getUpcoming().stream().map(EpisodeAirDate::getEpisode).toList(),
                "昨日已播 + 未来集都在日程,7 天前的已播集不进");
        assertEquals(today.minusDays(1).atTime(20, 0).atZone(ZONE).toInstant().toEpochMilli(),
                details.getUpcoming().get(0).getAirTime(), "昨日集落昨日 20:00 桶");
        assertEquals(today.plusDays(6).atTime(20, 0).atZone(ZONE).toInstant().toEpochMilli(),
                details.getNextAirTime(), "nextAirTime 仍严格取未来集");
    }

    @Test
    void massReleaseNotAiredBeforeAirHour() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        MetadataDetails details = new MetadataDetails();
        TmdbMetadataProvider.applySeasonEpisodes(details, massReleaseSeason(today),
                today.atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli());

        assertEquals(33, details.getTotalEpisodes());
        assertEquals(28, details.getAiredEpisodes(), "播出日当天 20:00 前,当日点映集不算已播");
        assertEquals(today.atTime(20, 0).atZone(ZONE).toInstant().toEpochMilli(),
                details.getNextAirTime(), "下集播出 = 当日 20:00");
        assertEquals(List.of(29, 30, 31, 32, 33),
                details.getUpcoming().stream().map(EpisodeAirDate::getEpisode).toList(),
                "当日待播集进日程(时间轴「今天」分组)");
    }

    @Test
    void massReleaseAiredAfterAirHour() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        MetadataDetails details = new MetadataDetails();
        TmdbMetadataProvider.applySeasonEpisodes(details, massReleaseSeason(today),
                today.atTime(21, 0).atZone(ZONE).toInstant().toEpochMilli());

        assertEquals(33, details.getAiredEpisodes(), "播出时刻(20:00)一过即算已播");
        assertNull(details.getNextAirTime());
        assertEquals(List.of(29, 30, 31, 32, 33),
                details.getUpcoming().stream().map(EpisodeAirDate::getEpisode).toList(),
                "当日已播集仍在日程(昨天/今天窗口),时间轴不空档");
    }

    // ---------- 线上事故回归:海贼王 sub48 卡 ENDED ----------
    // 海贼王 TMDB 37854 是全剧绝对集号形态(S23 已播 E1176/注册 26 集):season=1 订阅只拉 S1,
    // 剧级排播被「next.season_number == season」过滤丢弃 → nextAirTime 恒空、已播停在滞后口径,
    // 集齐滞后总数即误判完结且重开条件「官方已播 > 本地」永不满足。

    @Test
    void wholeShowAbsoluteNumberingAdoptsShowLevelSignals() {
        LocalDate today = LocalDate.now(ZONE);
        String key = Constants.TMDB_API_KEY;
        String tvBody = "{\"id\":37854,\"name\":\"航海王\",\"status\":\"Returning Series\","
                + "\"number_of_seasons\":23,\"number_of_episodes\":1181,"
                + "\"seasons\":[{\"season_number\":1,\"episode_count\":61},"
                + "{\"season_number\":23,\"episode_count\":26}],"
                + "\"last_episode_to_air\":{\"episode_number\":1176,\"season_number\":23,\"air_date\":\""
                + today.minusDays(8) + "\"},"
                + "\"next_episode_to_air\":{\"episode_number\":1177,\"season_number\":23,\"air_date\":\""
                + today.plusDays(6) + "\"}}";
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/37854?language=zh-CN&append_to_response=images&api_key=" + key))
                .andRespond(withSuccess(tvBody, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/37854/alternative_titles?api_key=" + key))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/37854/credits?language=zh-CN&api_key=" + key))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.themoviedb.org/3/tv/37854/season/1?language=zh-CN&api_key=" + key))
                .andRespond(withSuccess("{\"episodes\":[" + String.join(",",
                        episode(1, today.minusYears(20)), episode(2, today.minusYears(20))) + "]}",
                        MediaType.APPLICATION_JSON));

        MetadataDetails details = provider.details("37854", 1);

        assertEquals(1176, details.getAiredEpisodes(), "剧级最近已播集号才是全剧权威已播");
        assertEquals(1181, details.getTotalEpisodes(), "总数顶到 number_of_episodes");
        assertEquals(today.plusDays(6).atTime(20, 0).atZone(ZONE).toInstant().toEpochMilli(),
                details.getNextAirTime(), "下集排播不再按季过滤(下集在 S23 与 S1 同一连续集号空间)");
        assertTrue(details.getUpcoming().stream().anyMatch(e -> e.getEpisode() == 1177),
                "下集排播行进时间轴");
    }

    @Test
    void perSeasonNumberingKeepsSeasonScope() throws Exception {
        MetadataDetails details = new MetadataDetails();
        details.setTotalEpisodes(8);
        details.setAiredEpisodes(8);
        details.setUpcoming(new java.util.ArrayList<>());
        JsonNode tv = new ObjectMapper().readTree("{\"number_of_episodes\":24,"
                + "\"seasons\":[{\"season_number\":3,\"episode_count\":8}],"
                + "\"last_episode_to_air\":{\"episode_number\":8,\"season_number\":3,\"air_date\":\"2026-08-30\"},"
                + "\"next_episode_to_air\":{\"episode_number\":1,\"season_number\":4,\"air_date\":\"2026-10-01\"}}");

        TmdbMetadataProvider.adoptWholeShowSignals(details, tv, 1);

        assertEquals(8, details.getAiredEpisodes(), "分季编号剧(集号 ≤ 本季注册数)维持季口径");
        assertEquals(8, details.getTotalEpisodes());
        assertNull(details.getNextAirTime(), "别季排播不混入 S1 订阅");
        assertTrue(details.getUpcoming().isEmpty());
    }

    @Test
    void absoluteAdoptionOnlyRaisesAndGatesOnSeasonOne() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tv = mapper.readTree("{\"number_of_episodes\":1181,"
                + "\"seasons\":[{\"season_number\":1,\"episode_count\":61},"
                + "{\"season_number\":23,\"episode_count\":26}],"
                + "\"last_episode_to_air\":{\"episode_number\":1176,\"season_number\":23,\"air_date\":\"2026-08-30\"},"
                + "\"next_episode_to_air\":{\"episode_number\":1177,\"season_number\":23,\"air_date\":\"2026-09-13\"}}");
        MetadataDetails leading = new MetadataDetails();
        leading.setTotalEpisodes(1185);
        leading.setAiredEpisodes(1180); // B站领先口径(柯南形态)
        leading.setUpcoming(new java.util.ArrayList<>());

        TmdbMetadataProvider.adoptWholeShowSignals(leading, tv, 1);

        assertEquals(1180, leading.getAiredEpisodes(), "只升不降:领先口径不被剧级滞后值回拉");
        assertEquals(1185, leading.getTotalEpisodes());
        assertNotNull(leading.getNextAirTime(), "排播仍采纳(完结判定靠它)");

        MetadataDetails seasonTwo = new MetadataDetails();
        seasonTwo.setTotalEpisodes(26);
        seasonTwo.setAiredEpisodes(5);
        TmdbMetadataProvider.adoptWholeShowSignals(seasonTwo, tv, 2);
        assertEquals(5, seasonTwo.getAiredEpisodes(), "分季订阅不混入剧级信号");
        assertNull(seasonTwo.getNextAirTime());
    }

    private static String seasonBody(LocalDate today) {
        return "{\"episodes\":[" + String.join(",",
                episode(10, today.minusDays(7)), episode(11, today.minusDays(1)),
                episode(13, today.plusDays(6))) + "]}";
    }

    /** 线上点映礼形态:1-28 集已播,29-33 集全部排在同一天(今天 20:00)。 */
    private static JsonNode massReleaseSeason(LocalDate today) throws Exception {
        StringBuilder sb = new StringBuilder("{\"episodes\":[");
        for (int i = 1; i <= 28; i++) {
            sb.append(episode(i, today.minusDays(30 - i))).append(',');
        }
        for (int i = 29; i <= 33; i++) {
            sb.append(episode(i, today));
            if (i < 33) {
                sb.append(',');
            }
        }
        return new ObjectMapper().readTree(sb.append("]}").toString());
    }

    private static String episode(int number, LocalDate date) {
        return "{\"episode_number\":" + number + ",\"air_date\":\"" + date + "\"}";

    }
}
