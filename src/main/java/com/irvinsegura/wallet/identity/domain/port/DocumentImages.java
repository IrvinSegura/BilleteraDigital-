package com.irvinsegura.wallet.identity.domain.port;

/**
 * Reads a picture of an identity document and writes it again as a plain JPEG: what is kept is only the image, without
 * its metadata (such as where it was taken) or anything else hidden in the file.
 */
public interface DocumentImages {

    /**
     * @throws com.irvinsegura.wallet.identity.domain.model.InvalidIdentityDocumentException {@code IMAGE_INVALID} if
     *         it isn't a JPEG or PNG picture, or is too small to read or too big
     */
    byte[] sanitize(byte[] content);
}
