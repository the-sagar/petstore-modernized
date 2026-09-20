package com.mdb.petstore.catalog.repository;

import java.util.List;
import java.util.regex.Pattern;

import com.mdb.petstore.catalog.dto.CatalogPage;
import com.mdb.petstore.catalog.dto.ProductResponse;
import com.mdb.petstore.catalog.model.Product;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogSearchRepository {

    private final MongoTemplate mongo;

    public CatalogSearchRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public CatalogPage<ProductResponse> search(List<String> tokens, String locale, int page, int size) {
        // Literal escaped contains matching, not a user-supplied regex. Unlike $toLower,
        // PCRE case-insensitive matching also supports non-ASCII letters.
        var matches = tokens.stream().map(token -> new Document("$regexMatch", new Document("input", "$text")
                .append("regex", new Document("$literal", Pattern.quote(token))).append("options", "i"))).toList();
        var stages = List.of(
                new Document("$lookup", new Document("from", "items").append("localField", "_id")
                        .append("foreignField", "productId").append("as", "items")),
                new Document("$set", new Document("detail", effectiveDetail("$details", locale))
                        .append("itemDetails", new Document("$map", new Document("input", "$items")
                                .append("as", "item").append("in", effectiveDetail("$$item.details", locale))))),
                new Document("$set", new Document("text", new Document("$concat", List.of(
                        text("$detail.name"), "\n", text("$detail.description"), "\n", text("$categoryId"), "\n",
                        new Document("$reduce", new Document("input", "$itemDetails").append("initialValue", "")
                                .append("in", new Document("$concat", List.of("$$value",
                                        text("$$this.description"), "\n")))))))),
                new Document("$match", new Document("$expr", new Document("$and", matches))),
                new Document("$project", new Document("categoryId", 1).append("detail", 1)),
                new Document("$facet", new Document("content", List.of(
                        new Document("$sort", new Document("_id", 1)),
                        new Document("$skip", (long) page * size), new Document("$limit", size)))
                        .append("totals", List.of(new Document("$count", "value")))));
        // Contains search scans candidate text; ordinary indexes do not accelerate it.
        // Atlas Search is a future option for relevance/fuzzy multilingual search, not required locally.
        var aggregation = Aggregation.newAggregation(stages.stream()
                .map(stage -> (AggregationOperation) context -> stage).toList());
        Document result = mongo.aggregate(aggregation, mongo.getCollectionName(Product.class), Document.class)
                .getUniqueMappedResult();
        if (result == null) return CatalogPage.of(List.of(), page, size, 0);
        var totals = result.getList("totals", Document.class);
        long total = totals.isEmpty() ? 0 : ((Number) totals.getFirst().get("value")).longValue();
        var content = result.getList("content", Document.class).stream().map(product -> {
            Document detail = product.get("detail", Document.class);
            if (detail == null) detail = new Document();
            return new ProductResponse(product.getString("_id"), product.getString("categoryId"),
                    detail.getString("name"), detail.getString("image"), detail.getString("description"));
        }).toList();
        return CatalogPage.of(content, page, size, total);
    }

    private static Document text(String field) {
        return new Document("$ifNull", List.of(field, ""));
    }

    private static Document effectiveDetail(String details, String locale) {
        // Resolve independently for the Product and for each Item, preserving array order.
        Object array = new Document("$ifNull", List.of(details, List.of()));
        return new Document("$ifNull", List.of(firstForLocale(array, locale), firstForLocale(array, "en-US"),
                new Document("$arrayElemAt", List.of(array, 0)), new Document()));
    }

    private static Document firstForLocale(Object details, String locale) {
        return new Document("$arrayElemAt", List.of(new Document("$filter", new Document("input", details)
                .append("as", "detail").append("cond", new Document("$eq", List.of("$$detail.locale",
                        new Document("$literal", locale))))), 0));
    }
}
