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
package pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class RootPage {
    protected WebDriver driver;

    public RootPage(WebDriver driver){
        this.driver = driver;
        PageFactory.initElements(driver, this);
    }

    @FindBy(id = "login")
    public WebElement loginButton;

    @FindBy(id = "register")
    public WebElement registrationButton;

    public void navigateToTheRootPage(){
        driver.manage().window().maximize();
        driver.get("http://localhost:8080"); // TODO pass as env variable.
    }

    public boolean isLoginButtonDisplayed(){
        return loginButton.isDisplayed();
    }

    public boolean isRegistrationButtonDisplayed(){
        return registrationButton.isDisplayed();
    }
}
