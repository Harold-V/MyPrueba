package com.myprueba.catalog.internal;

import com.myprueba.catalog.ProductCatalog;
import com.myprueba.catalog.ProductCreated;
import com.myprueba.catalog.ProductView;
import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Quantity;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
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

    private record Input(String name, String nameKey, long priceCents, Long costCents) {}

    private final ProductRepository products;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    ProductService(ProductRepository products, Clock clock, ApplicationEventPublisher events) {
        this.products = products;
        this.clock = clock;
        this.events = events;
    }

    @Override
    public Optional<ProductView> find(UUID businessId, UUID productId) {
        return products.find(businessId, productId);
    }

    @Override
    public List<ProductView> list(UUID businessId) {
        return products.findAll(businessId);
    }

    @Transactional
    ProductView create(UUID businessId, ProductRequest request) {
        Input input = parse(request);
        InitialStock stock = initialStock(request);
        if (products.nameTaken(businessId, input.nameKey(), null)) {
            throw new ConflictException("product_name_taken");
        }
        UUID id = UUID.randomUUID();
        try {
            products.insert(
                    businessId,
                    id,
                    input.name(),
                    input.nameKey(),
                    input.priceCents(),
                    input.costCents(),
                    clock.instant());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("product_name_taken");
        }
        events.publishEvent(new ProductCreated(businessId, id, stock.initial(), stock.min()));
        return new ProductView(id, input.name(), input.priceCents(), input.costCents());
    }

    private record InitialStock(long initial, Long min) {}

    private static InitialStock initialStock(ProductRequest request) {
        Violations violations = new Violations();
        Long initial = request.stock() == null || request.stock().isBlank()
                ? Long.valueOf(0)
                : violations.capture(() -> Quantity.parseNonNegativeMilli("stock", request.stock()));
        Long min = request.minStock() == null || request.minStock().isBlank()
                ? null
                : violations.capture(() -> Quantity.parseNonNegativeMilli("minStock", request.minStock()));
        violations.throwIfAny();
        return new InitialStock(initial, min);
    }

    @Transactional
    ProductView update(UUID businessId, UUID id, ProductRequest request) {
        Input input = parse(request);
        products.find(businessId, id).orElseThrow(NotFoundException::new);
        if (products.nameTaken(businessId, input.nameKey(), id)) {
            throw new ConflictException("product_name_taken");
        }
        products.update(
                businessId, id, input.name(), input.nameKey(), input.priceCents(), input.costCents(), clock.instant());
        return new ProductView(id, input.name(), input.priceCents(), input.costCents());
    }

    @Transactional
    void delete(UUID businessId, UUID id) {
        if (products.delete(businessId, id) == 0) {
            throw new NotFoundException();
        }
    }

    private static Input parse(ProductRequest request) {
        Violations violations = new Violations();
        String name = violations.capture(() -> Text.required("name", request.name(), 80));
        Long price = violations.capture(() -> Money.parseCents("price", request.price()));
        Long cost = request.cost() == null || request.cost().isBlank()
                ? null
                : violations.capture(() -> Money.parseCents("cost", request.cost()));
        violations.throwIfAny();
        return new Input(name, name.toLowerCase(Locale.ROOT), price, cost);
    }
}
