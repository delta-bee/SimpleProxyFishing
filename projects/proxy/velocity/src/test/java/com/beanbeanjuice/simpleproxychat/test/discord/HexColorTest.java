package com.beanbeanjuice.simpleproxychat.test.discord;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the hex color extraction used in ChatHandler.sendFromDiscordInternal.
 *
 * Java's Color.getRGB() always returns a negative int because alpha=0xFF is set.
 * Integer.toHexString() of a negative int gives an 8-char string (e.g. "ff808080").
 * The old code did .substring(2) which strips the alpha byte -- correct for most
 * colors but fragile. The fix takes the last 6 chars unconditionally.
 *
 * If this is wrong, every Discord message with a colored role produces a malformed
 * MiniMessage tag like <#ff808080>text</#ff808080> which throws, escapes into JDA,
 * and causes it to silently unregister the DiscordChatHandler listener.
 */
public class HexColorTest {

    /** Replicates the fixed logic from ChatHandler. */
    private static String colorToHex(Color color) {
        String raw = Integer.toHexString(color.getRGB());
        return "#" + (raw.length() > 6 ? raw.substring(raw.length() - 6) : raw);
    }

    @Test
    @DisplayName("Standard colors produce valid 6-char hex strings")
    public void testStandardColors() {
        assertValidHex(colorToHex(Color.RED));
        assertValidHex(colorToHex(Color.GREEN));
        assertValidHex(colorToHex(Color.BLUE));
        assertValidHex(colorToHex(Color.WHITE));
        assertValidHex(colorToHex(Color.BLACK));
        assertValidHex(colorToHex(Color.GRAY));
    }

    @Test
    @DisplayName("Hex is always exactly 7 chars (#rrggbb) across a range of colors")
    public void testHexLength() {
        for (int i = 0; i < 256; i += 17) {
            Color c = new Color(i, (i + 85) % 256, (i + 170) % 256);
            String hex = colorToHex(c);
            assertEquals(7, hex.length(),
                    "Hex for " + c + " should be 7 chars but was: " + hex);
        }
    }

    @Test
    @DisplayName("Gray (#808080) produces the correct hex value")
    public void testGray() {
        assertEquals("#808080", colorToHex(Color.GRAY));
    }

    @Test
    @DisplayName("toHexString of opaque Java Color is 8 chars (documents the root cause)")
    public void testRawToHexStringLength() {
        // getRGB() for any opaque Color is always negative (alpha=0xFF set).
        // toHexString() of a negative int always gives 8 chars.
        String rawHex = Integer.toHexString(Color.GRAY.getRGB()); // "ff808080"
        assertEquals(8, rawHex.length(),
                "toHexString of an opaque Color must be 8 chars - " +
                "if this fails the root-cause assumption is wrong");
    }

    @Test
    @DisplayName("Resulting hex forms a valid MiniMessage color tag")
    public void testMiniMessageTagFormat() {
        String hex = colorToHex(new Color(255, 100, 50));
        assertTrue(hex.startsWith("#"), "Should start with #");
        assertEquals(7, hex.length(), "Should be #rrggbb (7 chars)");
        // Valid MiniMessage tag: <#rrggbb>text</#rrggbb>
        String tag = String.format("<%s>test</%s>", hex, hex);
        assertFalse(tag.contains("null"), "Tag should not contain null");
    }

    private static void assertValidHex(String hex) {
        assertNotNull(hex);
        assertTrue(hex.startsWith("#"), "Should start with #: " + hex);
        assertEquals(7, hex.length(), "Should be 7 chars (#rrggbb): " + hex);
        assertTrue(hex.substring(1).matches("[0-9a-f]{6}"),
                "Should be valid lowercase hex: " + hex);
    }
}

