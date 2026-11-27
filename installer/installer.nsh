; Dunkelgraues Setup passend zum Client (eingebunden ueber package.json, "nsis.include").
; NSIS faerbt mit MUI_BGCOLOR nur Kopfzeile und Abschlussseite; alle anderen Flaechen und Texte
; werden hier je Seite nachgefaerbt. Knoepfe nutzen das dunkle Windows-Design, der Ladebalken die Clientfarbe.
; Farben: Flaeche 1E1F22, Eingabefelder 2B2D31, Schrift E8E9EC, Balken 27D86A (in Windows-Reihenfolge BGR)

!define MUI_BGCOLOR "1E1F22"
!define MUI_TEXTCOLOR "E8E9EC"
!define MUI_INSTFILESPAGE_COLORS "E8E9EC 2B2D31"

!ifndef BUILD_UNINSTALLER
  !define MUI_CUSTOMFUNCTION_GUIINIT DeuGuiInit
!else
  !define MUI_CUSTOMFUNCTION_UNGUIINIT un.DeuGuiInit
!endif

; Fenster selbst: dunkle Titelleiste und Hintergrund. Versionszeile und helle Trennlinien werden ausgeblendet
!macro deuHide ID
  GetDlgItem $0 $HWNDPARENT ${ID}
  ShowWindow $0 0
!macroend

; Knopf im dunklen Windows-Design. "DarkMode_DarkTheme" zeichnet ihn flach ohne hellen Rand;
; gibt es das auf dem Rechner nicht, wird das aeltere dunkle Design genommen (Windows 10 1809 und neuer).
!macro deuDarkButton HWND
  Push $1
  Push $2
  StrCpy $2 "DarkMode_Explorer"
  System::Call "uxtheme::OpenThemeData(p 0, w 'DarkMode_DarkTheme::Button') p .r1"
  ${If} $1 <> 0
    System::Call "uxtheme::CloseThemeData(p r1)"
    StrCpy $2 "DarkMode_DarkTheme"
  ${EndIf}
  System::Call "uxtheme::#133(p ${HWND}, i 1)"
  System::Call "uxtheme::SetWindowTheme(p ${HWND}, w r2, p 0)"
  SetCtlColors ${HWND} "E8E9EC" "1E1F22" ; Flaeche hinter den runden Ecken
  Pop $2
  Pop $1
!macroend

!macro deuGuiInit
  Push $0
  ; dunkles Design fuer dieses Programm erlauben (Zurueck, Weiter, Abbrechen)
  System::Call "uxtheme::#135(i 2)"
  GetDlgItem $0 $HWNDPARENT 1
  !insertmacro deuDarkButton $0
  GetDlgItem $0 $HWNDPARENT 2
  !insertmacro deuDarkButton $0
  GetDlgItem $0 $HWNDPARENT 3
  !insertmacro deuDarkButton $0
  System::Call "dwmapi::DwmSetWindowAttribute(p $HWNDPARENT, i 20, *i 1, i 4)"
  SetCtlColors $HWNDPARENT "E8E9EC" "1E1F22"
  !insertmacro deuHide 1028
  !insertmacro deuHide 1256
  !insertmacro deuHide 1035
  !insertmacro deuHide 1036
  !insertmacro deuHide 1045
  Pop $0
!macroend

