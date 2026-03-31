package dev.noaht8um.portalheim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TagNormalizerTest {
    @Test
    void normalizesCaseWhitespaceAndColorCodes() {
        assertEquals("meadow gate", TagNormalizer.normalize("  \u00A7aMeadow   Gate  "));
    }

    @Test
    void emptyAndNullBecomeBlankTags() {
        assertEquals("", TagNormalizer.normalize(null));
        assertEquals("", TagNormalizer.normalize("   "));
    }
}
