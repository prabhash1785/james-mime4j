/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mime4j.codec;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.apache.james.mime4j.util.CharsetUtil;

/**
 * Static methods for encoding encoded-words as defined in <a
 * href='http://www.faqs.org/rfcs/rfc2047.html'>RFC 2047</a>.
 */
public class EncoderUtil {
    private static final byte[] BASE64_TABLE = Base64OutputStream.BASE64_TABLE;
    private static final char BASE64_PAD = '=';

    private static final boolean[] ENCODE_Q_REGULAR = initQTable("=_?");
    private static final boolean[] ENCODE_Q_RESTRICTED = initQTable("=_?\"#$%&'(),.:;<>@[\\]^`{|}~");

    private static boolean[] initQTable(String needToEncode) {
        boolean[] table = new boolean[128];
        for (int i = 0; i < 128; i++) {
            table[i] = i < 32 || i >= 127 || needToEncode.indexOf(i) != -1;
        }
        return table;
    }

    private static final int MAX_USED_CHARACTERS = 50;

    private static final String ENC_WORD_PREFIX = "=?";
    private static final String ENC_WORD_SUFFIX = "?=";

    private static final int ENCODED_WORD_MAX_LENGTH = 75; // RFC 2047

    /**
     * Selects one of the two encodings specified in RFC 2047.
     */
    public enum Encoding {
        /** The B encoding (identical to base64 defined in RFC 2045). */
        B,
        /** The Q encoding (similar to quoted-printable defined in RFC 2045). */
        Q
    };

    /**
     * Indicates the intended usage of an encoded word.
     */
    public enum Usage {
        /**
         * Encoded word is used to replace a 'text' token in any Subject or
         * Comments header field.
         */
        TEXT_TOKEN,
        /**
         * Encoded word is used to replace a 'word' entity within a 'phrase',
         * for example, one that precedes an address in a From, To, or Cc
         * header.
         */
        WORD_ENTITY
    }

    private EncoderUtil() {
    }

    /**
     * Shortcut method that encodes the specified text into an encoded-word if
     * the text has to be encoded.
     * 
     * @param text
     *            text to encode.
     * @param usage
     *            whether the encoded-word is to be used to replace a text token
     *            or a word entity (see RFC 822).
     * @param usedCharacters
     *            number of characters already used up (<code>0 <= usedCharacters <= 50</code>).
     * @return the specified text if encoding is not necessary or an encoded
     *         word or a sequence of encoded words otherwise.
     */
    public static String encodeIfNecessary(String text, Usage usage,
            int usedCharacters) {
        if (hasToBeEncoded(text, usedCharacters))
            return encodeEncodedWord(text, usage, usedCharacters);
        else
            return text;
    }

