package iuh.fit.commonframework.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Tự động tìm và nạp các biến từ file .env ở thư mục gốc của backend vào Spring Environment.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File envFile = findDotenvFile();
        if (envFile != null && envFile.exists() && envFile.isFile()) {
            Map<String, Object> envMap = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                        envMap.put(key, value);
                    }
                }
                if (!envMap.isEmpty()) {
                    environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", envMap));
                    System.out.println("[Dotenv] Loaded " + envMap.size() + " environment variables from: " + envFile.getAbsolutePath());
                }
            } catch (Exception e) {
                System.err.println("[Dotenv] Failed to load .env file: " + e.getMessage());
            }
        }
    }

    private File findDotenvFile() {
        // 1. Kiểm tra thư mục làm việc hiện tại
        File currentDir = new File(System.getProperty("user.dir", "."));
        File envFile = new File(currentDir, ".env");
        if (envFile.exists()) {
            return envFile;
        }

        // 2. Kiểm tra thư mục cha (khi chạy từ module con trong IntelliJ IDEA)
        File parentDir = currentDir.getParentFile();
        if (parentDir != null) {
            envFile = new File(parentDir, ".env");
            if (envFile.exists()) {
                return envFile;
            }
        }

        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
