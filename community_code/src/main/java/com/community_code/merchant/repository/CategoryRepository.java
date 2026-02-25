package com.community_code.merchant.repository;

import com.community_code.merchant.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByIdAndParentCategoryIsNull(Long id);

    List<Category> findByParentCategoryIsNullOrderByNameAsc();

    List<Category> findByParentCategoryIdOrderByNameAsc(Long parentCategoryId);

}
