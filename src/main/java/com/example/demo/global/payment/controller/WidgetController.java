package com.example.demo.global.payment.controller;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST})
public class WidgetController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final JSONParser parser = new JSONParser();

    static class PaymentRequest {
        private String orderId;
        private Integer amount;
        private String paymentKey;

        // Getters and Setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public Integer getAmount() { return amount; }
        public void setAmount(Integer amount) { this.amount = amount; }
        public String getPaymentKey() { return paymentKey; }
        public void setPaymentKey(String paymentKey) { this.paymentKey = paymentKey; }
    }

    @PostMapping("/confirm")
    public ResponseEntity<JSONObject> confirmPayment(@RequestBody PaymentRequest request) throws Exception {
        try {
            // request 로깅
            logger.info("Received payment request: {}", request);

            // 필수 파라미터(Id, Amount, PaymentKey)가 누락되었는지 확인
            if (request.getOrderId() == null || request.getAmount() == null || request.getPaymentKey() == null) {
                throw new RuntimeException("Required parameters are missing");
            }

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


            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = obj.toString().getBytes(StandardCharsets.UTF_8);
                outputStream.write(input, 0, input.length);
                outputStream.flush();
            }

            int code = connection.getResponseCode();

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

    /**
     * 인증성공처리
     * @param request
     * @param model
     * @return
     * @throws Exception
     */
//    @GetMapping("/success")
//    public ResponseEntity<Void> paymentSuccess() {
//        return ResponseEntity.ok().build();
//    }
//
//    @RequestMapping(value = "/", method = RequestMethod.GET)
//    public String index(HttpServletRequest request, Model model) throws Exception {
//        return "/checkout";
//    }

    /**
     * 인증실패처리
     * @param request
     * @param model
     * @return
     * @throws Exception
     */
//    @GetMapping("/fail")
//    public ResponseEntity<Void> paymentFail() {
//        return ResponseEntity.ok().build();
//    }
}
