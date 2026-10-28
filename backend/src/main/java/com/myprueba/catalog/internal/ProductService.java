package com.myprueba.catalog.internal;

import com.myprueba.catalog.ProductCatalog;
import com.myprueba.catalog.ProductCreated;
import com.myprueba.catalog.ProductView;
import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Ids;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Quantity;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
import java.text.Normalizer;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService implements ProductCatalog {

    static final int MAX_NAME = 80;
    static final int MAX_BARCODE = 40;
    static final String VARIANT_SEPARATOR = " — ";

    /** A validated product ready to store, with the stock it starts with. */
    record Draft(
            String name,
            long priceCents,
            Long costCents,
            UUID categoryId,
            String barcode,
            UUID groupId,
            String variant,
            long stockMilli,
            Long minStockMilli) {}

    private final ProductRepository products;
    private final CategoryService categories;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    ProductService(
            ProductRepository products, CategoryService categories, Clock clock, ApplicationEventPublisher events) {
        this.products = products;
        this.categories = categories;
        this.clock = clock;
        this.events = events;
    }

    /** Uniqueness key of names: case-insensitive. */
    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Search key: case- and accent-insensitive ("Jabón" matches "jabon"). */
    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    static String variantName(String groupName, String variant) {
        return groupName + VARIANT_SEPARATOR + variant;
    }

    @Override
    public Optional<ProductView> find(UUID businessId, UUID productId) {
        return products.find(businessId, productId).map(ProductRow::view);
    }

    @Override
    public List<ProductView> list(UUID businessId) {
        return products.findAll(businessId).stream().map(ProductRow::view).toList();
    }

    @Override
    public List<ProductView> search(UUID businessId, UUID categoryId, String query) {
        return rows(businessId, categoryId, query).stream()
                .map(ProductRow::view)
                .toList();
    }

    List<ProductRow> rows(UUID businessId, UUID categoryId, String query) {
        String folded = query == null || query.isBlank() ? null : fold(query.strip());
        return products.findAll(businessId).stream()
                .filter(p -> categoryId == null || categoryId.equals(p.categoryId()))
                .filter(p -> folded == null
                        || fold(p.name()).contains(folded)
                        || (p.barcode() != null && fold(p.barcode()).contains(folded)))
                .toList();
    }

    @Transactional
    ProductRow create(UUID businessId, ProductRequest request) {
        Violations violations = new Violations();
        String name = violations.capture(() -> Text.required("name", request.name(), MAX_NAME));
        Long price = violations.capture(() -> Money.parseCents("price", request.price()));
        Long cost = violations.capture(() -> optionalMoney("cost", request.cost()));
        UUID categoryId = violations.capture(() -> Ids.optional("categoryId", request.categoryId()));
        String barcode = violations.capture(() -> Text.optional("barcode", request.barcode(), MAX_BARCODE));
        Long stock = violations.capture(() -> optionalQuantity("stock", request.stock()));
        Long min = violations.capture(() -> optionalQuantity("minStock", request.minStock()));
        violations.throwIfAny();
        categories.require(businessId, categoryId);
        return insert(
                businessId,
                new Draft(name, price, cost, categoryId, barcode, null, null, stock == null ? 0 : stock, min));
    }

    /** Stores a validated product and gives it its starting stock (shared by groups and imports). */
    ProductRow insert(UUID businessId, Draft draft) {
        checkUnique(businessId, draft.name(), draft.barcode(), null);
        UUID id = UUID.randomUUID();
        try {
            products.insert(
                    businessId,
                    new ProductRepository.NewProduct(
                            id,
                            draft.name(),
                            draft.priceCents(),
                            draft.costCents(),
                            draft.categoryId(),
                            draft.barcode(),
                            draft.groupId(),
                            draft.variant()),
                    clock.instant());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("product_name_taken");
        }
        events.publishEvent(new ProductCreated(businessId, id, draft.stockMilli(), draft.minStockMilli()));
        return products.find(businessId, id).orElseThrow();
    }

    @Transactional
    ProductRow update(UUID businessId, UUID id, ProductRequest request) {
        ProductRow existing = products.find(businessId, id).orElseThrow(NotFoundException::new);
        boolean variant = existing.groupId() != null;
        Violations violations = new Violations();
        // A variant is renamed through its label; its name and category follow its group.
        String label = variant ? violations.capture(() -> Text.required("variant", request.variant(), 36)) : null;
        String name = variant
                ? (label == null ? null : variantName(existing.groupName(), label))
                : violations.capture(() -> Text.required("name", request.name(), MAX_NAME));
        Long price = violations.capture(() -> Money.parseCents("price", request.price()));
        Long cost = violations.capture(() -> optionalMoney("cost", request.cost()));
        UUID categoryId = variant
                ? existing.categoryId()
                : violations.capture(() -> Ids.optional("categoryId", request.categoryId()));
        String barcode = violations.capture(() -> Text.optional("barcode", request.barcode(), MAX_BARCODE));
        violations.throwIfAny();
        if (!variant) {
            categories.require(businessId, categoryId);
        }
        checkUnique(businessId, name, barcode, id);
        products.update(
                businessId,
                new ProductRepository.NewProduct(id, name, price, cost, categoryId, barcode, existing.groupId(), label),
                clock.instant());
        return products.find(businessId, id).orElseThrow();
    }

    @Transactional
    void delete(UUID businessId, UUID id) {
        if (products.delete(businessId, id) == 0) {
            throw new NotFoundException();
        }
    }

    void checkUnique(UUID businessId, String name, String barcode, UUID exceptId) {
        if (products.nameTaken(businessId, key(name), exceptId)) {
            throw new ConflictException("product_name_taken");
        }
        if (barcode != null && products.barcodeTaken(businessId, barcode, exceptId)) {
            throw new ConflictException("barcode_taken");
        }
    }

    boolean nameTaken(UUID businessId, String name) {
        return products.nameTaken(businessId, key(name), null);
    }

    boolean barcodeTaken(UUID businessId, String barcode) {
        return products.barcodeTaken(businessId, barcode, null);
    }

    static Long optionalMoney(String field, String raw) {
        return raw == null || raw.isBlank() ? null : Money.parseCents(field, raw);
    }

    static Long optionalQuantity(String field, String raw) {
        return raw == null || raw.isBlank() ? null : Quantity.parseNonNegativeMilli(field, raw);
    }
}
