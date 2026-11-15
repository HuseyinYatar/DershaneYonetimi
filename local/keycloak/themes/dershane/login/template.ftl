<#-- Dershane layout: two-column card (form left, value panel right; the panel is hidden on mobile). -->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false subtitle="">
<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light">
    <title>${msg("loginTitle", (realm.displayName!""))}</title>
    <link rel="icon" type="image/png" href="${url.resourcesPath}/img/favicon.png" />
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
    </script>
    <script type="module">
        <#outputformat "JavaScript">
        import { startSessionPolling } from ${(url.resourcesPath + "/js/authChecker.js")?c};

        startSessionPolling(
            ${url.ssoLoginInOtherTabsUrl?c}
        );
        </#outputformat>
    </script>
    <script type="module">
        document.addEventListener("click", (event) => {
            const link = event.target.closest("a[data-once-link]");

            if (!link) {
                return;
            }

            if (link.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                return;
            }

            const { disabledClass } = link.dataset;

            if (disabledClass) {
                link.classList.add(...disabledClass.trim().split(/\s+/));
            }

            link.setAttribute("role", "link");
            link.setAttribute("aria-disabled", "true");
        });
    </script>
    <#if authenticationSession??>
        <script type="module">
            <#outputformat "JavaScript">
            import { checkAuthSession } from ${(url.resourcesPath + "/js/authChecker.js")?c};

            checkAuthSession(
                ${authenticationSession.authSessionIdHash?c}
            );
            </#outputformat>
        </script>
    </#if>
</head>

<body data-page-id="login-${pageId}">
<main class="shell">
    <div class="card">
        <section class="form-side">
            <div class="brand">
                <img class="brand-mark" src="${url.resourcesPath}/img/logo.png" alt="" />
                <span class="brand-name">Dershane</span>
            </div>

            <div class="form-body">
                <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
                    <#nested "show-username">
                    <p class="attempted-user">
                        <span id="kc-attempted-username">${auth.attemptedUsername}</span>
                        <a id="reset-login" href="${url.loginRestartFlowUrl}">${msg("restartLoginTooltip")}</a>
                    </p>
                </#if>
                <h1 id="kc-page-title"><#nested "header"></h1>
                <#if subtitle?has_content><p class="lead">${subtitle}</p></#if>

                <#-- App-initiated actions should not see warning messages about the need to complete the action during login. -->
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="alert alert-${message.type}" role="<#if message.type = 'error'>alert<#else>status</#if>">
                        ${kcSanitize(message.summary)?no_esc}
                    </div>
                </#if>

                <#nested "form">

                <#if auth?has_content && auth.showTryAnotherWayLink()>
                    <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                        <input type="hidden" name="tryAnotherWay" value="on"/>
                        <a href="#" id="try-another-way" class="text-link"
                           onclick="document.forms['kc-select-try-another-way-form'].requestSubmit();return false;">${msg("doTryAnotherWay")}</a>
                    </form>
                </#if>

                <#nested "socialProviders">

                <#if displayInfo>
                    <div id="kc-info" class="info-text"><#nested "info"></div>
                </#if>
            </div>
        </section>

        <aside class="panel">
            <h2>${msg("panelTitle")}</h2>
            <ul class="features">
                <li>
                    <span class="feature-icon" aria-hidden="true">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 21v-6"/><path d="M12 21V3"/><path d="M19 21V9"/></svg>
                    </span>
                    <span><strong>${msg("panelExamTitle")}</strong>${msg("panelExamText")}</span>
                </li>
                <li>
                    <span class="feature-icon" aria-hidden="true">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M13 5h8"/><path d="M13 12h8"/><path d="M13 19h8"/><path d="m3 17 2 2 4-4"/><path d="m3 7 2 2 4-4"/></svg>
                    </span>
                    <span><strong>${msg("panelTaskTitle")}</strong>${msg("panelTaskText")}</span>
                </li>
                <li>
                    <span class="feature-icon" aria-hidden="true">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M8 2v3"/><path d="M16 2v3"/><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M3 10h18"/><path d="m9 16 2 2 4-4"/></svg>
                    </span>
                    <span><strong>${msg("panelAttendanceTitle")}</strong>${msg("panelAttendanceText")}</span>
                </li>
            </ul>
        </aside>
    </div>
</main>
</body>
</html>
</#macro>
