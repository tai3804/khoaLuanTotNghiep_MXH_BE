package iuh.fit.mediaservice.infrastructure.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AwsS3Config {

    AwsS3Properties awsS3Properties;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(awsS3Properties.getRegion()));

        if (awsS3Properties.getAccessKeyId() != null && !awsS3Properties.getAccessKeyId().isBlank()
                && awsS3Properties.getSecretAccessKey() != null && !awsS3Properties.getSecretAccessKey().isBlank()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    awsS3Properties.getAccessKeyId(),
                    awsS3Properties.getSecretAccessKey()
            );
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
