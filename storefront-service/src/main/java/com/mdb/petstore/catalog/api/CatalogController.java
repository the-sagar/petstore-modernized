package com.mdb.petstore.catalog.api;

import java.util.List;

import com.mdb.petstore.catalog.dto.CatalogPage;
import com.mdb.petstore.catalog.dto.CategoryResponse;
import com.mdb.petstore.catalog.dto.ItemResponse;
import com.mdb.petstore.catalog.dto.ProductResponse;
import com.mdb.petstore.catalog.service.CatalogService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> getCategories(@RequestParam(defaultValue = "en-US") String locale) {
        return catalogService.getCategories(locale);
    }

    @GetMapping("/categories/{categoryId}")
    public CategoryResponse getCategory(@PathVariable String categoryId,
            @RequestParam(defaultValue = "en-US") String locale) {
        return catalogService.getCategory(categoryId, locale);
    }

    @GetMapping("/categories/{categoryId}/products")
    public CatalogPage<ProductResponse> getProductsByCategory(@PathVariable String categoryId,
            @RequestParam(defaultValue = "en-US") String locale,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "2") int size) {
        return catalogService.getProductsByCategory(categoryId, locale, page, size);
    }

    @GetMapping("/products/{productId}")
    public ProductResponse getProduct(@PathVariable String productId,
            @RequestParam(defaultValue = "en-US") String locale) {
        return catalogService.getProduct(productId, locale);
    }

    @GetMapping("/products/{productId}/items")
    public CatalogPage<ItemResponse> getItemsByProduct(@PathVariable String productId,
            @RequestParam(defaultValue = "en-US") String locale,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "2") int size) {
        return catalogService.getItemsByProduct(productId, locale, page, size);
    }

    @GetMapping("/items/{itemId}")
    public ItemResponse getItem(@PathVariable String itemId,
            @RequestParam(defaultValue = "en-US") String locale) {
        return catalogService.getItem(itemId, locale);
    }

    @GetMapping("/search")
    public CatalogPage<ProductResponse> searchProducts(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "en-US") String locale,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "2") int size) {
        return catalogService.searchProducts(q, locale, page, size);
    }
}
