<#import "template.ftl" as layout>
<#assign hasError = messagesPerField.existsError('username')>
<@layout.registrationLayout displayInfo=true displayMessage=!hasError; section>
    <#if section = "header">
        ${msg("emailForgotTitle")}
    <#elseif section = "form">
        <form id="kc-reset-password-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="username" class="${properties.kcLabelClass!}">${msg("usernameOrEmail")}</label>
                <input type="text" id="username" name="username" class="${properties.kcInputClass!}" autofocus autocomplete="username"
                       autocapitalize="none" spellcheck="false" value="${(auth.attemptedUsername!'')}"
                       aria-invalid="${hasError?c}" <#if hasError>aria-describedby="input-error-username"</#if> dir="ltr"/>
                <#if hasError>
                    <span id="input-error-username" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
                </#if>
            </div>
            <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" type="submit" value="${msg("doSubmit")}"/>
                <a class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!}" href="${url.loginUrl}">${msg("backToLogin")}</a>
            </div>
        </form>
    <#elseif section = "info" >
        <#if realm.duplicateEmailsAllowed>${msg("emailInstructionUsername")}<#else>${msg("emailInstruction")}</#if>
    </#if>
</@layout.registrationLayout>
