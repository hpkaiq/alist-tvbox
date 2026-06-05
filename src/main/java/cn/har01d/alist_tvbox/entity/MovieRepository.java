package cn.har01d.alist_tvbox.entity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Integer> {
    List<Movie> getByName(String name);
    Page<Movie> findByNameContains(String name, Pageable pageable);
    Page<Movie> findByYearAndNameContains(Integer year, String name, Pageable pageable);
}
