Unicode True
RequestExecutionLevel user
SetCompressor /SOLID lzma
!include "LogicLib.nsh"
!include "MUI2.nsh"


!define APP_PUBLISHER "{{ app_publisher }}"
!define APP_NAME "{{ app_name }}"
!define APP_DISPLAY_NAME "{{ app_display_name }}"
!define APP_DATA_DIR_NAME "{{ app_data_dir_name }}"
!define APP_VERSION "{{ app_version }}"
!define APP_VERSION_WITH_BUILD "{{ app_version_with_build }}"
!define APP_DISPLAY_VERSION "{{ app_display_version }}"
!define SOURCE_CODE_URL "{{ source_code_url }}"
!define PROJECT_WEBSITE "{{ project_website }}"
!define COPYRIGHT "{{ copyright }}"

!define INPUT_DIR "{{ input_dir }}"
!define LICENSE_FILE "{{ license_file }}"
!define MAIN_BINARY_NAME "${APP_NAME}"
!define NATIVE_MESSAGING_HOST_BINARY_NAME "{{ native_messaging_host_binary_name }}"
!define CLI_BINARY_NAME "{{ cli_binary_name }}"

!define SIDEBAR_IMAGE "{{ sidebar_image_file }}"
!define HEADER_IMAGE "{{ header_image_file }}"
!define ICON_FILE "{{ icon_file }}"

!define REG_UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"
!define REG_RUN_KEY "Software\Microsoft\Windows\CurrentVersion\Run\${APP_NAME}"
!define REG_APP_KEY "Software\${APP_NAME}"

; icon for this installer!

Icon "${ICON_FILE}"
!define MUI_ICON "${ICON_FILE}"
!define MUI_UNICON "${ICON_FILE}"

!if "${SIDEBAR_IMAGE}" != ""
  !define MUI_WELCOMEFINISHPAGE_BITMAP "${SIDEBAR_IMAGE}"

  !define MUI_UNWELCOMEFINISHPAGE_BITMAP "${SIDEBAR_IMAGE}"
!endif

!if "${HEADER_IMAGE}" != ""
  !define MUI_HEADERIMAGE
  !define MUI_HEADERIMAGE_BITMAP  "${HEADER_IMAGE}"

  !define MUI_UNHEADERIMAGE
  !define MUI_UNHEADERIMAGE_BITMAP "${HEADER_IMAGE}"
!endif

VIProductVersion "${APP_VERSION_WITH_BUILD}"
VIAddVersionKey "ProductName" "${APP_DISPLAY_NAME}"
VIAddVersionKey "FileDescription" "${APP_DISPLAY_NAME}"
VIAddVersionKey "LegalCopyright" "${COPYRIGHT}"
VIAddVersionKey "FileVersion" "${APP_VERSION_WITH_BUILD}"
VIAddVersionKey "ProductVersion" "${APP_VERSION_WITH_BUILD}"

Name "${APP_DISPLAY_NAME}"
OutFile "{{ output_file }}"

InstallDir "$LOCALAPPDATA\${APP_NAME}"



!define INSTALL_DIR `$INSTDIR`

; the Setup starts by asking English or Arabic (like IDM); the answer is remembered for the next updates
!define MUI_LANGDLL_REGISTRY_ROOT "HKCU"
!define MUI_LANGDLL_REGISTRY_KEY "${REG_APP_KEY}"
!define MUI_LANGDLL_REGISTRY_VALUENAME "InstallerLanguage"
!define MUI_LANGDLL_WINDOWTITLE "${APP_DISPLAY_NAME}"
!define MUI_LANGDLL_INFO "Please select a language / الرجاء اختيار اللغة"

Function .onInit

    ; Call RestorePreviousInstallLocation
    !insertmacro MUI_LANGDLL_DISPLAY

FunctionEnd

Function un.onInit
    !insertmacro MUI_UNGETLANGUAGE
FunctionEnd

; untick add-ons of browsers that are not installed (function defined further down)
!define MUI_CUSTOMFUNCTION_GUIINIT NdmUntickMissingBrowsers

; configure instfiles page
!define MUI_FINISHPAGE_NOAUTOCLOSE
!define MUI_INSTFILESPAGE_NOAUTOCLOSE

; configure finish page
!define MUI_FINISHPAGE_LINK "$(NDM_GITHUB)"
!define MUI_FINISHPAGE_LINK_LOCATION "${SOURCE_CODE_URL}"
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION RunMainBinary

; IDM style welcome text
!define MUI_WELCOMEPAGE_TITLE "$(NDM_WELCOME_TITLE)"
!define MUI_WELCOMEPAGE_TEXT "$(NDM_WELCOME_TEXT)"
!define MUI_COMPONENTSPAGE_TEXT_TOP "$(NDM_COMPONENTS_TOP)"

