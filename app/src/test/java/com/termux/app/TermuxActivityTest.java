package com.termux.app;

import com.termux.shared.termux.data.TermuxUrlUtils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;

public class TermuxActivityTest {

    private void assertUrlsAre(String text, String... urls) {
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        Collections.addAll(expected, urls);
        Assert.assertEquals(expected, TermuxUrlUtils.extractUrls(text));
    }

    @Test
    public void testExtractUrls() {
        assertUrlsAre("hello http://example.com world", "http://example.com");

        assertUrlsAre("http://example.com\nhttp://another.com", "http://example.com", "http://another.com");

        assertUrlsAre("hello http://example.com world and http://more.example.com with secure https://more.example.com",
            "http://example.com", "http://more.example.com", "https://more.example.com");

        assertUrlsAre("hello https://example.com/#bar https://example.com/foo#bar",
            "https://example.com/#bar", "https://example.com/foo#bar");
    }

    @Test
    public void testExtractUrlsInParentheses() {
        // How Claude Code shows a markdown link in a terminal without hyperlinks.
        assertUrlsAre("docs (https://example.com/a)", "https://example.com/a");
        assertUrlsAre("(https://example.com/a).", "https://example.com/a");
        assertUrlsAre("(https://en.wikipedia.org/wiki/Fork_(software))", "https://en.wikipedia.org/wiki/Fork_(software)");
    }

}
