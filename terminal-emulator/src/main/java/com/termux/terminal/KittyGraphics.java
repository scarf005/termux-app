package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The kitty graphics protocol `APC G ... ST` images, displayed through Unicode placeholders.
 *
 * Images are transmitted directly (`t=d`) as PNG (`f=100`), RGB (`f=24`) or RGBA (`f=32`) data,
 * optionally in chunks (`m=1`). Only virtual placements (`U=1`) are supported: the application
 * writes {@link #PLACEHOLDER} cells whose foreground color holds the image id and whose
 * combining diacritics hold the row and column of the image cell, so the image moves with the
 * text like any other character.
 *
 * - https://sw.kovidgoyal.net/kitty/graphics-protocol/#unicode-placeholders
 */
public final class KittyGraphics {

    /** The placeholder character drawn as one cell of an image. */
    public static final int PLACEHOLDER = 0x10EEEE;

    /** The max base64 data length of one image, which is 12 MiB decoded. */
    static final int MAX_DATA_LENGTH = 16 * 1024 * 1024;
    /** The max total memory cost of stored images before the oldest ones are evicted. */
    static final long MAX_STORED_BYTES = 128L * 1024 * 1024;

    /**
     * The diacritics whose index in this table encodes a row, column or the most significant byte
     * of an image id, sorted ascending.
     *
     * - https://sw.kovidgoyal.net/kitty/_downloads/f0a0de9ec8d9ff4456206db8e0814937/rowcolumn-diacritics.txt
     */
    private static final int[] DIACRITICS = {
        0x0305, 0x030D, 0x030E, 0x0310, 0x0312, 0x033D, 0x033E, 0x033F, 0x0346, 0x034A,
        0x034B, 0x034C, 0x0350, 0x0351, 0x0352, 0x0357, 0x035B, 0x0363, 0x0364, 0x0365,
        0x0366, 0x0367, 0x0368, 0x0369, 0x036A, 0x036B, 0x036C, 0x036D, 0x036E, 0x036F,
        0x0483, 0x0484, 0x0485, 0x0486, 0x0487, 0x0592, 0x0593, 0x0594, 0x0595, 0x0597,
        0x0598, 0x0599, 0x059C, 0x059D, 0x059E, 0x059F, 0x05A0, 0x05A1, 0x05A8, 0x05A9,
        0x05AB, 0x05AC, 0x05AF, 0x05C4, 0x0610, 0x0611, 0x0612, 0x0613, 0x0614, 0x0615,
        0x0616, 0x0617, 0x0657, 0x0658, 0x0659, 0x065A, 0x065B, 0x065D, 0x065E, 0x06D6,
        0x06D7, 0x06D8, 0x06D9, 0x06DA, 0x06DB, 0x06DC, 0x06DF, 0x06E0, 0x06E1, 0x06E2,
        0x06E4, 0x06E7, 0x06E8, 0x06EB, 0x06EC, 0x0730, 0x0732, 0x0733, 0x0735, 0x0736,
        0x073A, 0x073D, 0x073F, 0x0740, 0x0741, 0x0743, 0x0745, 0x0747, 0x0749, 0x074A,
        0x07EB, 0x07EC, 0x07ED, 0x07EE, 0x07EF, 0x07F0, 0x07F1, 0x07F3, 0x0816, 0x0817,
        0x0818, 0x0819, 0x081B, 0x081C, 0x081D, 0x081E, 0x081F, 0x0820, 0x0821, 0x0822,
        0x0823, 0x0825, 0x0826, 0x0827, 0x0829, 0x082A, 0x082B, 0x082C, 0x082D, 0x0951,
        0x0953, 0x0954, 0x0F82, 0x0F83, 0x0F86, 0x0F87, 0x135D, 0x135E, 0x135F, 0x17DD,
        0x193A, 0x1A17, 0x1A75, 0x1A76, 0x1A77, 0x1A78, 0x1A79, 0x1A7A, 0x1A7B, 0x1A7C,
        0x1B6B, 0x1B6D, 0x1B6E, 0x1B6F, 0x1B70, 0x1B71, 0x1B72, 0x1B73, 0x1CD0, 0x1CD1,
        0x1CD2, 0x1CDA, 0x1CDB, 0x1CE0, 0x1DC0, 0x1DC1, 0x1DC3, 0x1DC4, 0x1DC5, 0x1DC6,
        0x1DC7, 0x1DC8, 0x1DC9, 0x1DCB, 0x1DCC, 0x1DD1, 0x1DD2, 0x1DD3, 0x1DD4, 0x1DD5,
        0x1DD6, 0x1DD7, 0x1DD8, 0x1DD9, 0x1DDA, 0x1DDB, 0x1DDC, 0x1DDD, 0x1DDE, 0x1DDF,
        0x1DE0, 0x1DE1, 0x1DE2, 0x1DE3, 0x1DE4, 0x1DE5, 0x1DE6, 0x1DFE, 0x20D0, 0x20D1,
        0x20D4, 0x20D5, 0x20D6, 0x20D7, 0x20DB, 0x20DC, 0x20E1, 0x20E7, 0x20E9, 0x20F0,
        0x2CEF, 0x2CF0, 0x2CF1, 0x2DE0, 0x2DE1, 0x2DE2, 0x2DE3, 0x2DE4, 0x2DE5, 0x2DE6,
        0x2DE7, 0x2DE8, 0x2DE9, 0x2DEA, 0x2DEB, 0x2DEC, 0x2DED, 0x2DEE, 0x2DEF, 0x2DF0,
        0x2DF1, 0x2DF2, 0x2DF3, 0x2DF4, 0x2DF5, 0x2DF6, 0x2DF7, 0x2DF8, 0x2DF9, 0x2DFA,
        0x2DFB, 0x2DFC, 0x2DFD, 0x2DFE, 0x2DFF, 0xA66F, 0xA67C, 0xA67D, 0xA6F0, 0xA6F1,
        0xA8E0, 0xA8E1, 0xA8E2, 0xA8E3, 0xA8E4, 0xA8E5, 0xA8E6, 0xA8E7, 0xA8E8, 0xA8E9,
        0xA8EA, 0xA8EB, 0xA8EC, 0xA8ED, 0xA8EE, 0xA8EF, 0xA8F0, 0xA8F1, 0xAAB0, 0xAAB2,
        0xAAB3, 0xAAB7, 0xAAB8, 0xAABE, 0xAABF, 0xAAC1, 0xFE20, 0xFE21, 0xFE22, 0xFE23,
        0xFE24, 0xFE25, 0xFE26, 0x10A0F, 0x10A38, 0x1D185, 0x1D186, 0x1D187, 0x1D188, 0x1D189,
        0x1D1AA, 0x1D1AB, 0x1D1AC, 0x1D1AD, 0x1D242, 0x1D243, 0x1D244,
    };

    /** The row, column or id byte encoded by a placeholder diacritic, or `-1` for other code points. */
    public static int diacriticIndex(int codePoint) {
        int index = Arrays.binarySearch(DIACRITICS, codePoint);
        return index >= 0 ? index : -1;
    }

    /**
     * The image id held by the foreground color of a placeholder cell: the 24-bit color or the
     * 256-color index, with `idMsb` from the third diacritic as the most significant byte.
     */
    public static int imageId(long style, int idMsb) {
        int color = TextStyle.decodeForeColor(style);
        int id = (color & 0xff000000) == 0xff000000 ? color & 0xffffff : color < 256 ? color : 0;
        return id == 0 ? 0 : id | (idMsb << 24);
    }


    public static final class Image {
        static final int FORMAT_RGB = 24, FORMAT_RGBA = 32, FORMAT_PNG = 100;

        final int format, width, height;
        /** The size of the virtual placement in cells, `0` if it is to be derived from the image size. */
        int columns, rows;
        /** Whether a virtual placement exists, without which placeholders draw nothing. */
        boolean placed;

        private String data;
        private Bitmap bitmap;

        Image(int format, int width, int height, String data) {
            this.format = format;
            this.width = width;
            this.height = height;
            this.data = data;
        }

        public int getColumns() {
            return columns;
        }

        public int getRows() {
            return rows;
        }

        public boolean isPlaced() {
            return placed;
        }

        /** Decode the image once on first use, `null` if it is not decodable. */
        public Bitmap getBitmap() {
            if (bitmap != null || data == null) return bitmap;
            String encoded = data;
            data = null;
            try {
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                if (format == FORMAT_PNG) {
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } else {
                    int step = format == FORMAT_RGB ? 3 : 4;
                    int[] colors = new int[width * height];
                    for (int i = 0, j = 0; i < colors.length; i++, j += step) {
                        int alpha = step == 4 ? bytes[j + 3] & 0xff : 0xff;
                        colors[i] = alpha << 24 | (bytes[j] & 0xff) << 16 | (bytes[j + 1] & 0xff) << 8 | bytes[j + 2] & 0xff;
                    }
                    bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
                }
            } catch (Throwable t) {
                if (t instanceof OutOfMemoryError) System.gc();
            }
            return bitmap;
        }

        long cost() {
            return bitmap != null ? bitmap.getByteCount() : data != null ? data.length() : 0;
        }
    }


    /** The images by id, oldest first. */
    private final LinkedHashMap<Integer, Image> mImages = new LinkedHashMap<>();

    /** The control data of the first chunk of a chunked transmission. */
    private Map<Character, String> mChunkedControl;
    /** The base64 data of the chunks received so far. */
    private StringBuilder mChunkedData;

    public Image getImage(int id) {
        return mImages.get(id);
    }

    public void clear() {
        mImages.clear();
        mChunkedControl = null;
        mChunkedData = null;
    }

    /**
     * Handle the `G...` content of an APC command.
     *
     * @return The response to send to the application, or `null` if none.
     */
    public String handle(String command) {
        int separator = command.indexOf(';');
        Map<Character, String> control = parseControl(command.substring(1, separator < 0 ? command.length() : separator));
        String payload = separator < 0 ? "" : command.substring(separator + 1);
        boolean more = "1".equals(control.get('m'));

        // Later chunks only carry `m` and `q`, any other command abandons an unfinished transmission.
        for (char key : control.keySet()) {
            if (key != 'm' && key != 'q') {
                mChunkedControl = null;
                mChunkedData = null;
            }
        }

        if (mChunkedControl != null) {
            if (mChunkedData.length() + payload.length() > MAX_DATA_LENGTH) {
                Map<Character, String> first = mChunkedControl;
                mChunkedControl = null;
                mChunkedData = null;
                return respond(first, "EFBIG:image data is too large");
            }
            mChunkedData.append(payload);
            if (more) return null;
            control.putAll(mChunkedControl);
            payload = mChunkedData.toString();
            mChunkedControl = null;
            mChunkedData = null;
        } else if (more) {
            if (payload.length() > MAX_DATA_LENGTH) return respond(control, "EFBIG:image data is too large");
            mChunkedControl = control;
            mChunkedData = new StringBuilder(payload);
            return null;
        }

        try {
            return respond(control, run(control, payload));
        } catch (NumberFormatException e) {
            return respond(control, "EINVAL:malformed control data");
        }
    }

    /** Run a complete command, returning `OK` or an error. */
    private String run(Map<Character, String> control, String payload) {
        char action = charValue(control, 'a', 't');
        int id = intValue(control, 'i', 0);
        switch (action) {
            case 't':
            case 'T':
            case 'q': {
                if (charValue(control, 't', 'd') != 'd') return "EINVAL:only direct transmission (t=d) is supported";
                if (control.containsKey('o')) return "EINVAL:compressed data is not supported";
                int format = intValue(control, 'f', Image.FORMAT_RGBA);
                int width = intValue(control, 's', 0);
                int height = intValue(control, 'v', 0);
                if (payload.length() > MAX_DATA_LENGTH) return "EFBIG:image data is too large";
                if (!payload.matches("[A-Za-z0-9+/]*={0,2}")) return "EINVAL:payload is not base64";
                if (format == Image.FORMAT_RGB || format == Image.FORMAT_RGBA) {
                    if (width <= 0 || height <= 0) return "EINVAL:width and height are required";
                    int padding = payload.endsWith("==") ? 2 : payload.endsWith("=") ? 1 : 0;
                    long size = (long) payload.length() / 4 * 3 - padding;
                    if (size < (long) width * height * (format / 8)) return "ENODATA:insufficient image data";
                } else if (format != Image.FORMAT_PNG) {
                    return "EINVAL:unknown format";
                } else if (payload.isEmpty()) {
                    return "ENODATA:no image data";
                }
                if (action == 'q' || id == 0) return "OK";

                mImages.remove(id);
                Image image = new Image(format, width, height, payload);
                mImages.put(id, image);
                evict();
                return action == 'T' ? place(image, control) : "OK";
            }
            case 'p': {
                Image image = mImages.get(id);
                return image == null ? "ENOENT:no image with this id" : place(image, control);
            }
            case 'd': {
                char what = charValue(control, 'd', 'a');
                if (what == 'a' || what == 'A') mImages.clear();
                else if (what == 'i' || what == 'I') mImages.remove(id);
                return null;
            }
            default:
                return "EINVAL:unsupported action";
        }
    }

    private static String place(Image image, Map<Character, String> control) {
        if (intValue(control, 'U', 0) != 1) return "EINVAL:only Unicode placeholder placements (U=1) are supported";
        image.columns = Math.max(0, intValue(control, 'c', 0));
        image.rows = Math.max(0, intValue(control, 'r', 0));
        image.placed = true;
        return "OK";
    }

    /** Drop the oldest images until the stored ones fit in {@link #MAX_STORED_BYTES}, keeping the newest. */
    private void evict() {
        long total = 0;
        for (Image image : mImages.values()) total += image.cost();
        Iterator<Image> iterator = mImages.values().iterator();
        while (total > MAX_STORED_BYTES && mImages.size() > 1) {
            total -= iterator.next().cost();
            iterator.remove();
        }
    }

    /** The response for a result, which is only sent for commands with an image id and not quieted by `q`. */
    private static String respond(Map<Character, String> control, String result) {
        if (result == null || !control.containsKey('i')) return null;
        String quiet = control.get('q');
        if ("2".equals(quiet) || ("1".equals(quiet) && result.equals("OK"))) return null;
        return String.format(Locale.US, "\033_Gi=%s;%s\033\\", control.get('i'), result);
    }

    private static Map<Character, String> parseControl(String control) {
        Map<Character, String> map = new LinkedHashMap<>();
        for (String pair : control.split(",")) {
            if (pair.length() >= 2 && pair.charAt(1) == '=') map.put(pair.charAt(0), pair.substring(2));
        }
        return map;
    }

    private static char charValue(Map<Character, String> control, char key, char defaultValue) {
        String value = control.get(key);
        return value == null || value.isEmpty() ? defaultValue : value.charAt(0);
    }

    private static int intValue(Map<Character, String> control, char key, int defaultValue) {
        String value = control.get(key);
        return value == null ? defaultValue : (int) Long.parseLong(value);
    }

}
