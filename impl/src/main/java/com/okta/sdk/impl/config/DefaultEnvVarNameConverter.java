/*
 * Copyright 2014 Stormpath, Inc.
 * Modifications Copyright 2018 Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.sdk.impl.config;

import com.okta.commons.lang.Assert;
import com.okta.commons.lang.Strings;
import com.okta.sdk.impl.client.DefaultIDXClientBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultEnvVarNameConverter implements EnvVarNameConverter {

    private final Map<String, String> envToDotPropMap;

    public DefaultEnvVarNameConverter() {

        this.envToDotPropMap = buildReverseLookupToMap(
            DefaultIDXClientBuilder.DEFAULT_CLIENT_ISSUER_PROPERTY_NAME,
            DefaultIDXClientBuilder.DEFAULT_CLIENT_ID_PROPERTY_NAME,
            DefaultIDXClientBuilder.DEFAULT_CLIENT_SECRET_PROPERTY_NAME,
            DefaultIDXClientBuilder.DEFAULT_CLIENT_SCOPES_PROPERTY_NAME);
    }

    private Map<String, String> buildReverseLookupToMap(String... dottedPropertyNames) {
        return Arrays.stream(dottedPropertyNames)
            .collect(Collectors.toMap(this::toEnvVarName, dottedPropertyName -> dottedPropertyName));
    }

    @Override
    public String toEnvVarName(String dottedPropertyName) {
        Assert.hasText(dottedPropertyName, "dottedPropertyName argument cannot be null or empty.");
        dottedPropertyName = Strings.trimWhitespace(dottedPropertyName);

        StringBuilder sb = new StringBuilder();

        for (char c : dottedPropertyName.toCharArray()) {
            if (c == '.') {
                sb.append('_');
                continue;
            }
            sb.append(Character.toUpperCase(c));
        }

        return sb.toString();
    }

    @Override
    public String toDottedPropertyName(String envVarName) {
        Assert.hasText(envVarName, "envVarName argument cannot be null or empty.");
        envVarName = Strings.trimWhitespace(envVarName);

        //special cases (camel case):
        if (envToDotPropMap.containsKey(envVarName)) {
            return envToDotPropMap.get(envVarName);
        }

        //default cases:
        StringBuilder sb = new StringBuilder();

        for (char c : envVarName.toCharArray()) {
            if (c == '_') {
                sb.append('.');
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }

        return sb.toString();
    }
}