;Installation Pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${LICENSE_FILE}"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

;Uninstallation Pages
!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_COMPONENTS
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

; set language (the first one is the default)
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Arabic"
!insertmacro MUI_RESERVEFILE_LANGDLL

LangString NDM_GITHUB ${LANG_ENGLISH} "Open project in GitHub"
LangString NDM_GITHUB ${LANG_ARABIC} "فتح المشروع على GitHub"
LangString NDM_WELCOME_TITLE ${LANG_ENGLISH} "Welcome to ${APP_DISPLAY_NAME} ${APP_VERSION}"
LangString NDM_WELCOME_TITLE ${LANG_ARABIC} "مرحبًا بك في ${APP_DISPLAY_NAME} ${APP_VERSION}"
LangString NDM_WELCOME_TEXT ${LANG_ENGLISH} "This will install ${APP_DISPLAY_NAME} on your computer.$\r$\n$\r$\nPlease close all browsers (Firefox, Chrome, Edge) before you continue, so the browser add-on can be set up.$\r$\n$\r$\nIf ${APP_DISPLAY_NAME} is already installed, your settings and downloads are kept.$\r$\n$\r$\nClick Next to continue."
LangString NDM_WELCOME_TEXT ${LANG_ARABIC} "سيتم تثبيت ${APP_DISPLAY_NAME} على جهازك.$\r$\n$\r$\nمن فضلك أغلق كل المتصفحات (Firefox و Chrome و Edge) قبل المتابعة حتى يتم إعداد إضافة المتصفح.$\r$\n$\r$\nإذا كان ${APP_DISPLAY_NAME} مثبتًا بالفعل فستبقى إعداداتك وتحميلاتك كما هي.$\r$\n$\r$\nاضغط التالي للمتابعة."
LangString NDM_COMPONENTS_TOP ${LANG_ENGLISH} "Choose what to install. The add-ons are offered only for the browsers found on this computer."
LangString NDM_COMPONENTS_TOP ${LANG_ARABIC} "اختر ما تريد تثبيته. الإضافات تظهر فقط للمتصفحات الموجودة على هذا الجهاز."
LangString NDM_SEC_STARTMENU ${LANG_ENGLISH} "Start Menu"
LangString NDM_SEC_STARTMENU ${LANG_ARABIC} "قائمة ابدأ"
LangString NDM_SEC_DESKTOP ${LANG_ENGLISH} "Create an icon on Desktop"
LangString NDM_SEC_DESKTOP ${LANG_ARABIC} "إنشاء أيقونة على سطح المكتب"
LangString NDM_SEC_BROWSERS ${LANG_ENGLISH} "Browser extensions"
LangString NDM_SEC_BROWSERS ${LANG_ARABIC} "إضافات المتصفح"
LangString NDM_SEC_FIREFOX ${LANG_ENGLISH} "Firefox add-on"
LangString NDM_SEC_FIREFOX ${LANG_ARABIC} "إضافة Firefox"
LangString NDM_SEC_CHROME ${LANG_ENGLISH} "Chrome extension"
LangString NDM_SEC_CHROME ${LANG_ARABIC} "إضافة Chrome"
LangString NDM_SEC_EDGE ${LANG_ENGLISH} "Edge extension"
LangString NDM_SEC_EDGE ${LANG_ARABIC} "إضافة Edge"
LangString NDM_SEC_USERDATA ${LANG_ENGLISH} "Remove User Data"
LangString NDM_SEC_USERDATA ${LANG_ARABIC} "حذف بيانات المستخدم"
LangString NDM_CHROME_STEPS ${LANG_ENGLISH} "Add the extension to Chrome (only once, it stays after that):$\r$\n$\r$\n1. Turn on Developer mode (top right of the page that just opened)$\r$\n2. Click Load unpacked$\r$\n3. Pick the folder that just opened:$\r$\n${INSTALL_DIR}\app\resources\addons\chrome"
LangString NDM_CHROME_STEPS ${LANG_ARABIC} "إضافة الامتداد إلى Chrome (مرة واحدة فقط ويبقى بعدها):$\r$\n$\r$\n1. فعّل Developer mode (أعلى يمين الصفحة التي فُتحت)$\r$\n2. اضغط Load unpacked$\r$\n3. اختر المجلد الذي فُتح:$\r$\n${INSTALL_DIR}\app\resources\addons\chrome"
LangString NDM_EDGE_STEPS ${LANG_ENGLISH} "Add the extension to Edge (only once, it stays after that):$\r$\n$\r$\n1. Turn on Developer mode (left side of the page that just opened)$\r$\n2. Click Load unpacked$\r$\n3. Pick the folder that just opened:$\r$\n${INSTALL_DIR}\app\resources\addons\chrome"
LangString NDM_EDGE_STEPS ${LANG_ARABIC} "إضافة الامتداد إلى Edge (مرة واحدة فقط ويبقى بعدها):$\r$\n$\r$\n1. فعّل Developer mode (يسار الصفحة التي فُتحت)$\r$\n2. اضغط Load unpacked$\r$\n3. اختر المجلد الذي فُتح:$\r$\n${INSTALL_DIR}\app\resources\addons\chrome"

