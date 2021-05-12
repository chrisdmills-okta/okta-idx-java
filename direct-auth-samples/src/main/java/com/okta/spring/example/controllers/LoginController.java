/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.spring.example.controllers;

import com.okta.commons.lang.Strings;
import com.okta.idx.sdk.api.client.Authenticator;
import com.okta.idx.sdk.api.client.IDXAuthenticationWrapper;
import com.okta.idx.sdk.api.client.ProceedContext;
import com.okta.idx.sdk.api.model.AuthenticationOptions;
import com.okta.idx.sdk.api.model.AuthenticationStatus;
import com.okta.idx.sdk.api.model.AuthenticatorType;
import com.okta.idx.sdk.api.model.ChangePasswordOptions;
import com.okta.idx.sdk.api.model.UserProfile;
import com.okta.idx.sdk.api.model.VerifyAuthenticatorOptions;
import com.okta.idx.sdk.api.response.AuthenticationResponse;
import com.okta.idx.sdk.api.response.NewUserRegistrationResponse;
import com.okta.spring.example.helpers.HomeHelper;
import com.okta.spring.example.helpers.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

@Controller
public class LoginController {

    /**
     * logger instance.
     */
    private final Logger logger = LoggerFactory.getLogger(LoginController.class);

    /**
     * idx authentication wrapper instance.
     */
    @Autowired
    private IDXAuthenticationWrapper idxAuthenticationWrapper;

    /**
     * home helper instance.
     */
    @Autowired
    private HomeHelper homeHelper;

    /**
     * Handle login with the supplied username and password.
     *
     * @param username the username
     * @param password the password
     * @param session the session
     * @return the home page view (if login is successful), else the login page with errors.
     */
    @PostMapping("/custom-login")
    public ModelAndView handleLogin(final @RequestParam("username") String username,
                                    final @RequestParam("password") String password,
                                    final HttpSession session) {
        // trigger authentication
        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.authenticate(new AuthenticationOptions(username, password));
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getAuthenticationStatus() == AuthenticationStatus.PASSWORD_EXPIRED) {
            return new ModelAndView("change-password");
        }

        if (authenticationResponse.getAuthenticationStatus() == AuthenticationStatus.AWAITING_AUTHENTICATOR_SELECTION) {
            ModelAndView mav = new ModelAndView("select-authenticators");
            mav.addObject("factorList",
                    getFactorMethodsFromAuthenticators(session, authenticationResponse.getAuthenticators()));
            return mav;
        }

        // populate login view with errors
        if (authenticationResponse.getAuthenticationStatus() != AuthenticationStatus.SUCCESS) {
            ModelAndView mav = new ModelAndView("login");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        return homeHelper.proceedToHome(authenticationResponse.getTokenResponse(), session);
    }

    /**
     * Handle forgot password (password recovery) functionality.
     *
     * @param username the username
     * @param session the session
     * @return the verify view (if password recovery operation is successful),
     * else the forgot password page with errors.
     */
    @PostMapping("/forgot-password")
    public ModelAndView handleForgotPassword(final @RequestParam("username") String username,
                                             final HttpSession session) {
        logger.info(":: Forgot Password ::");

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.recoverPassword(username);
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getAuthenticationStatus() == null
                || authenticationResponse.getAuthenticators() == null) {
            ModelAndView mav = new ModelAndView("forgot-password");
            mav.addObject("result", authenticationResponse.getErrors());
            return mav;
        }

        ModelAndView mav;

