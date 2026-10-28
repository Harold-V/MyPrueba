package com.myprueba.catalog.internal;

import com.myprueba.shared.ConflictException;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Text;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product categories the business creates. Deleting one leaves its products without category. */
@Service
class CategoryService {

    static final int MAX_NAME = 60;

    private final CategoryRepository categories;
    private final Clock clock;

    CategoryService(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    List<CategoryResponse> list(UUID businessId) {
        return categories.findAll(businessId);
    }

    @Transactional
    CategoryResponse create(UUID businessId, CategoryRequest request) {
        String name = Text.required("name", request.name(), MAX_NAME);
        if (categories.findByName(businessId, ProductService.key(name)).isPresent()) {
            throw new ConflictException("category_name_taken");
        }
        CategoryResponse category = new CategoryResponse(UUID.randomUUID(), name);
        categories.insert(businessId, category, clock.instant());
        return category;
    }

    @Transactional
    CategoryResponse update(UUID businessId, UUID id, CategoryRequest request) {
        categories.find(businessId, id).orElseThrow(NotFoundException::new);
        String name = Text.required("name", request.name(), MAX_NAME);
        categories
                .findByName(businessId, ProductService.key(name))
                .filter(other -> !other.id().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("category_name_taken");
                });
        CategoryResponse category = new CategoryResponse(id, name);
        categories.update(businessId, category, clock.instant());
        return category;
    }

    @Transactional
    void delete(UUID businessId, UUID id) {
        if (categories.delete(businessId, id) == 0) {
            throw new NotFoundException();
        }
    }

    /** Fails with 404 {@code category_not_found} unless the category is absent or the business's own. */
    void require(UUID businessId, UUID categoryId) {
        if (categoryId != null && categories.find(businessId, categoryId).isEmpty()) {
            throw new NotFoundException("category_not_found");
        }
    }

    /** The category with this name (ignoring case), created when missing. */
    UUID findOrCreate(UUID businessId, String name) {
        return categories
                .findByName(businessId, ProductService.key(name))
                .map(CategoryResponse::id)
                .orElseGet(() -> create(businessId, new CategoryRequest(name)).id());
    }
}
