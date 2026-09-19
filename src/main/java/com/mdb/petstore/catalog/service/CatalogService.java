package com.mdb.petstore.catalog.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mdb.petstore.catalog.dto.CategoryResponse;
import com.mdb.petstore.catalog.dto.ItemResponse;
import com.mdb.petstore.catalog.dto.ProductResponse;
import com.mdb.petstore.catalog.model.Category;
import com.mdb.petstore.catalog.model.CategoryDetails;
import com.mdb.petstore.catalog.model.Item;
import com.mdb.petstore.catalog.model.ItemDetails;
import com.mdb.petstore.catalog.model.Product;
import com.mdb.petstore.catalog.model.ProductDetails;
import com.mdb.petstore.catalog.repository.CategoryRepository;
import com.mdb.petstore.catalog.repository.ItemRepository;
import com.mdb.petstore.catalog.repository.ProductRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final String DEFAULT_LOCALE = "en-US";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ItemRepository itemRepository;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository,
            ItemRepository itemRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.itemRepository = itemRepository;
    }

    public List<CategoryResponse> getCategories(String locale) {
        String language = normalizeLocale(locale);
        var results = categoryRepository.findAll().stream().sorted(Comparator.comparing(Category::getId))
                .map(category -> categoryResponse(category, language)).toList();
        log.debug("Category listing locale={} results={}", language, results.size());
        return results;
    }

    public CategoryResponse getCategory(String categoryId, String locale) {
        String language = normalizeLocale(locale);
        log.debug("Category lookup id={} locale={}", categoryId, language);
        return categoryResponse(category(categoryId), language);
    }

    public List<ProductResponse> getProductsByCategory(String categoryId, String locale) {
        category(categoryId);
        String language = normalizeLocale(locale);
        var results = productRepository.findByCategoryId(categoryId).stream()
                .sorted(Comparator.comparing(Product::getId)).map(product -> productResponse(product, language)).toList();
        log.debug("Product listing categoryId={} locale={} results={}", categoryId, language, results.size());
        return results;
    }

    public ProductResponse getProduct(String productId, String locale) {
        String language = normalizeLocale(locale);
        log.debug("Product lookup id={} locale={}", productId, language);
        return productResponse(product(productId), language);
    }

    public List<ItemResponse> getItemsByProduct(String productId, String locale) {
        product(productId);
        String language = normalizeLocale(locale);
        var results = itemRepository.findByProductId(productId).stream().sorted(Comparator.comparing(Item::getId))
                .map(item -> itemResponse(item, language)).toList();
        log.debug("Item listing productId={} locale={} results={}", productId, language, results.size());
        return results;
    }

    public ItemResponse getItem(String itemId, String locale) {
        String language = normalizeLocale(locale);
        log.debug("Item lookup id={} locale={}", itemId, language);
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        return itemResponse(item, language);
    }

    public List<ProductResponse> searchProducts(String query, String locale) {
        String language = normalizeLocale(locale);
        List<String> tokens = query == null ? List.of() : Arrays.stream(query.toLowerCase(Locale.ROOT)
                .split("(?U)\\s+")).filter(token -> !token.isBlank()).distinct().toList();
        log.debug("Catalog search request locale={} tokens={}", language, tokens.size());
        if (tokens.isEmpty()) {
            log.debug("Catalog search results=0");
            return List.of();
        }
        // Resolve each item's own locale fallback, then group once to avoid one query per product.
        Map<String, List<Item>> itemsByProduct = itemRepository.findAll().stream()
                .collect(Collectors.groupingBy(Item::getProductId));
        var results = productRepository.findAll().stream()
                .filter(product -> matches(product, itemsByProduct.getOrDefault(product.getId(), List.of()),
                        tokens, language))
                .sorted(Comparator.comparing(Product::getId))
                .map(product -> productResponse(product, language)).toList();
        log.debug("Catalog search locale={} results={}", language, results.size());
        return results;
    }

    private boolean matches(Product product, List<Item> items, List<String> tokens, String locale) {
        ProductResponse localized = productResponse(product, locale);
        StringBuilder content = new StringBuilder();
        append(content, localized.name());
        append(content, localized.description());
        append(content, product.getCategoryId());
        for (Item item : items) {
            ItemDetails detail = resolve(item.getDetails(), ItemDetails::getLocale, locale);
            append(content, detail == null ? null : detail.getDescription());
        }
        String searchable = content.toString().toLowerCase(Locale.ROOT);
        return tokens.stream().allMatch(searchable::contains);
    }

    private static void append(StringBuilder content, String value) {
        if (value != null) {
            content.append(value).append('\n');
        }
    }

    private Category category(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private Product product(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private CategoryResponse categoryResponse(Category category, String locale) {
        CategoryDetails detail = resolve(category.getDetails(), CategoryDetails::getLocale, locale);
        return new CategoryResponse(category.getId(), detail == null ? null : detail.getName(),
                detail == null ? null : detail.getImage(), detail == null ? null : detail.getDescription());
    }

    private ProductResponse productResponse(Product product, String locale) {
        ProductDetails detail = resolve(product.getDetails(), ProductDetails::getLocale, locale);
        return new ProductResponse(product.getId(), product.getCategoryId(), detail == null ? null : detail.getName(),
                detail == null ? null : detail.getImage(), detail == null ? null : detail.getDescription());
    }

    private ItemResponse itemResponse(Item item, String locale) {
        ItemDetails detail = resolve(item.getDetails(), ItemDetails::getLocale, locale);
        return new ItemResponse(item.getId(), item.getProductId(), item.getCategoryId(),
                detail == null ? null : detail.getListPrice(), detail == null ? null : detail.getUnitCost(),
                detail == null ? null : detail.getImage(), detail == null ? null : detail.getDescription(),
                detail == null ? List.of() : detail.getAttributes());
    }

    private static String normalizeLocale(String locale) {
        return locale == null || locale.isBlank() ? DEFAULT_LOCALE
                : Locale.forLanguageTag(locale.strip().replace('_', '-')).toLanguageTag();
    }

    private static <T> T resolve(List<T> details, Function<T, String> localeOf, String locale) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.stream().filter(detail -> locale.equals(localeOf.apply(detail))).findFirst()
                .orElseGet(() -> details.stream().filter(detail -> DEFAULT_LOCALE.equals(localeOf.apply(detail)))
                        .findFirst().orElse(details.getFirst()));
    }
}
