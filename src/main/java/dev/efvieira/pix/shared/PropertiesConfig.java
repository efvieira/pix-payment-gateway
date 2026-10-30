package dev.efvieira.pix.shared;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PixProperties.class)
public class PropertiesConfig {
}
