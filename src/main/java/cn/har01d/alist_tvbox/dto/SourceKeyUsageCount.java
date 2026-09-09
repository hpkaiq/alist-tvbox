package cn.har01d.alist_tvbox.dto;

/**
 * 播放记录按 sourceKey 的使用次数聚合投影(插件预热清单排序依据)。
 */
public interface SourceKeyUsageCount {
    String getSourceKey();

    long getTotal();
}
