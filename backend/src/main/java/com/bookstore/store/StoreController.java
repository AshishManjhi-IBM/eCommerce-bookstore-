package com.bookstore.store;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.common.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Store service: stores, catalogs and policies. Writes are ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/stores")
public class StoreController {

    public record StoreRequest(@NotBlank String name) {
    }

    public record StoreView(Long id, String name, boolean active) {
    }

    public record StorePolicyRequest(@NotBlank String title, @NotBlank String description) {
    }

    public record StorePolicyView(Long id, Long storeId, String title, String description) {
    }

    public record CatalogRequest(@NotBlank String name) {
    }

    public record CatalogView(Long id, Long storeId, String name, boolean active) {
    }

    private final StoreRepository stores;
    private final StorePolicyRepository policies;
    private final CatalogRepository catalogs;

    public StoreController(StoreRepository stores, StorePolicyRepository policies, CatalogRepository catalogs) {
        this.stores = stores;
        this.policies = policies;
        this.catalogs = catalogs;
    }

    @GetMapping
    public List<StoreView> list() {
        return stores.findAll().stream().map(s -> new StoreView(s.getId(), s.getName(), s.isActive())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreView create(@Valid @RequestBody StoreRequest req) {
        Store s = new Store();
        s.setName(req.name());
        s = stores.save(s);
        return new StoreView(s.getId(), s.getName(), s.isActive());
    }

    @GetMapping("/{id}/policies")
    public List<StorePolicyView> listPolicies(@PathVariable Long id) {
        requireStore(id);
        return policies.findByStoreId(id).stream()
                .map(p -> new StorePolicyView(p.getId(), p.getStoreId(), p.getTitle(), p.getDescription())).toList();
    }

    @PostMapping("/{id}/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public StorePolicyView createPolicy(@PathVariable Long id, @Valid @RequestBody StorePolicyRequest req) {
        requireStore(id);
        StorePolicy p = new StorePolicy();
        p.setStoreId(id);
        p.setTitle(req.title());
        p.setDescription(req.description());
        p = policies.save(p);
        return new StorePolicyView(p.getId(), p.getStoreId(), p.getTitle(), p.getDescription());
    }

    @GetMapping("/{id}/catalogs")
    public List<CatalogView> listCatalogs(@PathVariable Long id) {
        requireStore(id);
        return catalogs.findByStoreId(id).stream()
                .map(c -> new CatalogView(c.getId(), c.getStoreId(), c.getName(), c.isActive())).toList();
    }

    @PostMapping("/{id}/catalogs")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogView createCatalog(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        requireStore(id);
        CatalogEntity c = new CatalogEntity();
        c.setStoreId(id);
        c.setName(req.name());
        c = catalogs.save(c);
        return new CatalogView(c.getId(), c.getStoreId(), c.getName(), c.isActive());
    }

    private void requireStore(Long id) {
        if (!stores.existsById(id)) {
            throw ApiException.notFound("Store not found");
        }
    }
}
