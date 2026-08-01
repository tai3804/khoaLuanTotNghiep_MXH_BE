package iuh.fit.mediaservice.infrastructure.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws.s3")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AwsS3Properties {
    String region;
    String accessKeyId;
    String secretAccessKey;
    String bucketName;
    String customDomain;
}
