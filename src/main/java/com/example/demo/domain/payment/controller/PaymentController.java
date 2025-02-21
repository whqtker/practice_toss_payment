package com.example.demo.domain.payment.controller;

import com.example.demo.domain.missing.entity.Missing;
import com.example.demo.domain.missing.service.MissingService;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST})
public class PaymentController {
    private final MissingService missingService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final JSONParser parser = new JSONParser();

    static class PaymentRequest {
        private String orderId;
        private Integer amount;
        private String paymentKey;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public Integer getAmount() { return amount; }
        public void setAmount(Integer amount) { this.amount = amount; }
        public String getPaymentKey() { return paymentKey; }
        public void setPaymentKey(String paymentKey) { this.paymentKey = paymentKey; }
    }

    // 결제 승인
    @PostMapping("/confirm")
    public ResponseEntity<JSONObject> confirmPayment(@RequestBody PaymentRequest request) throws Exception {
        try {
            // request 로깅
            logger.info("Received payment request: {}", request);

            // 필수 파라미터(Id, Amount, PaymentKey)가 누락되었는지 확인
            if (request.getOrderId() == null || request.getAmount() == null || request.getPaymentKey() == null) {
                throw new RuntimeException("Required parameters are missing");
            }

            // 결제 승인 API 요청을 위한 JSON 객체 생성
            JSONObject obj = new JSONObject();
            obj.put("orderId", request.getOrderId());
            obj.put("amount", request.getAmount());
            obj.put("paymentKey", request.getPaymentKey());

            // TODO: 개발자센터에 로그인해서 내 결제위젯 연동 키 > 시크릿 키를 입력하세요. 시크릿 키는 외부에 공개되면 안돼요.
            // @docs https://docs.tosspayments.com/reference/using-api/api-keys
            String widgetSecretKey = "test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6";

            // 토스페이먼츠 API는 시크릿 키를 사용자 ID로 사용하고, 비밀번호는 사용하지 않습니다.
            // 비밀번호가 없다는 것을 알리기 위해 시크릿 키 뒤에 콜론을 추가합니다.
            // @docs https://docs.tosspayments.com/reference/using-api/authorization#%EC%9D%B8%EC%A6%9D
            Base64.Encoder encoder = Base64.getEncoder();
            byte[] encodedBytes = encoder.encode((widgetSecretKey + ":").getBytes(StandardCharsets.UTF_8));
            String authorizations = "Basic " + new String(encodedBytes);

            // 결제 승인 API를 호출하세요.
            // 결제를 승인하면 결제수단에서 금액이 차감돼요.
            // @docs https://docs.tosspayments.com/guides/v2/payment-widget/integration#3-결제-승인하기
            URL url = new URL("https://api.tosspayments.com/v1/payments/confirm");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Authorization", authorizations);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // request 전송
            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = obj.toString().getBytes(StandardCharsets.UTF_8);
                outputStream.write(input, 0, input.length);
                outputStream.flush();
            }

            // 응답 코드
            int code = connection.getResponseCode();

            // 응답 코드에 따른 세부 처리
            try (InputStream responseStream = code == 200 ? connection.getInputStream() : connection.getErrorStream();
                 Reader reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8)) {
                JSONObject jsonObject = (JSONObject) parser.parse(reader);

                if (code != 200) {
                    logger.error("Payment failed: {}", jsonObject);
                    return ResponseEntity.status(code).body(jsonObject);
                }

                logger.info("Payment successful: {}", jsonObject);
                return ResponseEntity.ok(jsonObject);
            }
        } catch (Exception e) {
            logger.error("Payment processing error", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("code", "INTERNAL_ERROR");
            errorResponse.put("message", "결제 처리 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // 결제 요청
    @GetMapping("/pay/{missingId}")
    public ResponseEntity<?> pay(@PathVariable("missingId") Long missingId) {
        try {
            Missing missing = missingService.findById(missingId);
            if (missing == null) {
                return ResponseEntity.notFound().build();
            }

            // 프론트엔드 체크아웃 페이지로 리다이렉트
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("http://localhost:5173/checkout?missingId=" + missingId));

            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 결제 금액 조회
    @GetMapping("/pay/amount/{missingId}")
    public ResponseEntity<PaymentResponse> getPaymentAmount(@PathVariable("missingId") Long missingId) {
        try {
            Missing missing = missingService.findById(missingId);
            if (missing == null) {
                return ResponseEntity.notFound().build();
            }

            PaymentResponse response = new PaymentResponse();
            response.setAmount(missing.getReward());
            response.setMissingId(missingId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    static class PaymentResponse {
        private Integer amount;
        private Long missingId;

        public Integer getAmount() { return amount; }
        public void setAmount(Integer amount) { this.amount = amount; }
        public Long getMissingId() { return missingId; }
        public void setMissingId(Long missingId) { this.missingId = missingId; }
    }
}
