package com.minicube.launcher.service;

import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.minicube.launcher.util.Safety;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Accueil vocal : le launcher salue le joueur par son pseudo au demarrage.
 *
 * <p>La voix vient du systeme, jamais du reseau : rien n'est envoye a un service
 * exterieur, et le pseudo ne quitte pas la machine. Sous Windows, la synthese passe par
 * les voix <i>OneCore</i> de Windows 10 et 11, nettement plus naturelles que les anciennes
 * voix SAPI, avec repli automatique sur ces dernieres si elles sont indisponibles.</p>
 *
 * <p>Le resultat est conserve dans le cache : la phrase n'est synthetisee qu'une fois par
 * pseudo et par langue. Les demarrages suivants se contentent de rejouer le fichier, sans
 * lancer le moindre processus.</p>
 *
 * <h2>Pourquoi le pseudo ne figure jamais dans le script</h2>
 *
 * <p>La synthese Windows demande d'executer un script PowerShell. Y inserer le pseudo
 * reviendrait a executer du code choisi par l'utilisateur : un pseudo tel que
 * {@code "; Remove-Item ...} suffirait. Le script est donc une <b>constante</b>, transmise
 * encodee pour echapper a toute regle de citation, et la phrase voyage par une variable
 * d'environnement que PowerShell lit comme une simple donnee.</p>
 */
public class VoiceService {

    /** Au-dela, on ne synthetise pas : un pseudo n'a aucune raison d'etre si long. */
    private static final int MAX_PHRASE_LENGTH = 120;

    /** La synthese ne doit jamais retenir le launcher. */
    private static final int SYNTHESIS_TIMEOUT_SECONDS = 20;

    /**
     * Script de synthese Windows. Constante : rien de ce que fournit l'utilisateur n'y
     * entre. Il lit la phrase dans MINICUBE_VOICE_TEXT et ecrit le WAV dans
     * MINICUBE_VOICE_OUT.
     *
     * <p>Deux moteurs sont tentes dans l'ordre. Les voix OneCore, jointes a Windows 10 et
     * 11, sonnent nettement mieux mais ne sont accessibles qu'a travers WinRT ; l'ancien
     * moteur SAPI, lui, est present partout. La voix retenue est celle qui correspond a la
     * langue de l'interface, sans quoi une phrase francaise serait lue avec l'accent
     * d'une voix anglaise.</p>
     */
    private static final String WINDOWS_SCRIPT = String.join("\n",
            "$ErrorActionPreference = 'Stop'",
            "$texte = $env:MINICUBE_VOICE_TEXT",
            "$sortie = $env:MINICUBE_VOICE_OUT",
            "$langue = $env:MINICUBE_VOICE_LANG",
            "if ([string]::IsNullOrWhiteSpace($texte)) { exit 2 }",
            "try {",
            "  $null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media,"
                    + " ContentType = WindowsRuntime]",
            "  $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams,"
                    + " ContentType = WindowsRuntime]",
            "  Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() |"
                    + " Where-Object { $_.Name -eq 'AsTask' -and"
                    + " $_.GetParameters().Count -eq 1 -and"
                    + " $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
            "  function Attendre($op, $type) {",
            "    $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))",
            "    $null = $t.Wait(15000)",
            "    $t.Result",
            "  }",
            "  $synth = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer",
            "  $voix = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices |"
                    + " Where-Object { $_.Language -like \"$langue*\" } | Select-Object -First 1",
            "  if ($voix) { $synth.Voice = $voix }",
            "  $flux = Attendre $synth.SynthesizeTextToStreamAsync($texte)"
                    + " ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])",
            "  $lecteur = New-Object Windows.Storage.Streams.DataReader($flux)",
            "  $null = Attendre $lecteur.LoadAsync([uint32]$flux.Size) ([uint32])",
            "  $octets = New-Object byte[] $flux.Size",
            "  $lecteur.ReadBytes($octets)",
            "  [System.IO.File]::WriteAllBytes($sortie, $octets)",
            "  exit 0",
            "} catch {",
            "  try {",
            "    Add-Type -AssemblyName System.Speech",
            "    $s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "    $v = $s.GetInstalledVoices() | Where-Object {"
                    + " $_.VoiceInfo.Culture.Name -like \"$langue*\" } | Select-Object -First 1",
            "    if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }",
            "    $s.SetOutputToWaveFile($sortie)",
            "    $s.Speak($texte)",
            "    $s.Dispose()",
            "    exit 0",
            "  } catch { exit 3 }",
            "}");

    private final LauncherSettings settings;
    private final Path cacheDir;

    public VoiceService(LauncherSettings settings, Path cacheDir) {
        this.settings = settings;
        this.cacheDir = cacheDir;
    }

    /**
     * Souhaite la bienvenue au joueur, sans jamais bloquer l'appelant.
     *
     * @param username pseudo a prononcer ; vide, l'accueil reste generique
     */
    public void greet(String username) {
        if (!settings.isVoiceGreetingEnabled()) {
            return;
        }
        String name = clean(username);
        String phrase = name.isEmpty()
                ? I18n.tr("voice.welcome.anonymous")
                : I18n.tr("voice.welcome", name);

        Thread thread = new Thread(() -> speakQuietly(phrase), "minicube-voix");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Prononce une phrase. Un echec ne doit jamais remonter : l'accueil vocal est un
     * agrement, pas une fonction dont depend le launcher.
     */
    private void speakQuietly(String phrase) {
        try {
            speak(phrase);
        } catch (Exception e) {
            Log.debug("Accueil vocal indisponible : " + e.getMessage());
        }
    }

    private void speak(String phrase) throws Exception {
        if (OsUtil.isWindows()) {
            Path wave = cachedWave(phrase);
            if (!Files.isRegularFile(wave) || Files.size(wave) == 0) {
                synthesiseOnWindows(phrase, wave);
            }
            play(wave);
            return;
        }
        // macOS et Linux savent prononcer directement. Le texte est passe comme argument
        // d'un programme, jamais a un interpreteur de commandes : rien n'y est evalue.
        List<String> command = OsUtil.isMac()
                ? List.of("say", phrase)
                : List.of("spd-say", "--wait", phrase);
        run(command, null);
    }

    /* ------------------------------------------------------------------ */
    /* Synthese                                                            */
    /* ------------------------------------------------------------------ */

    /** Emplacement du fichier deja synthetise pour cette phrase. */
    private Path cachedWave(String phrase) throws Exception {
        String key = Hashing.toHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(phrase.getBytes(StandardCharsets.UTF_8)));
        return cacheDir.resolve("voice").resolve(key.substring(0, 16) + ".wav");
    }

    private void synthesiseOnWindows(String phrase, Path wave) throws Exception {
        Files.createDirectories(wave.getParent());

        // Chemin absolu : le programme execute ne doit pas dependre du PATH, qu'un
        // logiciel tiers pourrait avoir detourne.
        Path powershell = Path.of(System.getenv("SystemRoot") == null
                ? "C:\\Windows" : System.getenv("SystemRoot"),
                "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        if (!Files.isRegularFile(powershell)) {
            throw new IllegalStateException("PowerShell introuvable");
        }
        // -EncodedCommand attend de l'UTF-16LE encode en base64. Le script y passe entier,
        // sans guillemet a echapper : aucune regle de citation ne peut etre detournee.
        String encoded = Base64.getEncoder().encodeToString(
                WINDOWS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));

        ProcessBuilder builder = new ProcessBuilder(powershell.toString(),
                "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
        // La phrase et la destination voyagent hors du script : PowerShell les lit comme
        // des donnees, jamais comme du code.
        builder.environment().put("MINICUBE_VOICE_TEXT", phrase);
        builder.environment().put("MINICUBE_VOICE_OUT", wave.toString());
        builder.environment().put("MINICUBE_VOICE_LANG", I18n.currentLanguage());
        run(builder, wave);
    }

    private void run(List<String> command, Path expected) throws Exception {
        run(new ProcessBuilder(command), expected);
    }

    private void run(ProcessBuilder builder, Path expected) throws Exception {
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        if (!process.waitFor(SYNTHESIS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("synthese trop longue");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("synthese refusee (code "
                    + process.exitValue() + ")");
        }
        if (expected != null && (!Files.isRegularFile(expected) || Files.size(expected) == 0)) {
            Files.deleteIfExists(expected);
            throw new IllegalStateException("aucun son produit");
        }
        if (expected != null) {
            Safety.restrictToOwner(expected);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Lecture                                                             */
    /* ------------------------------------------------------------------ */

    /** Joue le fichier et attend la fin, pour liberer la carte son proprement. */
    private void play(Path wave) throws Exception {
        File file = wave.toFile();
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file);
             Clip clip = AudioSystem.getClip()) {
            Object done = new Object();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    synchronized (done) {
                        done.notifyAll();
                    }
                }
            });
            clip.open(stream);
            synchronized (done) {
                clip.start();
                // Duree du son plus une marge ; jamais d'attente indefinie.
                done.wait(clip.getMicrosecondLength() / 1000 + 2000);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Nettoyage du pseudo                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Reduit le pseudo a ce qui se prononce.
     *
     * <p>Les caracteres de controle sont ecartes : ils n'ont aucun sens a l'oral et
     * n'ont rien a faire dans une variable d'environnement.</p>
     */
    private String clean(String username) {
        if (username == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        username.codePoints()
                .filter(code -> !Character.isISOControl(code))
                .forEach(text::appendCodePoint);
        String result = text.toString().trim();
        return result.length() > MAX_PHRASE_LENGTH
                ? result.substring(0, MAX_PHRASE_LENGTH).trim()
                : result;
    }

    /** Efface les phrases deja synthetisees, par exemple apres un changement de langue. */
    public void clearCache() {
        Path dir = cacheDir.resolve("voice");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
            Log.debug("Cache de l'accueil vocal vide");
        } catch (Exception e) {
            Log.debug("Cache vocal non vide : " + e.getMessage());
        }
    }

}
