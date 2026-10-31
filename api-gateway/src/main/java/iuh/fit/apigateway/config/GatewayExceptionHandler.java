package iuh.fit.apigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Xử lý ngoại lệ tầng Gateway trả về định dạng JSON chuẩn thay vì HTML whitelabel error
 * khi dịch vụ phía sau bị sập (503 Service Unavailable) hoặc quá thời gian chờ (504 Timeout).
 */
@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Đã xảy ra lỗi tại cổng API Gateway";

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else {
            String exMsg = ex.getMessage() != null ? ex.getMessage() : "";
            if (exMsg.contains("Connection refused") || exMsg.contains("503") || exMsg.contains("FinishConnectException")) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                message = "Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau giây lát";
            } else if (exMsg.contains("Timeout") || exMsg.contains("504")) {
                status = HttpStatus.GATEWAY_TIMEOUT;
                message = "Dịch vụ phản hồi quá thời gian cho phép (Gateway Timeout)";
            } else if (exMsg.contains("404") || exMsg.contains("Not Found")) {
                status = HttpStatus.NOT_FOUND;
                message = "Đường dẫn yêu cầu không tồn tại trên hệ thống";
            }
        }

        log.warn("[GatewayException] URI={}, Status={}, Msg={}",
                exchange.getRequest().getURI().getPath(), status, ex.getMessage());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String jsonResponse = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"path\":\"%s\"}",
                status.value(), message.replace("\"", "\\\""), exchange.getRequest().getURI().getPath()
        );

        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