; a macro clear files to cleanup installation folder
!macro clearFiles
    RmDir /r "${INSTALL_DIR}\app"
    RmDir /r "${INSTALL_DIR}\runtime"
    Delete "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
    Delete "${INSTALL_DIR}\${MAIN_BINARY_NAME}.ico"
    Delete "${INSTALL_DIR}\${CLI_BINARY_NAME}.exe"
    Delete "${INSTALL_DIR}\${NATIVE_MESSAGING_HOST_BINARY_NAME}.exe"
    Delete "${INSTALL_DIR}\uninstall.exe"
    RmDir "${INSTALL_DIR}"
!macroend

Function RunMainBinary
   Exec "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
FunctionEnd

!macro closeProcess executableName
    DetailPrint "Stopping Executable ${executableName}"

    ${If} "$EXEFILE" != "${executableName}"
        nsExec::ExecToLog 'taskkill /F /IM "${executableName}"'
        Pop $0
    ${Else}
        DetailPrint "It seems that installer file name is same as executable name"
        DetailPrint "Please close app manually"
        StrCpy $0 "1"
    ${Endif}
!macroend

; Function RestorePreviousInstallLocation
;     ReadRegStr $4 SHCTX "${REG_APP_KEY}" "InstallPath"
;     ${if} $4 != ""
;         StrCpy $INSTDIR $4
;     ${endif}
; FunctionEnd

; I should improve this.
!macro closeApp
    !insertmacro closeProcess "${NATIVE_MESSAGING_HOST_BINARY_NAME}.exe"
    !insertmacro closeProcess "${CLI_BINARY_NAME}.exe"
    !insertmacro closeProcess "${MAIN_BINARY_NAME}.exe"

    ${If} $0 == "0"
        Sleep 500
        BringToFront ; when we sleep it seems that window goes down
        DetailPrint "Current app stopped successfully"
    ${Endif}
!macroend

!macro CreateStartMenu
	createDirectory "$SMPROGRAMS\${APP_DISPLAY_NAME}"
	createShortCut "$SMPROGRAMS\${APP_DISPLAY_NAME}\${APP_DISPLAY_NAME}.lnk" "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
!macroend

!macro RemoveStartMenu
	RmDir /r "$SMPROGRAMS\${APP_DISPLAY_NAME}"
!macroend

!macro RemoveUserData
	RMDir /r "$PROFILE\${APP_DATA_DIR_NAME}"
	RmDir /r "${INSTALL_DIR}\${APP_DATA_DIR_NAME}"
!macroend

!macro CreateDesktopShortcut
    CreateShortcut "$DESKTOP\${APP_DISPLAY_NAME}.lnk" "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
!macroend

!macro RemoveDesktopShortCut
	Delete "$DESKTOP\${APP_DISPLAY_NAME}.lnk"
!macroend

Function .onInstSuccess
    ; Check if the installer is running in silent mode
    ${If} ${Silent}
        ; In silent mode, always run the app
        Call RunMainBinary
    ${Endif}
FunctionEnd

Section "${APP_DISPLAY_NAME}"
    SectionInstType RO

    DetailPrint "Closing app (if any)"
    !insertmacro closeApp
    DetailPrint "clearing old app (if any)"
    !insertmacro clearFiles
    DetailPrint "writing new data"
    SetOutPath "${INSTALL_DIR}"
    CreateDirectory "${INSTALL_DIR}"

    WriteUninstaller "${INSTALL_DIR}\uninstall.exe"

    File /nonfatal /r "${INPUT_DIR}\"


    ; Registry information for add/remove programs
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayName" "${APP_DISPLAY_NAME}"
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayIcon" "$\"${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe$\""
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "Publisher" "${APP_PUBLISHER}"
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "InstallLocation" "$\"${INSTALL_DIR}$\""
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "UninstallString" "$\"${INSTALL_DIR}\uninstall.exe$\""
    WriteRegDWORD SHCTX "${REG_UNINSTALL_KEY}" "NoModify" "1"
    WriteRegDWORD SHCTX "${REG_UNINSTALL_KEY}" "NoRepair" "1"

    ; Registry keys for app installation path and version
    WriteRegStr SHCTX "${REG_APP_KEY}" "InstallPath" "${INSTALL_DIR}"
    WriteRegStr SHCTX "${REG_APP_KEY}" "Version" "${APP_VERSION}"

    ; the app opens in the language chosen on the first Setup page (read once, then deleted)
    ${IfNot} ${Silent}
        FileOpen $9 "${INSTALL_DIR}\installer-language.txt" w
        ${If} $LANGUAGE == ${LANG_ARABIC}
            FileWrite $9 "ar"
        ${Else}
            FileWrite $9 "en"
        ${EndIf}
        FileClose $9
    ${EndIf}
