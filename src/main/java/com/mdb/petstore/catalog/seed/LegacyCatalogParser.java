package com.mdb.petstore.catalog.seed;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.mdb.petstore.catalog.model.Category;
import com.mdb.petstore.catalog.model.CategoryDetails;
import com.mdb.petstore.catalog.model.Item;
import com.mdb.petstore.catalog.model.ItemDetails;
import com.mdb.petstore.catalog.model.Product;
import com.mdb.petstore.catalog.model.ProductDetails;

import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

final class LegacyCatalogParser {

    CatalogData parse(InputStream input) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        // Do not let the default error handler print source content to stderr.
        builder.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException exception) throws SAXParseException {
                throw exception;
            }

            public void error(SAXParseException exception) throws SAXParseException {
                throw exception;
            }

            public void fatalError(SAXParseException exception) throws SAXParseException {
                throw exception;
            }
        });
        Element root = builder.parse(input).getDocumentElement();
        require("Catalog".equals(root.getTagName()), "Expected Catalog root");
        require(children(root, null).size() == 3, "Expected only catalog sections");

        List<Category> categories = new ArrayList<>();
        for (Element element : children(section(root, "Categories"), "Category")) {
            Category category = new Category();
            category.setId(attribute(element, "id"));
            List<CategoryDetails> details = new ArrayList<>();
            for (Element localized : children(element, "CategoryDetails")) {
                CategoryDetails detail = new CategoryDetails();
                detail.setLocale(locale(localized));
                detail.setName(text(localized, "Name", true));
                detail.setImage(text(localized, "Image", false));
                detail.setDescription(text(localized, "Description", false));
                details.add(detail);
            }
            validateLocales(details.stream().map(CategoryDetails::getLocale).toList());
            category.setDetails(details);
            categories.add(category);
        }
        Map<String, Category> categoriesById = index(categories, Category::getId);

        List<Product> products = new ArrayList<>();
        for (Element element : children(section(root, "Products"), "Product")) {
            Product product = new Product();
            product.setId(attribute(element, "id"));
            product.setCategoryId(attribute(element, "category"));
            require(categoriesById.containsKey(product.getCategoryId()), "Product references missing category");
            List<ProductDetails> details = new ArrayList<>();
            for (Element localized : children(element, "ProductDetails")) {
                ProductDetails detail = new ProductDetails();
                detail.setLocale(locale(localized));
                detail.setName(text(localized, "Name", true));
                detail.setImage(text(localized, "Image", false));
                detail.setDescription(text(localized, "Description", false));
                details.add(detail);
            }
            validateLocales(details.stream().map(ProductDetails::getLocale).toList());
            product.setDetails(details);
            products.add(product);
        }
        Map<String, Product> productsById = index(products, Product::getId);

        List<Item> items = new ArrayList<>();
        for (Element element : children(section(root, "Items"), "Item")) {
            Item item = new Item();
            item.setId(attribute(element, "id"));
            item.setProductId(attribute(element, "product"));
            Product product = productsById.get(item.getProductId());
            require(product != null, "Item references missing product");
            item.setCategoryId(product.getCategoryId());
            List<ItemDetails> details = new ArrayList<>();
            for (Element localized : children(element, "ItemDetails")) {
                ItemDetails detail = new ItemDetails();
                detail.setLocale(locale(localized));
                detail.setListPrice(price(localized, "ListPrice"));
                detail.setUnitCost(price(localized, "UnitCost"));
                detail.setAttributes(children(localized, "Attribute").stream()
                        .map(Element::getTextContent).toList());
                detail.setImage(text(localized, "Image", false));
                detail.setDescription(text(localized, "Description", false));
                details.add(detail);
            }
            validateLocales(details.stream().map(ItemDetails::getLocale).toList());
            item.setDetails(details);
            require(item.getCategoryId().equals(product.getCategoryId()), "Item category differs from product");
            items.add(item);
        }
        index(items, Item::getId);
        require(categories.size() == 5, "Expected 5 categories");
        require(products.size() == 16, "Expected 16 products");
        require(items.size() == 28, "Expected 28 items");
        require(categories.stream().mapToInt(c -> c.getDetails().size()).sum() == 15,
                "Expected 15 category details");
        require(products.stream().mapToInt(p -> p.getDetails().size()).sum() == 48,
                "Expected 48 product details");
        require(items.stream().mapToInt(i -> i.getDetails().size()).sum() == 83,
                "Expected 83 item details");
        return new CatalogData(categories, products, items);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> elements = new ArrayList<>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && (name == null || name.equals(element.getTagName()))) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static Element section(Element root, String name) {
        List<Element> matches = children(root, name);
        require(matches.size() == 1, "Expected one " + name + " section");
        return matches.getFirst();
    }

    private static String attribute(Element element, String name) {
        String value = element.getAttribute(name);
        require(!value.isBlank(), "Missing catalog attribute " + name);
        return value;
    }

    private static String locale(Element element) {
        String value = element.getAttributeNS(XMLConstants.XML_NS_URI, "lang");
        require(Set.of("en-US", "ja-JP", "zh-CN").contains(value), "Invalid catalog locale");
        return value;
    }

    private static void validateLocales(List<String> locales) {
        require(!locales.isEmpty(), "Missing localized details");
        require(new HashSet<>(locales).size() == locales.size(), "Duplicate localized details");
    }

    private static String text(Element element, String name, boolean required) {
        List<Element> matches = children(element, name);
        require(matches.size() <= 1, "Duplicate catalog field " + name);
        if (matches.isEmpty()) {
            require(!required, "Missing catalog field " + name);
            return null;
        }
        String value = matches.getFirst().getTextContent();
        require(!required || !value.isBlank(), "Empty catalog field " + name);
        return value;
    }

    private static BigDecimal price(Element element, String name) {
        String value = text(element, name, true);
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid catalog price in " + name);
        }
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> id) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            require(result.putIfAbsent(id.apply(value), value) == null, "Duplicate catalog ID");
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    record CatalogData(List<Category> categories, List<Product> products, List<Item> items) {
    }
}
