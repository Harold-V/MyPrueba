package com.myprueba.catalog.internal;

import com.myprueba.catalog.ProductCatalog;
import com.myprueba.catalog.ProductView;
import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService implements ProductCatalog {

    private record Input(String name, String nameKey, long priceCents) {}

    private final ProductRepository products;
    private final Clock clock;

    ProductService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Override
    public Optional<ProductView> find(UUID businessId, UUID productId) {
        return products.find(businessId, productId);
    }

    List<ProductView> list(UUID businessId) {
        return products.findAll(businessId);
    }

    @Transactional
    ProductView create(UUID businessId, ProductRequest request) {
        Input input = parse(request);
        if (products.nameTaken(businessId, input.nameKey(), null)) {
            throw new ConflictException("product_name_taken");
        }
        UUID id = UUID.randomUUID();
        try {
            products.insert(businessId, id, input.name(), input.nameKey(), input.priceCents(), clock.instant());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("product_name_taken");
        }
        return new ProductView(id, input.name(), input.priceCents());
    }

    @Transactional
    ProductView update(UUID businessId, UUID id, ProductRequest request) {
        Input input = parse(request);
        products.find(businessId, id).orElseThrow(NotFoundException::new);
        if (products.nameTaken(businessId, input.nameKey(), id)) {
            throw new ConflictException("product_name_taken");
        }
        products.update(businessId, id, input.name(), input.nameKey(), input.priceCents(), clock.instant());
        return new ProductView(id, input.name(), input.priceCents());
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
        violations.throwIfAny();
        return new Input(name, name.toLowerCase(Locale.ROOT), price);
    }
}
