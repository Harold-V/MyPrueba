package com.myprueba.catalog.internal;

import com.myprueba.shared.Ids;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Products with variants (size, colour, presentation). Each variant is a product of its own, named
 * "Group — Variant", so it is sold, bought and counted separately.
 */
@Service
class GroupService {

    static final int MAX_GROUP_NAME = 40;
    static final int MAX_VARIANT = 36;

    private final GroupRepository groups;
    private final ProductRepository products;
    private final ProductService catalog;
    private final CategoryService categories;
    private final Clock clock;

    GroupService(
            GroupRepository groups,
            ProductRepository products,
            ProductService catalog,
            CategoryService categories,
            Clock clock) {
        this.groups = groups;
        this.products = products;
        this.catalog = catalog;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    GroupResponse create(UUID businessId, GroupRequest request) {
        Violations violations = new Violations();
        String name = violations.capture(() -> Text.required("name", request.name(), MAX_GROUP_NAME));
        UUID categoryId = violations.capture(() -> Ids.optional("categoryId", request.categoryId()));
        Long price = violations.capture(() -> ProductService.optionalMoney("price", request.price()));
        Long cost = violations.capture(() -> ProductService.optionalMoney("cost", request.cost()));
        List<VariantRequest> requested = request.variants() == null ? List.of() : request.variants();
        if (requested.isEmpty()) {
            violations.add("variants", "required");
        }
        List<ProductService.Draft> drafts = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            ProductService.Draft draft =
                    variant(violations, "variants[" + i + "].", requested.get(i), name, categoryId, price, cost);
            if (draft != null && !labels.add(ProductService.key(draft.variant()))) {
                violations.add("variants[" + i + "].name", "duplicate");
            }
            drafts.add(draft);
        }
        violations.throwIfAny();
        categories.require(businessId, categoryId);
        UUID id = UUID.randomUUID();
        groups.insert(businessId, new GroupRepository.Group(id, name, categoryId), clock.instant());
        List<ProductResponse> variants = drafts.stream()
                .map(draft -> catalog.insert(businessId, withGroup(draft, id)).response())
                .toList();
        return new GroupResponse(id, name, categoryId, variants);
    }

    @Transactional
    ProductResponse addVariant(UUID businessId, UUID groupId, VariantRequest request) {
        GroupRepository.Group group = groups.find(businessId, groupId).orElseThrow(NotFoundException::new);
        Violations violations = new Violations();
        ProductService.Draft draft = variant(violations, "", request, group.name(), group.categoryId(), null, null);
        violations.throwIfAny();
        return catalog.insert(businessId, withGroup(draft, groupId)).response();
    }

    /** Renames every variant and moves them all to the group's category. */
    @Transactional
    GroupResponse update(UUID businessId, UUID groupId, GroupRequest request) {
        groups.find(businessId, groupId).orElseThrow(NotFoundException::new);
        Violations violations = new Violations();
        String name = violations.capture(() -> Text.required("name", request.name(), MAX_GROUP_NAME));
        UUID categoryId = violations.capture(() -> Ids.optional("categoryId", request.categoryId()));
        violations.throwIfAny();
        categories.require(businessId, categoryId);
        groups.update(businessId, new GroupRepository.Group(groupId, name, categoryId), clock.instant());
        for (ProductRow row : products.findByGroup(businessId, groupId)) {
            String renamed = ProductService.variantName(name, row.variant());
            catalog.checkUnique(businessId, renamed, null, row.id());
            products.update(
                    businessId,
                    new ProductRepository.NewProduct(
                            row.id(),
                            renamed,
                            row.priceCents(),
                            row.costCents(),
                            categoryId,
                            row.barcode(),
                            groupId,
                            row.variant()),
                    clock.instant());
        }
        List<ProductResponse> variants = products.findByGroup(businessId, groupId).stream()
                .map(ProductRow::response)
                .toList();
        return new GroupResponse(groupId, name, categoryId, variants);
    }

    @Transactional
    void delete(UUID businessId, UUID groupId) {
        groups.find(businessId, groupId).orElseThrow(NotFoundException::new);
        groups.delete(businessId, groupId);
    }

    /** A variant's price and cost default to the group's; {@code null} when the variant is invalid. */
    private static ProductService.Draft variant(
            Violations violations,
            String prefix,
            VariantRequest request,
            String groupName,
            UUID categoryId,
            Long groupPrice,
            Long groupCost) {
        String label = violations.capture(() -> Text.required(prefix + "name", request.name(), MAX_VARIANT));
        boolean hasPrice = request.price() != null && !request.price().isBlank();
        Long price = hasPrice || groupPrice == null
                ? violations.capture(() -> Money.parseCents(prefix + "price", request.price()))
                : groupPrice;
        Long ownCost = violations.capture(() -> ProductService.optionalMoney(prefix + "cost", request.cost()));
        Long stock = violations.capture(() -> ProductService.optionalQuantity(prefix + "stock", request.stock()));
        Long min = violations.capture(() -> ProductService.optionalQuantity(prefix + "minStock", request.minStock()));
        String barcode = violations.capture(
                () -> Text.optional(prefix + "barcode", request.barcode(), ProductService.MAX_BARCODE));
        if (label == null || price == null || groupName == null) {
            return null;
        }
        return new ProductService.Draft(
                ProductService.variantName(groupName, label),
                price,
                ownCost != null ? ownCost : groupCost,
                categoryId,
                barcode,
                null,
                label,
                stock == null ? 0 : stock,
                min);
    }

    private static ProductService.Draft withGroup(ProductService.Draft draft, UUID groupId) {
        return new ProductService.Draft(
                draft.name(),
                draft.priceCents(),
                draft.costCents(),
                draft.categoryId(),
                draft.barcode(),
                groupId,
                draft.variant(),
                draft.stockMilli(),
                draft.minStockMilli());
    }
}
