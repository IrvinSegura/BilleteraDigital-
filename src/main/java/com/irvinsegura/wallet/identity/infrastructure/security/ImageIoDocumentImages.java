package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.model.InvalidIdentityDocumentException;
import com.irvinsegura.wallet.identity.domain.port.DocumentImages;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Decodes the picture and encodes its pixels again as a JPEG, so nothing but the image is kept: no EXIF data (where and
 * when it was taken), no other file hidden after it. The type comes from the file's first bytes, since a client can
 * claim anything, and the size is read from its header before decoding, so a small file that would decode into a
 * huge image is refused first. Large photos are read at a reduced size, which keeps memory in check and is still
 * more than enough to read a card.
 */
@Component
class ImageIoDocumentImages implements DocumentImages {

    static final int MAX_BYTES = 8 * 1024 * 1024;
    // Smaller than this a card can't be read
    static final int MIN_SIDE = 400;
    static final int MAX_SIDE = 12_000;
    // What is kept: the longest side at most this
    static final int STORED_SIDE = 2_000;
    private static final float QUALITY = 0.9f;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Override
    public byte[] sanitize(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES
                || !startsWith(content, JPEG) && !startsWith(content, PNG)) {
            throw invalid();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (Math.min(width, height) < MIN_SIDE || Math.max(width, height) > MAX_SIDE) {
                    throw invalid();
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = (Math.max(width, height) + STORED_SIDE - 1) / STORED_SIDE;
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return jpeg(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidIdentityDocumentException invalid) {
                throw invalid;
            }
            throw invalid();
        }
    }

    // Drawn on white, since JPEG has no transparency
    private static byte[] jpeg(BufferedImage image) throws IOException {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static InvalidIdentityDocumentException invalid() {
        return new InvalidIdentityDocumentException(InvalidIdentityDocumentException.IMAGE_INVALID);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }
}
