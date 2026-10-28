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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-categories")
class CategoryController {

    private final CategoryService categories;
    private final CurrentBusiness business;

    CategoryController(CategoryService categories, CurrentBusiness business) {
        this.categories = categories;
        this.business = business;
    }

    @GetMapping
    List<CategoryResponse> list() {
        return categories.list(business.id());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@RequestBody CategoryRequest request) {
        return categories.create(business.id(), request);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    CategoryResponse update(@PathVariable String id, @RequestBody CategoryRequest request) {
        return categories.update(business.id(), Ids.parseOrNotFound(id), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        categories.delete(business.id(), Ids.parseOrNotFound(id));
    }
}
