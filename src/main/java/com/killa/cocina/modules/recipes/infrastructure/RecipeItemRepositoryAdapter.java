package com.killa.cocina.modules.recipes.infrastructure;

import com.killa.cocina.modules.recipes.domain.RecipeItem;
import com.killa.cocina.modules.recipes.domain.RecipeItemRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link RecipeItemRepository}. */
@Repository
class RecipeItemRepositoryAdapter implements RecipeItemRepository {

    private final RecipeItemJpaRepository items;

    RecipeItemRepositoryAdapter(RecipeItemJpaRepository items) {
        this.items = items;
    }

    @Override
    public List<RecipeItem> findByVersion(long versionId) {
        return items.findByVersionIdOrderBySortOrderAsc(versionId).stream()
                .map(RecipeItemEntity::toDomain)
                .toList();
    }

    @Override
    public void replaceForVersion(long versionId, List<RecipeItem> newItems) {
        items.deleteByVersionId(versionId);
        if (!newItems.isEmpty()) {
            items.saveAll(newItems.stream().map(RecipeItemEntity::from).toList());
        }
    }

    @Override
    public void deleteByVersion(long versionId) {
        items.deleteByVersionId(versionId);
    }
}
