package com.example.sysarch.polarion.sso;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class AssertionsTest {

    private static final byte[] KEY = "shared-secret".getBytes(StandardCharsets.UTF_8);

    @Test
    void anAssertionIsVersionPayloadAndTheSignatureOfBoth() {
        String assertion = Assertions.issue("alice", KEY, 1_000);
        String[] parts = assertion.split("\\.");

        assertEquals(3, parts.length);
        assertEquals("v1", parts[0]);
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(json.matches("\\{\"sub\":\"alice\",\"iat\":1000,\"exp\":1060,\"nonce\":\"[0-9a-f]{32}\",\"aud\":\"sysarch\"\\}"), json);
        assertArrayEquals(Assertions.sign(KEY, "v1." + parts[1]), Base64.getUrlDecoder().decode(parts[2]));
        assertNotEquals(assertion, Assertions.issue("alice", KEY, 1_000), "a fresh nonce each time");
    }

    /** The key comes from a file, never from a property value (Polarion logs every property at startup). */
    @Test
    void theKeyIsTheFirstLineOfItsFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path key = dir.resolve("sysarch-sso.key");
        java.nio.file.Files.writeString(key, "  k3y-value \r\n# comment\n");

        assertEquals("k3y-value", StartServlet.readKey(key.toString()));
        assertEquals(null, StartServlet.readKey(dir.resolve("missing.key").toString()));
        assertEquals(null, StartServlet.readKey(null));
        java.nio.file.Files.writeString(key, "\n");
        assertEquals(null, StartServlet.readKey(key.toString()), "an empty file is no key");
    }

    /** The Work Item panel frames the start page, which lands on the embed page of that Work Item. */
    @Test
    void theWorkItemPanelFramesTheEmbedPageOfItsWorkItem() {
        String url = WorkItemPanel.startUrl("sysarch", "SYS-12");
        assertEquals("/polarion/sysarch-sso/start?return=%2F%3Fembed%3D1%26polarionProject%3Dsysarch%26workItem%3DSYS-12", url);
        assertEquals("/?embed=1&polarionProject=sysarch&workItem=SYS-12",
                java.net.URLDecoder.decode(url.substring(url.indexOf("return=") + 7), StandardCharsets.UTF_8));
        assertEquals("/", StartServlet.localPath("https://evil.example"), "the start page still accepts local paths only");

        String html = WorkItemPanel.html("sysarch", "SYS-12", true);
        assertTrue(html.contains("<iframe src=\"" + url + "\""), html);
        assertTrue(html.contains("target=\"_blank\""));
        String odd = WorkItemPanel.html("p\"><script>", "x", true);
        assertTrue(!odd.contains("<script>") && odd.contains("polarionProject%3Dp%2522%253E%253Cscript%253E"), "ids are encoded: " + odd);
        assertTrue(WorkItemPanel.html("sysarch", "SYS-12", false).contains("保存工作项后"), "not saved yet: no frame");
    }

    @Test
    void userIdsAreEscaped() {
        assertEquals("a\\\"b\\\\c\\u000a", Assertions.escape("a\"b\\c\n"));
    }

    @Test
    void onlyPathsOnSysArchAreForwardedAndHtmlIsEscaped() {
        assertEquals("/?embed=1&workItem=SYS-1", StartServlet.localPath("/?embed=1&workItem=SYS-1"));
        for (String bad : new String[] { null, "", "https://evil.example", "//evil.example/x", "/\\evil", "/a\nb" }) {
            assertEquals("/", StartServlet.localPath(bad), String.valueOf(bad));
        }
        assertEquals("&lt;script&gt;&quot;&#39;&amp;", StartServlet.html("<script>\"'&"));
    }
}
