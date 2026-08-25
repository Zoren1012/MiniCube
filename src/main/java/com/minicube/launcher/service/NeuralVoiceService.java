package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.minicube.launcher.util.Safety;
import com.minicube.launcher.util.Zips;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Voix neuronale locale, portee par <a href="https://github.com/rhasspy/piper">Piper</a>.
 *
 * <p>Piper est un moteur de synthese neuronale qui tourne entierement sur la machine :
 * la qualite n'a rien a voir avec les voix concatenatives de Windows, et pourtant rien
 * ne part sur Internet une fois l'installation faite. Le pseudo du joueur ne quitte
 * jamais l'ordinateur.</p>
 *
 * <h2>Ce qui est telecharge, et pourquoi c'est sur</h2>
 *
 * <p>Le moteur et les voix pesent trop lourd pour etre joints a l'installeur : ils sont
 * telecharges au premier usage, sur demande explicite. Comme il s'agit d'un
 * <b>programme qui sera execute</b>, deux garanties valent d'etre enoncees :</p>
 *
 * <ul>
 *   <li>les adresses sont en HTTPS et figees dans le code, jamais lues sur le reseau ;</li>
 *   <li>chaque fichier est compare a une <b>empreinte SHA-256 inscrite ici</b>. Elle
 *       correspond exactement a la version verifiee lors du developpement : si l'hebergeur
 *       servait un autre contenu, l'installation echouerait au lieu de l'executer.</li>
 * </ul>
 *
 * <h2>La phrase ne passe pas par la ligne de commande</h2>
 *
 * <p>Piper lit le texte a prononcer sur son <b>entree standard</b>. Le pseudo n'apparait
 * donc ni dans la ligne de commande, ni dans un script : il n'y a aucune regle de citation
 * a detourner, et le pseudo reste une donnee du debut a la fin.</p>
 */
public class NeuralVoiceService {

    /** Le moteur n'est fourni que pour Windows ; ailleurs, la voix du systeme prend le relais. */
    private static final String ENGINE_URL =
            "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip";
    private static final String ENGINE_SHA256 =
            "f3c58906402b24f3a96d92145f58acba6d86c9b5db896d207f78dc80811efcea";
    private static final long ENGINE_BYTES = 22_477_236L;

    private static final String VOICE_BASE =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/";

    /**
     * Voix francaises retenues, avec l'empreinte de chaque fichier.
     *
     * <p>Le modele {@code .onnx} porte le reseau de neurones, le {@code .onnx.json} decrit
     * la facon de le lire. Les deux sont indispensables et verifies.</p>
     */
    public static final Map<String, Voice> VOICES = new LinkedHashMap<>();

    static {
        VOICES.put("fr_FR-siwis-medium", new Voice("fr_FR-siwis-medium",
                "Siwis", "siwis/medium/fr_FR-siwis-medium",
                "641d1ab097da2b81128c076810edb052b385decc8be3381814802a64a73baf99", 63_201_294L,
                "39479916c2db192b5ac9764daddd0c744d83e023ad890c6976c0633ae4df8959"));
        VOICES.put("fr_FR-upmc-medium", new Voice("fr_FR-upmc-medium",
                "UPMC", "upmc/medium/fr_FR-upmc-medium",
                "9abb3800c199148897a9ed64e100d224f3de83579f100044174ad19418f1786f", 76_733_615L,
                "e8636ec15dfd5d72db37a02cb5320a20f2b8d339f2a0e4337da64c58a33a5868"));
        VOICES.put("fr_FR-tom-medium", new Voice("fr_FR-tom-medium",
                "Tom", "tom/medium/fr_FR-tom-medium",
                "bf65074ccdeeeeaa832e75edb1c0a513c01c9a972bdf085ff8a6e71ea234fd41", 63_511_038L,
                "2f7f885ad5a0aad802e3cc24e4f57239febdcb142b4876de5d238094674361cc"));
    }

    /** Une voix telechargeable : son modele neuronal et sa description. */
    public record Voice(String id, String label, String path,
                        String modelSha256, long modelBytes, String configSha256) {

        public String modelUrl() {
            return VOICE_BASE + path + ".onnx";
        }

        public String configUrl() {
            return VOICE_BASE + path + ".onnx.json";
        }
    }

    /** Une synthese ne doit jamais retenir le launcher indefiniment. */
    private static final int SYNTHESIS_TIMEOUT_SECONDS = 45;

    /* ------------------------------------------------------------------ */
    /* Emplacements                                                        */
    /* ------------------------------------------------------------------ */

    /** Dossier de la voix neuronale, a cote des autres donnees du launcher. */
    private Path root() {
        return LauncherPaths.launcherDir().resolve("voice-neural");
    }

    private Path engineExecutable() {
        return root().resolve("piper").resolve("piper.exe");
    }

    private Path modelFile(Voice voice) {
        return root().resolve(voice.id() + ".onnx");
    }

    private Path configFile(Voice voice) {
        return root().resolve(voice.id() + ".onnx.json");
    }

    /* ------------------------------------------------------------------ */
    /* Etat                                                                */
    /* ------------------------------------------------------------------ */

    /** Le moteur ne fonctionne que sous Windows en 64 bits. */
    public boolean isSupported() {
        return OsUtil.isWindows() && "64".equals(OsUtil.archBits());
    }

