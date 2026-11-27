package com.yourname.hb3d.asset;

public record ImageData(
        String uri,
        String mimeType,
        int bufferView,
        byte[] bytes
) {
    public boolean hasEmbeddedBytes() {
        return bytes != null && bytes.length > 0;
    }
}