    /**
     * Determines if the specified string has to encoded into an encoded-word.
     * Returns <code>true</code> if the text contains characters that don't
     * fall into the printable ASCII character set or if the text contains a
     * 'word' (sequence of non-whitespace characters) longer than 78 characters
     * (including characters already used up in the line).
     * 
     * @param text
     *            text to analyze.
     * @param usedCharacters
     *            number of characters already used up (<code>0 <= usedCharacters <= 50</code>).
     * @return <code>true</code> if the specified text has to be encoded into
     *         an encoded-word, <code>false</code> otherwise.
     */
    public static boolean hasToBeEncoded(String text, int usedCharacters) {
        if (text == null)
            throw new IllegalArgumentException();
        if (usedCharacters < 0 || usedCharacters > MAX_USED_CHARACTERS)
            throw new IllegalArgumentException();

        int nonWhiteSpaceCount = usedCharacters;

        for (int idx = 0; idx < text.length(); idx++) {
            char ch = text.charAt(idx);
            if (ch == '\t' || ch == ' ') {
                nonWhiteSpaceCount = 0;
            } else {
                nonWhiteSpaceCount++;
                if (nonWhiteSpaceCount > 78) {
                    // line cannot be folded into multiple lines with no more
                    // than 78 characters each. encoding as encoded-words makes
                    // that possible.
                    return true;
                }

                if (ch < 32 || ch >= 127) {
                    // non-printable ascii character has to be encoded
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Encodes the specified text into an encoded word or a sequence of encoded
     * words separated by space. The text is separated into a sequence of
     * encoded words if it does not fit in a single one.
     * <p>
     * The charset to encode the specified text into a byte array and the
     * encoding to use for the encoded-word are detected automatically.
     * <p>
     * This method assumes that zero characters have already been used up in the
     * current line.
     * 
     * @param text
     *            text to encode.
     * @param usage
     *            whether the encoded-word is to be used to replace a text token
     *            or a word entity (see RFC 822).
     * @return the encoded word (or sequence of encoded words if the given text
     *         does not fit in a single encoded word).
     * @see #hasToBeEncoded(String, int)
     */
    public static String encodeEncodedWord(String text, Usage usage) {
        return encodeEncodedWord(text, usage, 0, null, null);
    }

    /**
     * Encodes the specified text into an encoded word or a sequence of encoded
     * words separated by space. The text is separated into a sequence of
     * encoded words if it does not fit in a single one.
     * <p>
     * The charset to encode the specified text into a byte array and the
     * encoding to use for the encoded-word are detected automatically.
     * 
     * @param text
     *            text to encode.
     * @param usage
     *            whether the encoded-word is to be used to replace a text token
     *            or a word entity (see RFC 822).
     * @param usedCharacters
     *            number of characters already used up (<code>0 <= usedCharacters <= 50</code>).
     * @return the encoded word (or sequence of encoded words if the given text
     *         does not fit in a single encoded word).
     * @see #hasToBeEncoded(String, int)
     */
    public static String encodeEncodedWord(String text, Usage usage,
            int usedCharacters) {
        return encodeEncodedWord(text, usage, usedCharacters, null, null);
    }

    /**
     * Encodes the specified text into an encoded word or a sequence of encoded
     * words separated by space. The text is separated into a sequence of
     * encoded words if it does not fit in a single one.
     * 
     * @param text
     *            text to encode.
     * @param usage
     *            whether the encoded-word is to be used to replace a text token
     *            or a word entity (see RFC 822).
     * @param usedCharacters
     *            number of characters already used up (<code>0 <= usedCharacters <= 50</code>).
     * @param charset
     *            the Java charset that should be used to encode the specified
     *            string into a byte array. A suitable charset is detected
     *            automatically if this parameter is <code>null</code>.
     * @param encoding
     *            the encoding to use for the encoded-word (either B or Q). A
     *            suitable encoding is automatically chosen if this parameter is
     *            <code>null</code>.
     * @return the encoded word (or sequence of encoded words if the given text
     *         does not fit in a single encoded word).
     * @see #hasToBeEncoded(String, int)
     */
    public static String encodeEncodedWord(String text, Usage usage,
            int usedCharacters, Charset charset, Encoding encoding) {
        if (text == null)
            throw new IllegalArgumentException();
        if (usedCharacters < 0 || usedCharacters > MAX_USED_CHARACTERS)
            throw new IllegalArgumentException();

        if (charset == null)
            charset = determineCharset(text);

        String mimeCharset = CharsetUtil.toMimeCharset(charset.name());
        if (mimeCharset == null) {
            // cannot happen if charset was originally null
            throw new IllegalArgumentException("Unsupported charset");
        }

        byte[] bytes = encode(text, charset);

        if (encoding == null)
            encoding = determineEncoding(bytes, usage);

        if (encoding == Encoding.B) {
            String prefix = ENC_WORD_PREFIX + mimeCharset + "?B?";
            return encodeB(prefix, text, usedCharacters, charset, bytes);
        } else {
            String prefix = ENC_WORD_PREFIX + mimeCharset + "?Q?";
            return encodeQ(prefix, text, usage, usedCharacters, charset, bytes);
        }
    }

    /**
     * Encodes the specified byte array using the B encoding defined in RFC
     * 2047.
     * 
     * @param bytes
     *            byte array to encode.
     * @return encoded string.
     */
    public static String encodeB(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        int idx = 0;
        final int end = bytes.length;
        for (; idx < end - 2; idx += 3) {
            int data = (bytes[idx] & 0xff) << 16 | (bytes[idx + 1] & 0xff) << 8
                    | bytes[idx + 2] & 0xff;
            sb.append((char) BASE64_TABLE[data >> 18 & 0x3f]);
            sb.append((char) BASE64_TABLE[data >> 12 & 0x3f]);
            sb.append((char) BASE64_TABLE[data >> 6 & 0x3f]);
            sb.append((char) BASE64_TABLE[data & 0x3f]);
        }

        if (idx == end - 2) {
            int data = (bytes[idx] & 0xff) << 16 | (bytes[idx + 1] & 0xff) << 8;
            sb.append((char) BASE64_TABLE[data >> 18 & 0x3f]);
            sb.append((char) BASE64_TABLE[data >> 12 & 0x3f]);
            sb.append((char) BASE64_TABLE[data >> 6 & 0x3f]);
            sb.append(BASE64_PAD);

        } else if (idx == end - 1) {
            int data = (bytes[idx] & 0xff) << 16;
            sb.append((char) BASE64_TABLE[data >> 18 & 0x3f]);
            sb.append((char) BASE64_TABLE[data >> 12 & 0x3f]);
            sb.append(BASE64_PAD);
            sb.append(BASE64_PAD);
        }

        return sb.toString();
    }

    /**
     * Encodes the specified byte array using the Q encoding defined in RFC
     * 2047.
     * 
     * @param bytes
     *            byte array to encode.
     * @param usage
     *            whether the encoded-word is to be used to replace a text token
     *            or a word entity (see RFC 822).
     * @return encoded string.
     */
    public static String encodeQ(byte[] bytes, Usage usage) {
        boolean[] encode = usage == Usage.TEXT_TOKEN ? ENCODE_Q_REGULAR
                : ENCODE_Q_RESTRICTED;

        StringBuilder sb = new StringBuilder();

        final int end = bytes.length;
        for (int idx = 0; idx < end; idx++) {
            int v = bytes[idx] & 0xff;
            if (v == 32) {
                sb.append('_');
            } else if (v >= 128 || encode[v]) {
                sb.append('=');
                sb.append(hexDigit(v >>> 4));
                sb.append(hexDigit(v & 0xf));
            } else {
                sb.append((char) v);
            }
        }

        return sb.toString();
    }

    private static String encodeB(String prefix, String text,
            int usedCharacters, Charset charset, byte[] bytes) {
        int encodedLength = bEncodedLength(bytes);

        int totalLength = prefix.length() + encodedLength
                + ENC_WORD_SUFFIX.length();
        if (totalLength <= ENCODED_WORD_MAX_LENGTH - usedCharacters) {
            return prefix + encodeB(bytes) + ENC_WORD_SUFFIX;
        } else {
            String part1 = text.substring(0, text.length() / 2);
            byte[] bytes1 = encode(part1, charset);
            String word1 = encodeB(prefix, part1, usedCharacters, charset,
                    bytes1);

            String part2 = text.substring(text.length() / 2);
            byte[] bytes2 = encode(part2, charset);
            String word2 = encodeB(prefix, part2, 0, charset, bytes2);

            return word1 + " " + word2;
        }
    }

    private static int bEncodedLength(byte[] bytes) {
        return (bytes.length + 2) / 3 * 4;
    }

    private static String encodeQ(String prefix, String text, Usage usage,
            int usedCharacters, Charset charset, byte[] bytes) {
        int encodedLength = qEncodedLength(bytes, usage);

        int totalLength = prefix.length() + encodedLength
                + ENC_WORD_SUFFIX.length();
        if (totalLength <= ENCODED_WORD_MAX_LENGTH - usedCharacters) {
            return prefix + encodeQ(bytes, usage) + ENC_WORD_SUFFIX;
        } else {
            String part1 = text.substring(0, text.length() / 2);
            byte[] bytes1 = encode(part1, charset);
            String word1 = encodeQ(prefix, part1, usage, usedCharacters,
                    charset, bytes1);

            String part2 = text.substring(text.length() / 2);
            byte[] bytes2 = encode(part2, charset);
            String word2 = encodeQ(prefix, part2, usage, 0, charset, bytes2);

            return word1 + " " + word2;
        }
    }

    private static int qEncodedLength(byte[] bytes, Usage usage) {
        boolean[] encode = usage == Usage.TEXT_TOKEN ? ENCODE_Q_REGULAR
                : ENCODE_Q_RESTRICTED;

        int count = 0;

        for (int idx = 0; idx < bytes.length; idx++) {
            int v = bytes[idx] & 0xff;
            if (v == 32) {
                count++;
            } else if (v >= 128 || encode[v]) {
                count += 3;
            } else {
                count++;
            }
        }

        return count;
    }

    private static byte[] encode(String text, Charset charset) {
        ByteBuffer buffer = charset.encode(text);
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return bytes;
    }

    private static Charset determineCharset(String text) {
        // it is an important property of iso-8859-1 that it directly maps
        // unicode code points 0000 to 00ff to byte values 00 to ff.
        boolean ascii = true;
        final int len = text.length();
        for (int index = 0; index < len; index++) {
            char ch = text.charAt(index);
            if (ch > 0xff) {
                return CharsetUtil.UTF_8;
            }
            if (ch > 0x7f) {
                ascii = false;
            }
        }
        return ascii ? CharsetUtil.US_ASCII : CharsetUtil.ISO_8859_1;
    }

    private static Encoding determineEncoding(byte[] bytes, Usage usage) {
        if (bytes.length == 0)
            return Encoding.Q;

        boolean[] encode = usage == Usage.TEXT_TOKEN ? ENCODE_Q_REGULAR
                : ENCODE_Q_RESTRICTED;

        int qEncoded = 0;
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            if (v >= 128 || encode[v]) {
                qEncoded++;
            }
        }

        int percentage = qEncoded * 100 / bytes.length;
        return percentage > 30 ? Encoding.B : Encoding.Q;
    }

    private static char hexDigit(int i) {
        return i < 10 ? (char) (i + '0') : (char) (i - 10 + 'A');
    }
}
