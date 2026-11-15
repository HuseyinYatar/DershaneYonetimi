<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("errorTitle")}
    <#elseif section = "form">
        <div id="kc-error-message" class="form">
            <div class="alert alert-error" role="alert">${kcSanitize(message.summary)?no_esc}</div>
            <#if !skipLink?? && client?? && client.baseUrl?has_content>
                <div class="${properties.kcFormButtonsClass!}">
                    <a id="backToApplication" class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!}" href="${client.baseUrl}">${msg("backToApplication")}</a>
                </div>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
