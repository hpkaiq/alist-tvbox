package cn.har01d.alist_tvbox.entity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShareRepository extends JpaRepository<Share, Integer> {
    boolean existsByPath(String path);

    Share findByPath(String path);

    int countByType(int type);

    List<Share> findByType(int type);

    Page<Share> findByType(int type, Pageable pageable);

    Page<Share> findByPathContains(String keyword, Pageable pageable);

    Page<Share> findByTypeAndPathContains(int type, String keyword, Pageable pageable);

    List<Share> findByTempTrue();

    // 同一 (type, shareId) 可存在多行(不同密码/订阅挂载与 temp 推送,path 唯一而非 shareId),须按列表消费
    List<Share> findByTypeAndShareId(Integer type, String shareId);

    List<Share> findByTypeAndShareIdAndTempTrue(Integer type, String shareId);
}
