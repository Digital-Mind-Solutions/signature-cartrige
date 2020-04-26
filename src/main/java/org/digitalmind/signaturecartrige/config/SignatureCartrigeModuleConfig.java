package org.digitalmind.signaturecartrige.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.digitalmind.signaturecartrige.config.SignatureCartrigeModuleConfig.*;

@Configuration
@ComponentScan({
        SERVICE_PACKAGE,
        API_PACKAGE
})
@EnableCaching
@ConditionalOnProperty(name = ENABLED, havingValue = "true")
@Slf4j
public class SignatureCartrigeModuleConfig {
    public static final String MODULE = "signaturecartrige";
    public static final String PREFIX = "application.modules.common." + MODULE;
    public static final String ENABLED = PREFIX + ".enabled";
    public static final String API_ENABLED = PREFIX + ".api.enabled";

    public static final String ROOT_PACKAGE = "org.digitalmind." + MODULE;
    public static final String CONFIG_PACKAGE = ROOT_PACKAGE + ".config";
    public static final String SERVICE_PACKAGE = ROOT_PACKAGE + ".service";
    public static final String API_PACKAGE = ROOT_PACKAGE + ".api";
}
