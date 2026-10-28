package com.venuity.server.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.venuity.common.ErrorCode;
import com.venuity.server.api.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Service
public class QrService {

    public byte[] png(String payload, int size) {
        if (payload == null || payload.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "QR payload is empty");
        }
        int pixels = Math.max(96, Math.min(size <= 0 ? 512 : size, 2048));
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 4);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, pixels, pixels, hints);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not generate QR code");
        }
    }
}
