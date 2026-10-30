package com.killa.cocina.modules.inventory.infrastructure;

import com.killa.cocina.modules.inventory.domain.Category;
import com.killa.cocina.modules.inventory.domain.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link CategoryRepository}. */
@Repository
class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository categories;

    CategoryRepositoryAdapter(CategoryJpaRepository categories) {
        this.categories = categories;
    }

    @Override
    public Optional<Category> findById(long establishmentId, long categoryId) {
        return categories.findByIdAndEstablishmentId(categoryId, establishmentId)
                .map(CategoryEntity::toDomain);
    }

    @Override
    public boolean existsByName(long establishmentId, String name) {
        return categories.existsByEstablishmentIdAndName(establishmentId, name);
    }

    @Override
    public boolean existsByNameExcluding(long establishmentId, String name, long excludedCategoryId) {
        return categories.existsByEstablishmentIdAndNameAndIdNot(establishmentId, name,
                excludedCategoryId);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = category.id() == null
                ? CategoryEntity.from(category)
                : categories.findById(category.id()).orElseGet(() -> CategoryEntity.from(category));
        if (category.id() != null) {
            entity.apply(category);
        }
        return categories.save(entity).toDomain();
    }

    @Override
    public List<Category> findAll(long establishmentId, int page, int size) {
        return categories.findByEstablishmentIdOrderByNameAsc(establishmentId,
                        PageRequest.of(page, size, Sort.by("name").ascending()))
                .stream()
                .map(CategoryEntity::toDomain)
                .toList();
    }

    @Override
    public long count(long establishmentId) {
        return categories.countByEstablishmentId(establishmentId);
    }
}
