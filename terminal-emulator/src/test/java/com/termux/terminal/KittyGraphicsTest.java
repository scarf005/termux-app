package com.termux.terminal;

public class KittyGraphicsTest extends TerminalTestCase {

    private static final String PLACEHOLDER = new String(Character.toChars(KittyGraphics.PLACEHOLDER));
    /** The diacritics for 0, 1 and 2. */
    private static final String D0 = "̅", D1 = "̍", D2 = "̎";

    private static String apc(String command) {
        return "\033_G" + command + "\033\\";
    }

    public void testQueryAnswersOkWithoutDrawing() {
        withTerminalSized(2, 2);
        // What Claude Code and yazi send to detect support.
        assertEnteringStringGivesResponse(apc("i=31,s=1,v=1,a=q,t=d,f=24;AAAA"), "\033_Gi=31;OK\033\\");
        assertLinesAre("  ", "  ");
        assertNull(mTerminal.getKittyGraphics().getImage(31));
    }

    public void testQueryReportsUnsupportedAndInvalidData() {
        withTerminalSized(2, 2);
        assertEnteringStringGivesResponse(apc("i=1,s=2,v=1,a=q,t=d,f=24;AAAA"), "\033_Gi=1;ENODATA:insufficient image data\033\\");
        assertEnteringStringGivesResponse(apc("i=2,s=1,v=1,a=q,t=f,f=24;AAAA"), "\033_Gi=2;EINVAL:only direct transmission (t=d) is supported\033\\");
        assertEnteringStringGivesResponse(apc("i=3,a=q,f=100;not base64!"), "\033_Gi=3;EINVAL:payload is not base64\033\\");
        // No response without an image id.
        assertEnteringStringGivesResponse(apc("s=1,v=1,a=q,t=d,f=24;AAAA"), "");
    }

    public void testQuietSuppressesResponses() {
        withTerminalSized(2, 2);
        assertEnteringStringGivesResponse(apc("i=4,s=1,v=1,a=q,f=24,q=1;AAAA"), "");
        assertEnteringStringGivesResponse(apc("i=4,s=1,v=1,a=q,f=24,q=1;"), "\033_Gi=4;ENODATA:insufficient image data\033\\");
        assertEnteringStringGivesResponse(apc("i=4,s=1,v=1,a=q,f=24,q=2;"), "");
    }

    public void testChunkedTransmitCreatesVirtualPlacement() {
        withTerminalSized(4, 2);
        // The way Claude Code sends a PNG that is over 4096 base64 characters.
        enterString(apc("a=T,U=1,q=2,f=100,i=7,c=3,r=2,m=1;iVBO"));
        assertNull(mTerminal.getKittyGraphics().getImage(7));
        enterString(apc("m=1,q=2;Rw0K"));
        assertEnteringStringGivesResponse(apc("m=0,q=2;Ggo="), "");
        KittyGraphics.Image image = mTerminal.getKittyGraphics().getImage(7);
        assertNotNull(image);
        assertTrue(image.isPlaced());
        assertEquals(3, image.getColumns());
        assertEquals(2, image.getRows());
        assertLinesAre("    ", "    ");
    }

    public void testNewCommandAbandonsUnfinishedChunks() {
        withTerminalSized(4, 2);
        enterString(apc("a=T,U=1,q=2,f=100,i=12,m=1;iVBO"));
        // The program writing the image was interrupted, and another one queries support.
        assertEnteringStringGivesResponse(apc("i=31,s=1,v=1,a=q,t=d,f=24;AAAA"), "\033_Gi=31;OK\033\\");
        assertEnteringStringGivesResponse(apc("m=0;Rw0K"), "");
        assertNull(mTerminal.getKittyGraphics().getImage(12));
    }

    public void testTransmitOnlyThenPlace() {
        withTerminalSized(4, 2);
        assertEnteringStringGivesResponse(apc("a=t,f=24,s=1,v=1,i=8;AAAA"), "\033_Gi=8;OK\033\\");
        assertFalse(mTerminal.getKittyGraphics().getImage(8).isPlaced());
        assertEnteringStringGivesResponse(apc("a=p,U=1,i=8,c=2,r=1"), "\033_Gi=8;OK\033\\");
        assertTrue(mTerminal.getKittyGraphics().getImage(8).isPlaced());
        assertEnteringStringGivesResponse(apc("a=p,U=1,i=9"), "\033_Gi=9;ENOENT:no image with this id\033\\");
        // Direct placements are not supported, so clients can fall back to another protocol.
        assertEnteringStringGivesResponse(apc("a=p,i=8"), "\033_Gi=8;EINVAL:only Unicode placeholder placements (U=1) are supported\033\\");
    }

    public void testDeleteAndReset() {
        withTerminalSized(4, 2);
        enterString(apc("a=T,U=1,q=2,f=24,s=1,v=1,i=10;AAAA"));
        enterString(apc("a=T,U=1,q=2,f=24,s=1,v=1,i=11;AAAA"));
        assertEnteringStringGivesResponse(apc("a=d,d=I,i=10,q=2"), "");
        assertNull(mTerminal.getKittyGraphics().getImage(10));
        assertNotNull(mTerminal.getKittyGraphics().getImage(11));
        enterString("\033c");
        assertNull(mTerminal.getKittyGraphics().getImage(11));
    }

    public void testPlaceholderDiacriticsStayInOneCell() {
        withTerminalSized(4, 2);
        enterString("\033[38;2;0;0;7m" + PLACEHOLDER + D0 + D0 + PLACEHOLDER + D0 + D1 + D2 + "\033[mx");
        assertCursorAt(0, 3);
        long style = mTerminal.getScreen().getStyleAt(0, 0);
        assertEquals(7, KittyGraphics.imageId(style, 0));
        assertEquals(0x02000007, KittyGraphics.imageId(style, 2));
    }

    public void testEveryDiacriticIsZeroWidth() {
        int count = 0;
        for (int codePoint = 0; codePoint <= 0x1FFFF; codePoint++) {
            int index = KittyGraphics.diacriticIndex(codePoint);
            if (index < 0) continue;
            assertEquals(count, index);
            assertEquals("U+" + Integer.toHexString(codePoint), 0, WcWidth.width(codePoint));
            count++;
        }
        assertEquals(297, count);
        assertEquals(1, WcWidth.width(KittyGraphics.PLACEHOLDER));
    }

    public void testImageIdFromIndexedAndDefaultColors() {
        assertEquals(42, KittyGraphics.imageId(TextStyle.encode(42, TextStyle.COLOR_INDEX_BACKGROUND, 0), 0));
        assertEquals(0, KittyGraphics.imageId(TextStyle.NORMAL, 3));
    }

}
