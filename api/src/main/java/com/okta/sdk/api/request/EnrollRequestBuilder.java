/*
 * Copyright 2020-Present Okta, Inc.
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
package com.okta.sdk.api.request;

import com.okta.sdk.api.model.Authenticator;

public class EnrollRequestBuilder {

    private Authenticator authenticator;

    private String stateHandle;

    public static EnrollRequestBuilder builder() {
        return new EnrollRequestBuilder();
    }

    public EnrollRequestBuilder withAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
        return this;
    }

    public EnrollRequestBuilder withStateHandle(String stateHandle) {
        this.stateHandle = stateHandle;
        return this;
    }

    public EnrollRequest build() {
        return new EnrollRequest(stateHandle, authenticator);
    }
}
