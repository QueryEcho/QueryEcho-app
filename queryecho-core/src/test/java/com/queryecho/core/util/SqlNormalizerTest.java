package com.queryecho.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqlNormalizerTest {
    @Test
    void removesLiteralDifferencesAndCollapsesInLists() {
        assertEquals("select * from customer where id = ? and name = ? and code IN (?)",
                SqlNormalizer.normalize("select  * from customer where id = 42 and name = 'Lee' and code in (?, ?, ?)"));
    }
}
