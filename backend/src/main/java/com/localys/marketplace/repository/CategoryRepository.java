package com.localys.marketplace.repository;

import com.localys.marketplace.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortOrderAscNameAsc();

    List<Category> findByParentIsNullOrderBySortOrderAscNameAsc();

    List<Category> findByParentIdOrderBySortOrderAscNameAsc(Long parentId);
}