; Wird nach den Seiten eingefuegt, dann sind alle Hilfsmittel von electron-builder bekannt
!macro customHeader
  !ifdef BUILD_UNINSTALLER
    Function un.DeuGuiInit
      !insertmacro deuGuiInit
    FunctionEnd
  !else
    Function DeuGuiInit
      !insertmacro deuGuiInit
    FunctionEnd

    ; Alle Steuerelemente eines Fensters einfaerben. Aufruf: Push <Fenster>, Call DeuPaint
    Function DeuPaint
      Exch $R9
      Push $R8
      Push $R7
      Push $R6
      SetCtlColors $R9 "E8E9EC" "1E1F22"
      System::Call "user32::GetWindow(p R9, i 5) p .R8"
      ${While} $R8 <> 0
        System::Call "user32::GetClassName(p R8, t .R7, i 64)"
        ${If} $R7 == "Static"
          SetCtlColors $R8 "E8E9EC" "1E1F22"
        ${ElseIf} $R7 == "Edit"
          !insertmacro deuDarkButton $R8 ; dunkler Feldrand statt des weissen
          SetCtlColors $R8 "E8E9EC" "2B2D31"
        ${ElseIf} $R7 == "Button"
          System::Call "user32::GetWindowLong(p R8, i -16) i .R6"
          IntOp $R6 $R6 & 0x0F
          ; Kontrollkaestchen, Auswahlknoepfe und Gruppenrahmen (2 bis 9), nicht die normalen Knoepfe
          ${If} $R6 = 7
            ShowWindow $R8 0 ; der Gruppenrahmen um die Ordnerauswahl hat eine helle Linie
          ${ElseIf} $R6 >= 2
          ${AndIf} $R6 <= 9
            ; ohne Windows-Design wird die Schriftfarbe uebernommen
            System::Call "uxtheme::SetWindowTheme(p R8, w ' ', w ' ')"
            SetCtlColors $R8 "E8E9EC" "1E1F22"
          ${EndIf}
          ${If} $R6 <= 1 ; "Durchsuchen" und "Details anzeigen"
            !insertmacro deuDarkButton $R8
          ${EndIf}
        ${ElseIf} $R7 == "msctls_progress32"
          System::Call "uxtheme::SetWindowTheme(p R8, w ' ', w ' ')"
          SendMessage $R8 1033 0 0x006AD827 ; Balkenfarbe
          SendMessage $R8 8193 0 0x00312D2B ; Hintergrund
        ${ElseIf} $R7 == "SysListView32"
          System::Call "uxtheme::SetWindowTheme(p R8, w 'DarkMode_Explorer', p 0)"
        ${EndIf}
        System::Call "user32::GetWindow(p R8, i 2) p .R8"
      ${EndWhile}
      Pop $R6
      Pop $R7
      Pop $R8
      Pop $R9
    FunctionEnd

    Function DeuShowPage
      Push $0
      FindWindow $0 "#32770" "" $HWNDPARENT
      Push $0
      Call DeuPaint
      Pop $0
    FunctionEnd

    ; Die Ordnerauswahl hat keinen eigenen Anzeige-Aufruf; diese Pruefung laeuft beim Oeffnen der Seite
    Function .onVerifyInstDir
      Call DeuShowPage
    FunctionEnd

    Function DeuShowFinish
      Push $0
      ; die Abschlussseite blendet die Trennlinien wieder ein
      !insertmacro deuHide 1035
      !insertmacro deuHide 1045
      !insertmacro deuHide 1046
      Pop $0
      System::Call "uxtheme::SetWindowTheme(p $mui.FinishPage.Run, w ' ', w ' ')"
      SetCtlColors $mui.FinishPage.Run "E8E9EC" "1E1F22"
    FunctionEnd
  !endif
!macroend

; vor der Auswahl "Nur fuer mich / Fuer alle"
!macro customWelcomePage
  !define MUI_PAGE_CUSTOMFUNCTION_SHOW DeuShowPage
!macroend

; vor der Fortschrittsseite
!macro customPageAfterChangeDir
  !define MUI_PAGE_CUSTOMFUNCTION_SHOW DeuShowPage
!macroend

; Abschlussseite wie bei electron-builder ("DeuClient starten"), zusaetzlich eingefaerbt
!macro customFinishPage
  Function StartApp
    ${if} ${isUpdated}
      StrCpy $1 "--updated"
    ${else}
      StrCpy $1 ""
    ${endif}
    ${StdUtils.ExecShellAsUser} $0 "$launchLink" "open" "$1"
  FunctionEnd

  !define MUI_FINISHPAGE_RUN
  !define MUI_FINISHPAGE_RUN_FUNCTION "StartApp"
  !define MUI_PAGE_CUSTOMFUNCTION_SHOW DeuShowFinish
  !insertmacro MUI_PAGE_FINISH
!macroend
