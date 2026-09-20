package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.application.dto.response.ServiceHealthResponse;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GetSystemHealthQuery {

    // Map of Service Name -> Port
    private static final Map<String, Integer> SERVICES = new LinkedHashMap<>();

    static {
        SERVICES.put("Eureka Discovery Server", 8761);
        SERVICES.put("API Gateway", 8080);
        SERVICES.put("Auth Service", 8081);
        SERVICES.put("User Service", 8082);
        SERVICES.put("Post Service", 8083);
        SERVICES.put("Media Service", 8084);
        SERVICES.put("Chat Service", 8085);
        SERVICES.put("Notification Service", 8086);
        SERVICES.put("Moderation Service", 8089);
        SERVICES.put("Admin Service", 8090);
    }

    public List<ServiceHealthResponse> execute() {
        List<ServiceHealthResponse> healthList = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : SERVICES.entrySet()) {
            String name = entry.getKey();
            int port = entry.getValue();
            
            long startTime = System.currentTimeMillis();
            boolean isUp = false;
            
            try (Socket socket = new Socket()) {
                // Try to connect with a timeout of 1000ms
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                isUp = true;
            } catch (Exception e) {
                isUp = false;
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
