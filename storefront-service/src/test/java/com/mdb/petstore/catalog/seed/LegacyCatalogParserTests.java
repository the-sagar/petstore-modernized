package com.mdb.petstore.catalog.seed;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXParseException;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCatalogParserTests {

    @Test
    void resourceContainsOnlyCatalogElements() throws Exception {
        Set<String> allowed = Set.of("Catalog", "Categories", "Category", "CategoryDetails", "Products",
                "Product", "ProductDetails", "Items", "Item", "ItemDetails", "Name", "Image", "Description",
                "ListPrice", "UnitCost", "Attribute");
        try (var input = new ClassPathResource("legacy/catalog.xml").getInputStream()) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(input);
            assertEquals("Catalog", document.getDocumentElement().getTagName());
            var elements = document.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                assertTrue(allowed.contains(elements.item(i).getNodeName()));
            }
        }
    }

    @Test
    void rejectsUnexpectedCounts() throws Exception {
        // Remove references too so validation reaches the explicit category count check.
        String source = source().replaceFirst("(?s)<Category id=\"FISH\">.*?</Category>", "")
                .replace("category=\"FISH\"", "category=\"DOGS\"");
        assertEquals("Expected 5 categories", assertThrows(IllegalArgumentException.class,
                () -> parse(source)).getMessage());
    }

    @Test
    void rejectsBrokenCategoryAndProductReferences() throws Exception {
        String missingCategory = source().replaceFirst("category=\"FISH\"", "category=\"MISSING\"");
        String missingProduct = source().replaceFirst("product=\"FI-SW-01\"", "product=\"MISSING\"");
        assertEquals("Product references missing category", assertThrows(IllegalArgumentException.class,
                () -> parse(missingCategory)).getMessage());
        assertEquals("Item references missing product", assertThrows(IllegalArgumentException.class,
                () -> parse(missingProduct)).getMessage());
    }

    @Test
    void rejectsDuplicateIdsAndMalformedPrices() throws Exception {
        String duplicate = source().replace("id=\"EST-2\"", "id=\"EST-1\"");
        String invalidPrice = source().replaceFirst("<ListPrice>16.50</ListPrice>", "<ListPrice>invalid</ListPrice>");
        assertEquals("Duplicate catalog ID", assertThrows(IllegalArgumentException.class,
                () -> parse(duplicate)).getMessage());
        assertEquals("Invalid catalog price in ListPrice", assertThrows(IllegalArgumentException.class,
                () -> parse(invalidPrice)).getMessage());
    }

    @Test
    void rejectsDoctypeDeclarations() {
        assertThrows(SAXParseException.class, () -> parse("<!DOCTYPE Catalog [<!ENTITY value 'test'>]><Catalog/>"));
    }

    private static String source() throws Exception {
        return new ClassPathResource("legacy/catalog.xml").getContentAsString(StandardCharsets.UTF_8);
    }

    private static LegacyCatalogParser.CatalogData parse(String xml) throws Exception {
        return new LegacyCatalogParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
