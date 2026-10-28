package com.myprueba.inventory.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.shared.Ids;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
class InventoryController {

    private final InventoryService inventory;
    private final CurrentBusiness business;

    InventoryController(InventoryService inventory, CurrentBusiness business) {
        this.inventory = inventory;
        this.business = business;
    }

    @GetMapping
    InventoryResponse overview(@RequestParam(defaultValue = "false") boolean lowOnly) {
        return inventory.overview(business.id(), lowOnly);
    }

    @PutMapping(path = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    InventoryItem configure(@PathVariable String productId, @RequestBody TrackingRequest request) {
        return inventory.configure(business.id(), Ids.parseOrNotFound(productId), request);
    }

    @PostMapping(path = "/{productId}/adjustments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    InventoryItem adjust(@PathVariable String productId, @RequestBody AdjustmentRequest request) {
        return inventory.adjust(business.id(), Ids.parseOrNotFound(productId), request);
    }

    @GetMapping("/{productId}/movements")
    List<StockMovement> movements(@PathVariable String productId) {
        return inventory.movements(business.id(), Ids.parseOrNotFound(productId));
    }
}