    /** Vrai si le moteur et cette voix sont installes et pretes. */
    public boolean isReady(String voiceId) {
        Voice voice = VOICES.get(voiceId);
        return voice != null
                && Files.isRegularFile(engineExecutable())
                && Files.isRegularFile(modelFile(voice))
                && Files.isRegularFile(configFile(voice));
    }

    /** Poids total a telecharger pour cette voix, moteur compris s'il manque. */
    public long downloadSize(String voiceId) {
        Voice voice = VOICES.get(voiceId);
        if (voice == null) {
            return 0;
        }
        long total = voice.modelBytes();
        if (!Files.isRegularFile(engineExecutable())) {
            total += ENGINE_BYTES;
        }
        return total;
    }

    /* ------------------------------------------------------------------ */
    /* Installation                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Telecharge et installe le moteur puis la voix demandee.
     *
     * <p>Rien n'est execute avant d'avoir ete verifie : un fichier dont l'empreinte ne
     * correspond pas est efface et l'installation s'arrete.</p>
     *
     * @param voiceId identifiant d'une voix du catalogue
     * @param onProgress avancement, appele depuis le fil courant
     */
    public void install(String voiceId, Consumer<Progress> onProgress) throws IOException {
        Voice voice = VOICES.get(voiceId);
        if (voice == null) {
            throw new IOException("Voix inconnue : " + voiceId);
        }
        if (!isSupported()) {
            throw new IOException("La voix neuronale demande Windows 64 bits.");
        }
        Files.createDirectories(root());

        if (!Files.isRegularFile(engineExecutable())) {
            installEngine(onProgress);
        }
        if (!Files.isRegularFile(modelFile(voice))) {
            installVoice(voice, onProgress);
        }
        Log.info("Voix neuronale prete : " + voice.label());
    }

    private void installEngine(Consumer<Progress> onProgress) throws IOException {
        Path archive = root().resolve("piper-engine.zip");
        fetch(ENGINE_URL, archive, ENGINE_SHA256, ENGINE_BYTES,
                "Moteur de synthese neuronale", onProgress);

        onProgress.accept(Progress.indeterminate("Installation du moteur"));
        Zips.extract(archive, root(), List.of());
        Files.deleteIfExists(archive);

        if (!Files.isRegularFile(engineExecutable())) {
            throw new IOException("Le moteur ne contient pas piper.exe");
        }
        Log.info("Moteur de synthese neuronale installe");
    }

    private void installVoice(Voice voice, Consumer<Progress> onProgress) throws IOException {
        fetch(voice.modelUrl(), modelFile(voice), voice.modelSha256(), voice.modelBytes(),
                "Voix " + voice.label(), onProgress);
        fetch(voice.configUrl(), configFile(voice), voice.configSha256(), 0,
                "Description de la voix", onProgress);
    }

    /**
     * Telecharge un fichier et refuse de le garder si son empreinte ne correspond pas.
     *
     * <p>C'est la seule barriere qui compte : le contenu telecharge finira execute ou
     * charge par le moteur, il ne doit donc jamais differer de ce qui a ete verifie.</p>
     */
    private void fetch(String url, Path target, String expectedSha256, long expectedBytes,
                       String label, Consumer<Progress> onProgress) throws IOException {
        Safety.requireSecureUrl(url, label);
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(temporary);

        onProgress.accept(Progress.indeterminate(label));
        Http.download(url, temporary, received -> {
            if (expectedBytes > 0) {
                onProgress.accept(Progress.of(label,
                        received / 1_048_576 + " / " + expectedBytes / 1_048_576 + " Mo",
                        Math.min(1.0, (double) received / expectedBytes)));
            }
        });

        onProgress.accept(Progress.indeterminate("Verification de " + label));
        String actual = Hashing.sha256(temporary);
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            Files.deleteIfExists(temporary);
            throw new IOException("Empreinte inattendue pour " + label
                    + " : le fichier telecharge a ete refuse.");
        }
        Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Safety.restrictToOwner(target);
    }

    /** Efface moteur et voix : environ quatre-vingts megaoctets recuperes. */
    public void uninstall() {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Un fichier verrouille sera repris au prochain nettoyage.
                }
            });
            Log.info("Voix neuronale desinstallee");
        } catch (IOException e) {
            Log.warn("Desinstallation incomplete de la voix neuronale : " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Synthese                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Prononce une phrase dans un fichier WAV.
     *
     * <p>Le texte est ecrit sur l'entree standard du moteur : il ne figure ni dans la
     * ligne de commande, ni dans un script, et ne peut donc pas etre interprete comme
     * autre chose que du texte a lire.</p>
     */
    public void synthesise(String phrase, String voiceId, Path output) throws IOException {
        Voice voice = VOICES.get(voiceId);
        if (voice == null || !isReady(voiceId)) {
            throw new IOException("Voix neuronale indisponible");
        }
        Files.createDirectories(output.getParent());

        ProcessBuilder builder = new ProcessBuilder(
                engineExecutable().toString(),
                "--model", modelFile(voice).toString(),
                "--config", configFile(voice).toString(),
                "--output_file", output.toString());
        builder.directory(engineExecutable().getParent().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(phrase.getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
        }
        try {
            if (!process.waitFor(SYNTHESIS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Synthese neuronale trop longue");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Synthese interrompue");
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(output)
                || Files.size(output) == 0) {
            Files.deleteIfExists(output);
            throw new IOException("Le moteur neuronal n'a produit aucun son");
        }
    }
}
