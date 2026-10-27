package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.application.dto.response.ServiceHealthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GetSystemHealthQuery {

    @Autowired(required = false)
    private DiscoveryClient discoveryClient;

    @Autowired
    private Environment environment;

    private record ServiceMeta(String serviceId, int defaultPort, String envHostKey) {}

    private static final Map<String, ServiceMeta> SERVICES = new LinkedHashMap<>();

    static {
        SERVICES.put("Eureka Discovery Server", new ServiceMeta(null, 8761, "EUREKA_SERVER_HOST"));
        SERVICES.put("API Gateway", new ServiceMeta("api-gateway", 8080, "API_GATEWAY_HOST"));
        SERVICES.put("Auth Service", new ServiceMeta("auth-service", 8081, "AUTH_SERVICE_HOST"));
        SERVICES.put("Post Service", new ServiceMeta("post-service", 8082, "POST_SERVICE_HOST"));
        SERVICES.put("Notification Service", new ServiceMeta("notification-service", 8083, "NOTIFICATION_SERVICE_HOST"));
        SERVICES.put("Media Service", new ServiceMeta("media-service", 8084, "MEDIA_SERVICE_HOST"));
        SERVICES.put("User Service", new ServiceMeta("user-service", 8085, "USER_SERVICE_HOST"));
        SERVICES.put("Call Service", new ServiceMeta("call-service", 8086, "CALL_SERVICE_HOST"));
        SERVICES.put("Chat Service", new ServiceMeta("chat-service", 8087, "CHAT_SERVICE_HOST"));
        SERVICES.put("Feed Service", new ServiceMeta("feed-service", 8088, "FEED_SERVICE_HOST"));
        SERVICES.put("Admin Service", new ServiceMeta("admin-service", 8090, "ADMIN_SERVICE_HOST"));
        SERVICES.put("AI Service", new ServiceMeta("ai-service", 8091, "AI_SERVICE_HOST"));
    }

    public List<ServiceHealthResponse> execute() {
        List<ServiceHealthResponse> healthList = new ArrayList<>();

        for (Map.Entry<String, ServiceMeta> entry : SERVICES.entrySet()) {
            String name = entry.getKey();
            ServiceMeta meta = entry.getValue();

            long startTime = System.currentTimeMillis();
            boolean isUp = false;
            int port = meta.defaultPort();
            String host = environment != null ? environment.getProperty(meta.envHostKey(), "localhost") : "localhost";

            // 1. Try checking Eureka discovery if available
            if (discoveryClient != null && meta.serviceId() != null) {
                try {
                    List<ServiceInstance> instances = discoveryClient.getInstances(meta.serviceId());
                    if (instances != null && !instances.isEmpty()) {
                        isUp = true;
                        ServiceInstance firstInstance = instances.get(0);
                        port = firstInstance.getPort();
                        host = firstInstance.getHost();
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. Fallback to direct socket ping
            if (!isUp) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 1000);
                    isUp = true;
                } catch (Exception e) {
                    isUp = false;
                }
            }

            long endTime = System.currentTimeMillis();
            long responseTimeMs = endTime - startTime;

            healthList.add(ServiceHealthResponse.builder()
                    .name(name)
                    .port(port)
                    .status(isUp ? "UP" : "DOWN")
                    .responseTimeMs(isUp ? responseTimeMs : 0)
                    .build());
        }

        return healthList;
    }
}
