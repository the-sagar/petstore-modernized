package com.mdb.petstore.catalog.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.mdb.petstore.catalog.dto.CatalogPage;
import com.mdb.petstore.catalog.dto.CategoryResponse;
import com.mdb.petstore.catalog.repository.CatalogSearchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final CatalogSearchRepository searchRepository;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository,
            ItemRepository itemRepository, CatalogSearchRepository searchRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.itemRepository = itemRepository;
        this.searchRepository = searchRepository;
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

    public CatalogPage<ProductResponse> getProductsByCategory(String categoryId, String locale, int page, int size) {
        var pageable = pageable(page, size);
        category(categoryId);
        String language = normalizeLocale(locale);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            var content = productRepository.findPageAtOffset(categoryId, pageable.getOffset(), size);
            return CatalogPage.of(content.stream().map(value -> productResponse(value, language)).toList(),
                    page, size, productRepository.countByCategoryId(categoryId));
        }
        var results = productRepository.findByCategoryId(categoryId, pageable);
        log.debug("Product listing categoryId={} locale={} page={} size={} results={} total={}",
                categoryId, language, page, size, results.getNumberOfElements(), results.getTotalElements());
        return CatalogPage.of(results.getContent().stream().map(p -> productResponse(p, language)).toList(),
                page, size, results.getTotalElements());
    }

    public ProductResponse getProduct(String productId, String locale) {
        String language = normalizeLocale(locale);
        log.debug("Product lookup id={} locale={}", productId, language);
        return productResponse(product(productId), language);
    }

    public CatalogPage<ItemResponse> getItemsByProduct(String productId, String locale, int page, int size) {
        var pageable = pageable(page, size);
        product(productId);
        String language = normalizeLocale(locale);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            var content = itemRepository.findPageAtOffset(productId, pageable.getOffset(), size);
            return CatalogPage.of(content.stream().map(value -> itemResponse(value, language)).toList(),
                    page, size, itemRepository.countByProductId(productId));
        }
        var results = itemRepository.findByProductId(productId, pageable);
        log.debug("Item listing productId={} locale={} page={} size={} results={} total={}",
                productId, language, page, size, results.getNumberOfElements(), results.getTotalElements());
        return CatalogPage.of(results.getContent().stream().map(i -> itemResponse(i, language)).toList(),
                page, size, results.getTotalElements());
    }

    public ItemResponse getItem(String itemId, String locale) {
        String language = normalizeLocale(locale);
        log.debug("Item lookup id={} locale={}", itemId, language);
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        return itemResponse(item, language);
    }

    public CatalogPage<ProductResponse> searchProducts(String query, String locale, int page, int size) {
        pageable(page, size);
        String language = normalizeLocale(locale);
        List<String> tokens = query == null ? List.of() : Arrays.stream(query.toLowerCase(Locale.ROOT)
                .split("(?U)\\s+")).filter(token -> !token.isBlank()).distinct().toList();
        var result = tokens.isEmpty() ? CatalogPage.<ProductResponse>of(List.of(), page, size, 0)
                : searchRepository.search(tokens, language, page, size);
        log.debug("Catalog search locale={} tokens={} page={} size={} results={} total={}",
                language, tokens.size(), page, size, result.content().size(), result.totalElements());
        return result;
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 20");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
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
