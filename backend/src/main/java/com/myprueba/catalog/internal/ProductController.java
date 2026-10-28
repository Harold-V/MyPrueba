package com.myprueba.catalog.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.catalog.ProductView;
import com.myprueba.shared.Ids;
import com.myprueba.shared.Money;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
class ProductController {

    private final ProductService products;
    private final CurrentBusiness business;

    ProductController(ProductService products, CurrentBusiness business) {
        this.products = products;
        this.business = business;
    }

    @GetMapping
    List<ProductResponse> list() {
        return products.list(business.id()).stream()
                .map(ProductController::toResponse)
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductResponse> create(@RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(products.create(business.id(), request)));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ProductResponse update(@PathVariable String id, @RequestBody ProductRequest request) {
        return toResponse(products.update(business.id(), Ids.parseOrNotFound(id), request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        products.delete(business.id(), Ids.parseOrNotFound(id));
    }

    private static ProductResponse toResponse(ProductView product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                Money.format(product.priceCents()),
                product.costCents() == null ? null : Money.format(product.costCents()));
    }
}
