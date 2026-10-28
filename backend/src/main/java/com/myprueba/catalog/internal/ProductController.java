package com.myprueba.catalog.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.shared.Ids;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
class ProductController {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductService products;
    private final ProductImporter importer;
    private final CurrentBusiness business;

    ProductController(ProductService products, ProductImporter importer, CurrentBusiness business) {
        this.products = products;
        this.importer = importer;
        this.business = business;
    }

    @GetMapping
    List<ProductResponse> list(
            @RequestParam(required = false) String categoryId, @RequestParam(required = false) String q) {
        return products.rows(business.id(), Ids.optional("categoryId", categoryId), q).stream()
                .map(ProductRow::response)
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ProductResponse create(@RequestBody ProductRequest request) {
        return products.create(business.id(), request).response();
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ProductResponse update(@PathVariable String id, @RequestBody ProductRequest request) {
        return products.update(business.id(), Ids.parseOrNotFound(id), request).response();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        products.delete(business.id(), Ids.parseOrNotFound(id));
    }

    /**
     * The file travels as the raw body (not multipart), so this write still needs a CORS preflight
     * like every JSON write: the CSRF defence relies on that.
     */
    @PostMapping(
            path = "/import",
            consumes = {XLSX, "text/csv"})
    ImportResponse importFile(@RequestHeader("Content-Type") String contentType, @RequestBody byte[] file) {
        return importer.importFile(business.id(), file, contentType.startsWith(XLSX));
    }
}