        switch (authenticationResponse.getAuthenticationStatus()) {
            case AWAITING_AUTHENTICATOR_SELECTION:
                mav = new ModelAndView("forgot-password-authenticators");
                mav.addObject("factorList",
                        getFactorMethodsFromAuthenticators(session, authenticationResponse.getAuthenticators()));
                return mav;

            case AWAITING_USER_EMAIL_ACTIVATION:
                mav = new ModelAndView("login");
                mav.addObject("errors", authenticationResponse.getErrors());
                return mav;

            default:
                return new ModelAndView("login");
        }
    }

    /**
     * Handle forgot password authenticator selection functionality.
     *
     * @param authenticatorType the authenticator type
     * @param session the session
     * @return the verify view, else the forgot-password-authenticators page with errors.
     */
    @PostMapping(value = "/forgot-password-authenticator")
    public ModelAndView handleForgotPasswordAuthenticator(final @RequestParam("authenticator-type") String authenticatorType,
                                                          final HttpSession session) {

        logger.info(":: Forgot password Authenticator ::");

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.selectAuthenticator(proceedContext,
                        getFactorFromMethod(session, authenticatorType));

        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getErrors().size() > 0) {
            ModelAndView mav = new ModelAndView("forgot-password-authenticators");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        return new ModelAndView("verify");
    }

    /**
     * Handle authenticator selection during authentication.
     *
     * @param authenticatorType the authenticatorType
     * @param session the session
     * @return authenticate-email view
     */
    @PostMapping(value = "/select-authenticator")
    public ModelAndView selectAuthenticator(final @RequestParam("authenticator-type") String authenticatorType,
                                            final HttpSession session) {
        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

      AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.selectAuthenticator(proceedContext,
                        getFactorFromMethod(session, authenticatorType));
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getErrors().size() > 0) {
            ModelAndView mav = new ModelAndView("select-authenticators");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        return new ModelAndView("authenticate-email");
    }

    /**
     * Handle email verification functionality during authentication.
     *
     * @param code the email verification code
     * @param session the session
     * @return the home page.
     */
    @PostMapping(value = "/authenticate-email")
    public ModelAndView authenticateEmail(final @RequestParam("code") String code,
                                          final HttpSession session) {
        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.verifyAuthenticator(proceedContext, new VerifyAuthenticatorOptions(code));
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getErrors().size() > 0) {
            ModelAndView mav = new ModelAndView("authenticate-email");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        if (authenticationResponse.getTokenResponse() != null) {
            return homeHelper.proceedToHome(authenticationResponse.getTokenResponse(), session);
        }

        ModelAndView mav = new ModelAndView("login");
        mav.addObject("info", authenticationResponse.getAuthenticationStatus().toString());
        return mav;
    }

    /**
     * Handle email verification functionality.
     *
     * @param code the email verification code
     * @param session the session
     * @return the change password view (if awaiting password reset), else the login page.
     */
    @PostMapping("/verify")
    public ModelAndView handleEmailVerify(final @RequestParam("code") String code,
                                          final HttpSession session) {
        logger.info(":: Verify Code :: {}", code);

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        VerifyAuthenticatorOptions verifyAuthenticatorOptions = new VerifyAuthenticatorOptions(code);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.verifyAuthenticator(proceedContext, verifyAuthenticatorOptions);
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getErrors().size() > 0) {
            ModelAndView mav = new ModelAndView("login");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        if (authenticationResponse.getAuthenticationStatus() == AuthenticationStatus.AWAITING_PASSWORD_RESET) {
            return new ModelAndView("change-password");
        }

        ModelAndView mav = new ModelAndView("login");
        mav.addObject("messages", authenticationResponse.getAuthenticationStatus().toString());
        return mav;
    }

    /**
     * Handle change password functionality.
     *
     * @param newPassword the new password
     * @param confirmNewPassword the confirmation of the new password
     * @param session the session
     * @return the login view
     */
    @PostMapping("/change-password")
    public ModelAndView handleChangePassword(final @RequestParam("new-password") String newPassword,
                                             final @RequestParam("confirm-new-password") String confirmNewPassword,
                                             final HttpSession session) {
        logger.info(":: Change Password ::");

        if (!newPassword.equals(confirmNewPassword)) {
            ModelAndView mav = new ModelAndView("change-password");
            mav.addObject("errors", "Passwords do not match");
            return mav;
        }

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        ChangePasswordOptions changePasswordOptions = new ChangePasswordOptions();
        changePasswordOptions.setNewPassword(newPassword);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.changePassword(proceedContext, changePasswordOptions);
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getErrors().size() > 0) {
            ModelAndView mav = new ModelAndView("change-password");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        if (authenticationResponse.getTokenResponse() != null) {
            return homeHelper.proceedToHome(authenticationResponse.getTokenResponse(), session);
        }

        ModelAndView mav = new ModelAndView("login");
        mav.addObject("info", authenticationResponse.getAuthenticationStatus().toString());
        return mav;
    }

    /**
     * Handle new user registration functionality.
     *
     * @param lastname the lastname
     * @param firstname the firstname
     * @param email the email
     * @param session the session
     * @return the enroll authenticators view.
     */
    @PostMapping("/register")
    public ModelAndView handleRegister(final @RequestParam("lastname") String lastname,
                                       final @RequestParam("firstname") String firstname,
                                       final @RequestParam("email") String email,
                                       final HttpSession session) {
        logger.info(":: Register ::");

        ModelAndView mav = new ModelAndView("enroll-authenticators");

        NewUserRegistrationResponse newUserRegistrationResponse =
                idxAuthenticationWrapper.fetchSignUpFormValues();

        UserProfile userProfile = new UserProfile();
        userProfile.addAttribute("lastName", lastname);
        userProfile.addAttribute("firstName", firstname);
        userProfile.addAttribute("email", email);

        ProceedContext proceedContext = newUserRegistrationResponse.getProceedContext();

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.register(proceedContext, userProfile);
        Util.updateSession(session, authenticationResponse.getProceedContext());

        // check for error
        if (authenticationResponse.getErrors().size() > 0) {
            mav = new ModelAndView("register");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        if (authenticationResponse.getTokenResponse() != null) {
            return homeHelper.proceedToHome(authenticationResponse.getTokenResponse(), session);
        }

        mav.addObject("factorList",
                getFactorMethodsFromAuthenticators(session, authenticationResponse.getAuthenticators()));
        return mav;
    }

    /**
     * Handle authenticator enrollment functionality.
     *
     * @param authenticatorType the authenticator type
     * @param session the session
     * @return the email or password authenticator enrollment view, else the enroll authenticators page with errors.
     */
    @PostMapping(value = "/enroll-authenticator")
    public ModelAndView handleEnrollAuthenticator(final @RequestParam("authenticator-type") String authenticatorType,
                                                  final HttpSession session) {
        logger.info(":: Enroll Authenticator ::");

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.enrollAuthenticator(proceedContext,
                        getFactorFromMethod(session, authenticatorType));
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getErrors().size() > 0) {
            ModelAndView mav = new ModelAndView("enroll-authenticators");
            mav.addObject("messages", authenticationResponse.getAuthenticationStatus().toString());
            return mav;
        }

        ModelAndView mav;

        switch (AuthenticatorType.get(authenticatorType)) {
            case EMAIL:
                mav = new ModelAndView("verify-email-authenticator-enrollment");
                return mav;

            case PASSWORD:
                mav = new ModelAndView("password-authenticator-enrollment");
                return mav;

            case SMS:
                mav = new ModelAndView("phone-authenticator-enrollment");
                mav.addObject("mode", AuthenticatorType.SMS.toString());
                return mav;

            case VOICE:
                mav = new ModelAndView("phone-authenticator-enrollment");
                mav.addObject("mode", AuthenticatorType.VOICE.toString());
                return mav;

            default:
                logger.error("Unsupported authenticator {}", authenticatorType);
                return new ModelAndView("enroll-authenticators");
        }
    }

    /**
     * Handle email authenticator verification functionality.
     *
     * @param code the email verification code
     * @param session the session
     * @return the login page view (if login operation is successful), else the enroll-authenticators page.
     */
    @PostMapping(value = "/verify-email-authenticator-enrollment")
    public ModelAndView handleVerifyEmailAuthenticator(final @RequestParam("code") String code,
                                                       final HttpSession session) {
        logger.info(":: Verify Email Authenticator :: {}", code);

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        VerifyAuthenticatorOptions verifyAuthenticatorOptions = new VerifyAuthenticatorOptions(code);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.verifyAuthenticator(proceedContext, verifyAuthenticatorOptions);
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getTokenResponse() != null) {
            return homeHelper.proceedToHome(authenticationResponse.getTokenResponse(), session);
        }

        if (idxAuthenticationWrapper.isSkipAuthenticatorPresent(proceedContext)) {
            AuthenticationResponse response =
                    idxAuthenticationWrapper.skipAuthenticatorEnrollment(proceedContext);
            Util.updateSession(session, response.getProceedContext());
            if (response.getTokenResponse() != null) {
                return homeHelper.proceedToHome(response.getTokenResponse(), session);
            } else if (response.getAuthenticationStatus() == AuthenticationStatus.SKIP_COMPLETE) {
                ModelAndView mav = new ModelAndView("login");
                if (response.getErrors().size() == 1) {
                    mav.addObject("info", response.getErrors().get(0));
                }
                return mav;
            }
        }

        ModelAndView mav = new ModelAndView("enroll-authenticators");
        mav.addObject("factorList",
                getFactorMethodsFromAuthenticators(session, authenticationResponse.getAuthenticators()));
        return mav;
    }

    /**
     * Handle password authenticator enrollment functionality.
     *
     * @param newPassword the new password
     * @param confirmNewPassword the confirmation for new password
     * @param session the session
     * @return the login page view (if login operation is successful), else the enroll-authenticators page.
     */
    @PostMapping(value = "/password-authenticator-enrollment")
    public ModelAndView handleEnrollPasswordAuthenticator(final @RequestParam("new-password") String newPassword,
                                                          final @RequestParam("confirm-new-password") String confirmNewPassword,
                                                          final HttpSession session) {
        logger.info(":: Enroll Password Authenticator ::");

        if (!newPassword.equals(confirmNewPassword)) {
            ModelAndView mav = new ModelAndView("password-authenticator-enrollment");
            mav.addObject("result", "Passwords do not match");
            return mav;
        }

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        VerifyAuthenticatorOptions verifyAuthenticatorOptions = new VerifyAuthenticatorOptions(confirmNewPassword);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.verifyAuthenticator(proceedContext, verifyAuthenticatorOptions);
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getTokenResponse() != null) {
            return homeHelper.proceedToHome(authenticationResponse.getTokenResponse(), session);
        }

        if (idxAuthenticationWrapper.isSkipAuthenticatorPresent(proceedContext)) {
            AuthenticationResponse response =
                    idxAuthenticationWrapper.skipAuthenticatorEnrollment(proceedContext);
            Util.updateSession(session, response.getProceedContext());

            if (response.getTokenResponse() != null) {
                return homeHelper.proceedToHome(response.getTokenResponse(), session);
            } else if (response.getAuthenticationStatus() == AuthenticationStatus.SKIP_COMPLETE) {
                ModelAndView mav = new ModelAndView("login");
                if (response.getErrors().size() == 1) {
                    mav.addObject("info", response.getErrors().get(0));
                }
                return mav;
            }
        }

        if (authenticationResponse.getAuthenticators() == null) {
            ModelAndView mav = new ModelAndView("password-authenticator-enrollment");
            mav.addObject("errors", authenticationResponse.getErrors());
            return mav;
        }

        List<String> factorList =
                getFactorMethodsFromAuthenticators(session, authenticationResponse.getAuthenticators());

        if (factorList.size() == 0) {
            ModelAndView mav = new ModelAndView("login");
            mav.addObject("info", "Success");
            return mav;
        }

        ModelAndView mav = new ModelAndView("enroll-authenticators");
        mav.addObject("factorList", factorList);
        return mav;
    }

    /**
     * Handle phone authenticator enrollment functionality.
     *
     * @param phone the phone number
     * @param mode the delivery mode - sms or voice
     * @return the submit phone authenticator enrollment page that allows user to input
     * the received code (if phone validation is successful), else presents the same page with error message.
     */
    @PostMapping(value = "/phone-authenticator-enrollment")
    public ModelAndView handleEnrollPhoneAuthenticator(final @RequestParam("phone") String phone,
                                                       final @RequestParam("mode") String mode) {
        logger.info(":: Enroll Phone Authenticator ::");

        if (!Strings.hasText(phone)) {
            ModelAndView mav = new ModelAndView("phone-authenticator-enrollment");
            mav.addObject("errors", "Phone is required");
            return mav;
        }

        // remove all whitespaces
        final String trimmedPhoneNumber = phone.replaceAll("\\s+", "");

        // validate phone number
        if (!Util.isValidPhoneNumber(phone)) {
            ModelAndView mav = new ModelAndView("phone-authenticator-enrollment");
            mav.addObject("errors", "Invalid phone number");
            return mav;
        }

        ModelAndView mav = new ModelAndView("submit-phone-authenticator-enrollment");
        mav.addObject("phone", trimmedPhoneNumber);
        mav.addObject("mode", mode);
        return mav;
    }

    /**
     * Handle phone authenticator submission form.
     *
     * @param phone the phone number
     * @param mode the delivery mode - sms or voice
     * @param session the session
     * @return the verify phone authenticator view with phone number.
     */
    @PostMapping(value = "/submit-phone-authenticator-enrollment")
    public ModelAndView handleSubmitPhoneAuthenticator(final @RequestParam("phone") String phone,
                                                       final @RequestParam("mode") String mode,
                                                       final HttpSession session) {
        logger.info(":: Submit Phone Authenticator :: {}", phone);

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);

        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.submitPhoneAuthenticator(proceedContext,
                        phone, getFactorFromMethod(session, mode));
        Util.updateSession(session, authenticationResponse.getProceedContext());

        ModelAndView mav = new ModelAndView("verify-phone-authenticator-enrollment");
        mav.addObject("phone", phone);
        return mav;
    }

    /**
     * Handle phone authenticator verification form.
     *
     * @param code the verification code
     * @param session the session
     * @return the home page view (if verification is complete), else the enroll-authenticators page.
     */
    @PostMapping(value = "/verify-phone-authenticator-enrollment")
    public ModelAndView handleVerifyPhoneAuthenticator(final @RequestParam("code") String code,
                                                       final HttpSession session) {
        logger.info(":: Verify Phone Authenticator ::");


        VerifyAuthenticatorOptions verifyAuthenticatorOptions = new VerifyAuthenticatorOptions(code);

        ProceedContext proceedContext = Util.getProceedContextFromSession(session);
        AuthenticationResponse authenticationResponse =
                idxAuthenticationWrapper.verifyAuthenticator(proceedContext, verifyAuthenticatorOptions);
        Util.updateSession(session, authenticationResponse.getProceedContext());

        if (authenticationResponse.getTokenResponse() != null) {
            return homeHelper.proceedToHome(authenticationResponse.getTokenResponse(), session);
        }

        if (idxAuthenticationWrapper.isSkipAuthenticatorPresent(proceedContext)) {
            AuthenticationResponse response =
                    idxAuthenticationWrapper.skipAuthenticatorEnrollment(proceedContext);
            Util.updateSession(session, response.getProceedContext());
            if (response.getTokenResponse() != null) {
                return homeHelper.proceedToHome(response.getTokenResponse(), session);
            } else if (response.getAuthenticationStatus() == AuthenticationStatus.SKIP_COMPLETE) {
                ModelAndView mav = new ModelAndView("login");
                if (response.getErrors().size() > 0) {
                    mav.addObject("info", response.getErrors());
                }
                return mav;
            }
        }

        ModelAndView mav = new ModelAndView("enroll-authenticators");
        mav.addObject("factorList",
                getFactorMethodsFromAuthenticators(session, authenticationResponse.getAuthenticators()));
        return mav;
    }

    private List<String> getFactorMethodsFromAuthenticators(final HttpSession session,
                                                            final List<Authenticator> authenticators) {
        List<String> factorMethods = new ArrayList<>();
        for (Authenticator authenticator : authenticators) {
            for (Authenticator.Factor factor : authenticator.getFactors()) {
                factorMethods.add(factor.getMethod());
            }
        }
        session.setAttribute("authenticators", authenticators);
        return factorMethods;
    }

    private Authenticator.Factor getFactorFromMethod(final HttpSession session,
                                                     final String method) {
        List<Authenticator> authenticators = (List<Authenticator>) session.getAttribute("authenticators");
        for (Authenticator authenticator : authenticators) {
            for (Authenticator.Factor factor : authenticator.getFactors()) {
                if (factor.getMethod().equals(method)) {
                    return factor;
                }
            }
        }
        throw new IllegalStateException("Factor not found: " + method);
    }
}
