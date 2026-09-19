package com.mdb.petstore.customer.payment;

import java.util.List;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Idempotent startup hardening; PAN never leaves MongoDB in a query result. */
@Component
@Order(0)
public class PaymentMetadataMigration implements ApplicationRunner {
    private final MongoTemplate mongo;

    public PaymentMetadataMigration(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void run(ApplicationArguments arguments) {
        // Each document is updated atomically. No read/modify/write window can overwrite a concurrent account save.
        // Invalid/blank legacy input is removed too; existing display metadata is retained where available.
        mongo.getCollection("customers").updateMany(
                new Document("account.creditCard.cardNumber", new Document("$exists", true)),
                List.of(Document.parse("""
                    {"$set": {"account.creditCard.last4": {"$let": {
                      "vars": {"digits": {"$replaceAll": {
                        "input": {"$replaceAll": {
                          "input": {"$cond": [
                            {"$eq": [{"$type": "$account.creditCard.cardNumber"}, "string"]},
                            "$account.creditCard.cardNumber", ""]},
                          "find": " ", "replacement": ""}},
                        "find": "-", "replacement": ""}}},
                      "in": {"$cond": [
                        {"$regexMatch": {"input": "$$digits", "regex": "^[0-9]{4,19}$"}},
                        {"$substrBytes": ["$$digits", {"$subtract": [{"$strLenBytes": "$$digits"}, 4]}, 4]},
                        {"$ifNull": ["$account.creditCard.last4", null]}]}
                    }}}}
                    """), new Document("$unset", "account.creditCard.cardNumber")));
    }
}
