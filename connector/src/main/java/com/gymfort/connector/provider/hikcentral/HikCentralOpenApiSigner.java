package com.gymfort.connector.provider.hikcentral;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Implements the AK/SK canonical signature documented by HikCentral OpenAPI. */
public final class HikCentralOpenApiSigner {
    public SignedHeaders sign(
            String method,
            String path,
            byte[] body,
            String appKey,
            char[] appSecret,
            long timestamp,
            String nonce
    ) {
        try {
            String contentMd5 = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("MD5").digest(body));
            String signatureHeaderNames = "x-ca-key,x-ca-nonce,x-ca-timestamp";
            String canonical = method.toUpperCase() + "\n"
                    + "*/*\n"
                    + contentMd5 + "\n"
                    + "application/json\n"
                    + "x-ca-key:" + appKey + "\n"
                    + "x-ca-nonce:" + nonce + "\n"
                    + "x-ca-timestamp:" + timestamp + "\n"
                    + path;

            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(
                    new String(appSecret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(
                    hmac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "*/*");
            headers.put("Content-Type", "application/json");
            headers.put("Content-MD5", contentMd5);
            headers.put("X-Ca-Key", appKey);
            headers.put("X-Ca-Nonce", nonce);
            headers.put("X-Ca-Timestamp", Long.toString(timestamp));
            headers.put("X-Ca-Signature-Headers", signatureHeaderNames);
            headers.put("X-Ca-Signature", signature);
            return new SignedHeaders(Map.copyOf(headers), canonical);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible firmar la solicitud HikCentral.", exception);
        }
    }

    public record SignedHeaders(Map<String, String> headers, String canonicalString) {
    }
}
