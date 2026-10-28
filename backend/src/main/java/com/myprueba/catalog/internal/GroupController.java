package com.myprueba.catalog.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.shared.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-groups")
class GroupController {

    private final GroupService groups;
    private final CurrentBusiness business;

    GroupController(GroupService groups, CurrentBusiness business) {
        this.groups = groups;
        this.business = business;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse create(@RequestBody GroupRequest request) {
        return groups.create(business.id(), request);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    GroupResponse update(@PathVariable String id, @RequestBody GroupRequest request) {
        return groups.update(business.id(), Ids.parseOrNotFound(id), request);
    }

    @PostMapping(path = "/{id}/variants", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ProductResponse addVariant(@PathVariable String id, @RequestBody VariantRequest request) {
        return groups.addVariant(business.id(), Ids.parseOrNotFound(id), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        groups.delete(business.id(), Ids.parseOrNotFound(id));
    }
}
