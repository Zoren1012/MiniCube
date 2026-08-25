; ---------------------------------------------------------------------------
; MiniCube - script d'installation Inno Setup
;
; Compile par package.ps1, qui transmet la version : ISCC /DAppVersion=1.2.0
; Le paquet installe l'image produite par jpackage, runtime Java compris :
; l'utilisateur final n'a donc aucun prerequis a installer.
; ---------------------------------------------------------------------------

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

#define AppName        "MiniCube"
#define AppPublisher   "MiniCube"
#define AppExeName     "MiniCube.exe"

[Setup]
AppId={{7C5CFF01-4E36-4C9B-9A1C-4D696E694375}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
VersionInfoVersion={#AppVersion}

; Installation par utilisateur : pas d'elevation, donc aucune fenetre de
; controle de compte a franchir pour lancer un launcher de jeu.
PrivilegesRequired=lowest
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
DisableDirPage=no
AllowNoIcons=yes

OutputDir=..\dist
OutputBaseFilename=MiniCube-Setup-{#AppVersion}
SetupIconFile=MiniCube.ico
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName} {#AppVersion}

Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\dist\{#AppName}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Les fichiers produits par l'application apres l'installation ne sont pas
; suivis par le desinstalleur : sans cela, le dossier resterait sur le disque.
Type: filesandordirs; Name: "{app}"