SectionEnd

Section "$(NDM_SEC_STARTMENU)"
    !insertmacro CreateStartMenu
SectionEnd

Section "$(NDM_SEC_DESKTOP)"
    !insertmacro CreateDesktopShortcut
SectionEnd

; Net Download Manager: offer the browser add-on for every browser found on this PC.
; Each ticked browser opens on the add-on page after the install, the user confirms there.
!define FIREFOX_ADDON_URL "https://addons.mozilla.org/firefox/addon/net-download-manager/"
!define CHROME_ADDON_URL "https://github.com/PhKero/net-download-manager/releases/latest"
!define EDGE_ADDON_URL "https://github.com/PhKero/net-download-manager/releases/latest"

!macro findBrowser exeName outVar
    ReadRegStr ${outVar} HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\${exeName}" ""
    ${If} ${outVar} == ""
        ReadRegStr ${outVar} HKCU "SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\${exeName}" ""
    ${EndIf}
!macroend

SectionGroup "$(NDM_SEC_BROWSERS)" SecBrowsers
Section "$(NDM_SEC_FIREFOX)" SecFirefox
    ; the Mozilla-signed add-on ships with the app; registering it makes Firefox itself ask
    ; "Net Download Manager added, enable?" on its next start. After that it stays installed.
    ${If} ${FileExists} "${INSTALL_DIR}\app\resources\addons\net-download-manager-firefox.xpi"
        WriteRegStr HKCU "Software\Mozilla\Firefox\Extensions" "net-download-manager@phkero" "${INSTALL_DIR}\app\resources\addons\net-download-manager-firefox.xpi"
    ${ElseIfNot} ${Silent}
        !insertmacro findBrowser "firefox.exe" $1
        ${If} $1 != ""
            Exec '"$1" "${FIREFOX_ADDON_URL}"'
        ${EndIf}
    ${EndIf}
SectionEnd
Section "$(NDM_SEC_CHROME)" SecChrome
    ; Chrome / Edge only install store add-ons by themselves; for ours the user loads the folder once
    ; (it stays installed after that, the folder sits inside the app and does not move)
    ${IfNot} ${Silent}
        !insertmacro findBrowser "chrome.exe" $1
        ${If} $1 != ""
            Exec '"$1" "chrome://extensions"'
            ExecShell "open" "${INSTALL_DIR}\app\resources\addons\chrome"
            MessageBox MB_OK|MB_ICONINFORMATION "$(NDM_CHROME_STEPS)"
        ${EndIf}
    ${EndIf}
SectionEnd
Section "$(NDM_SEC_EDGE)" SecEdge
    ${IfNot} ${Silent}
        !insertmacro findBrowser "msedge.exe" $1
        ${If} $1 != ""
            Exec '"$1" "edge://extensions"'
            ExecShell "open" "${INSTALL_DIR}\app\resources\addons\chrome"
            MessageBox MB_OK|MB_ICONINFORMATION "$(NDM_EDGE_STEPS)"
        ${EndIf}
    ${EndIf}
SectionEnd
SectionGroupEnd

; untick the browsers that are not installed
Function NdmUntickMissingBrowsers
    !insertmacro findBrowser "firefox.exe" $1
    ${If} $1 == ""
        SectionSetFlags ${SecFirefox} 0
    ${EndIf}
    !insertmacro findBrowser "chrome.exe" $1
    ${If} $1 == ""
        SectionSetFlags ${SecChrome} 0
    ${EndIf}
    !insertmacro findBrowser "msedge.exe" $1
    ${If} $1 == ""
        SectionSetFlags ${SecEdge} 0
    ${EndIf}
FunctionEnd

Section /o "un.$(NDM_SEC_USERDATA)"
    !insertmacro RemoveUserData
SectionEnd

Section "Uninstall"
    DeleteRegValue HKCU "Software\Mozilla\Firefox\Extensions" "net-download-manager@phkero"
    SectionInstType RO

    !insertmacro closeApp
    !insertmacro clearFiles

    !insertmacro RemoveStartMenu
    !insertmacro RemoveDesktopShortCut

    DeleteRegKey SHCTX "${REG_UNINSTALL_KEY}"
    DeleteRegKey SHCTX "${REG_APP_KEY}"

    ; remove auto start on boot registry
    DeleteRegValue SHCTX "${REG_RUN_KEY}" "${APP_NAME}"
SectionEnd
