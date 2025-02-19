/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quarkus.runtime.configuration;

import static org.keycloak.quarkus.runtime.cli.Picocli.ARG_KEY_VALUE_SPLIT;
import static org.keycloak.quarkus.runtime.cli.Picocli.ARG_PREFIX;
import static org.keycloak.quarkus.runtime.cli.Picocli.ARG_SPLIT;
import static org.keycloak.quarkus.runtime.configuration.Configuration.getMappedPropertyName;
import static org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider.NS_KEYCLOAK_PREFIX;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import io.smallrye.config.PropertiesConfigSource;
import org.keycloak.quarkus.runtime.Environment;
import org.keycloak.quarkus.runtime.cli.Picocli;

/**
 * <p>A configuration source for mapping configuration arguments to their corresponding properties so that they can be recognized
 * when building and running the server.
 * 
 * <p>The mapping is based on the system property {@code kc.config.args}, where the value is a comma-separated list of
 * the arguments passed during build or runtime. E.g: "--http-enabled=true,--http-port=8180,--database-vendor=postgres".
 * 
 * <p>Each argument is going to be mapped to its corresponding configuration property by prefixing the key with the {@link MicroProfileConfigProvider#NS_KEYCLOAK} namespace. 
 */
public class ConfigArgsConfigSource extends PropertiesConfigSource {

    private static final Logger log = Logger.getLogger(ConfigArgsConfigSource.class);

    ConfigArgsConfigSource() {
        // higher priority over default Quarkus config sources
        super(parseArgument(), "CliConfigSource", 500);
    }

    @Override
    public String getValue(String propertyName) {
        String value = super.getValue(propertyName.replace('-', '.'));

        if (value != null) {
            return value;
        }

        return null;
    }

    private static Map<String, String> parseArgument() {
        String args = Environment.getConfigArgs();
        
        if (args == null || "".equals(args.trim())) {
            log.trace("No command-line arguments provided");
            return Collections.emptyMap();
        }
        
        Map<String, String> properties = new HashMap<>();

        for (String arg : ARG_SPLIT.split(args)) {
            if (!arg.startsWith(ARG_PREFIX)) {
                throw new IllegalArgumentException("Invalid argument format [" + arg + "], arguments must start with '--'");
            }

            String[] keyValue = ARG_KEY_VALUE_SPLIT.split(arg);
            String key = keyValue[0];
            
            if ("".equals(key.trim())) {
                throw new IllegalArgumentException("Invalid argument key");
            }
            
            String value;
            
            if (keyValue.length == 1) {
                continue;
            } else if (keyValue.length == 2) {
                // the argument has a simple value. Eg.: key=pair
                value = keyValue[1];
            } else {
                // to support cases like --db-url=jdbc:mariadb://localhost/kc?a=1
                value = arg.substring(key.length() + 1);
            }
            
            key = NS_KEYCLOAK_PREFIX + key.substring(2);

            log.tracef("Adding property [%s=%s] from command-line", key, value);
            properties.put(key, value);
            properties.put(getMappedPropertyName(key), value);
            // to make lookup easier, we normalize the key
            properties.put(Picocli.normalizeKey(key), value);
        }
        
        return properties;
    }
}
