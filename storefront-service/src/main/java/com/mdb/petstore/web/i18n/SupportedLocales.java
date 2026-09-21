package com.mdb.petstore.web.i18n;

import java.util.Locale;

public final class SupportedLocales {
    private SupportedLocales() { }

    public static Locale resolve(String value) {
        String tag = value == null ? "" : value.strip().replace('_', '-');
        return switch (tag.toLowerCase(Locale.ROOT)) {
            case "ja-jp" -> Locale.JAPAN;
            case "zh-cn" -> Locale.SIMPLIFIED_CHINESE;
            default -> Locale.US;
        };
    }
}
