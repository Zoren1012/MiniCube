package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.model.ServerStatus;
import com.minicube.launcher.service.OptimizationService;
import com.minicube.launcher.service.PerformanceService;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.view.PerformanceView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Controleur de l'onglet Performances.
 *
 * <p>Deux rythmes cohabitent : la cadence d'affichage se mesure a chaque image, tandis
 * que les compteurs du systeme ne sont relus qu'une fois par seconde. Interroger le
 * systeme a chaque image couterait plus cher que ce qu'il mesure.</p>
 */
public class PerformanceController {

    /** Intervalle de rafraichissement des compteurs systeme. */
    private static final Duration SAMPLE_INTERVAL = Duration.seconds(1);

    private final LauncherContext context;
    private final PerformanceView view = new PerformanceView();
    private final Supplier<String> selectedVersion;

    private final Timeline sampler;
    private final AnimationTimer frameCounter;

    private long frameWindowStart;
    private int framesInWindow;

    private final List<Runnable> pendingActions = new ArrayList<>();

    public PerformanceController(LauncherContext context, Supplier<String> selectedVersion) {
        this.context = context;
        this.selectedVersion = selectedVersion;

        view.analyseButton().setOnAction(event -> analyse());
        view.applyAllButton().setOnAction(event -> applyAll());

        frameCounter = new AnimationTimer() {
            @Override
            public void handle(long now) {
                countFrame(now);
            }
        };
        sampler = new Timeline(new KeyFrame(SAMPLE_INTERVAL, event -> refreshMetrics()));
        sampler.setCycleCount(Timeline.INDEFINITE);

        start();
        followVisibility();
        // Pas de releve synchrone ici : l'onglet doit s'ouvrir instantanement. La
        // premiere valeur arrive du fil de mesure, une seconde plus tard.
        refreshServers();
        analyse();
    }

    public Node root() {
        return view.root();
    }

    /**
     * Suspend les mesures des que l'onglet quitte l'ecran.
     *
     * <p>Changer d'onglet retire la page de la scene : c'est le signal le plus sur, et il
     * evite d'entretenir un fil de mesure et un compteur d'images pour une page que
     * personne ne regarde.</p>
     */
    private void followVisibility() {
        view.root().sceneProperty().addListener((observable, before, scene) -> {
            if (scene == null) {
                pause();
            } else {
                start();
            }
        });
    }

    /** Demarre les mesures. */
    public final void start() {
        frameWindowStart = 0;
        framesInWindow = 0;
        frameCounter.start();
        sampler.play();
        context.performance().startSampling();
    }

    /** Arrete les mesures sans liberer l'onglet, qui sera reaffiche tel quel. */
    private void pause() {
        frameCounter.stop();
        sampler.stop();
        context.performance().stopSampling();
    }

    /** Arrete les mesures : rien ne doit tourner pour un onglet qu'on ne regarde pas. */
    public void dispose() {
        frameCounter.stop();
        sampler.stop();
        context.performance().stopSampling();
    }

    /* ------------------------------------------------------------------ */
    /* Mesures                                                             */
    /* ------------------------------------------------------------------ */

    /** Compte les images sur des fenetres d'une seconde. */
    private void countFrame(long now) {
        if (frameWindowStart == 0) {
            frameWindowStart = now;
            return;
        }
        framesInWindow++;
        long elapsed = now - frameWindowStart;
        if (elapsed >= 1_000_000_000L) {
            double fps = framesInWindow / (elapsed / 1_000_000_000d);
            context.performance().reportFrameRate(fps);
            frameWindowStart = now;
            framesInWindow = 0;
        }
    }

    private void refreshMetrics() {
        PerformanceService.Snapshot snapshot = context.performance().sample();
        view.setMetric("fps", snapshot.framesLabel());
        view.setMetric("cpu", snapshot.launcherCpuLabel());
        view.setMetric("ram", snapshot.heapLabel());
        view.setMetric("system", snapshot.systemMemoryLabel());
        view.setMetric("java", snapshot.javaVersion());
        view.setMetric("startup", snapshot.gameStartupLabel());
    }

    /* ------------------------------------------------------------------ */
    /* Analyse                                                             */
    /* ------------------------------------------------------------------ */

    private void analyse() {
        view.analyseButton().setDisable(true);
        view.findingsStatus().setText(I18n.tr("perf.analysing"));
        pendingActions.clear();

        Fx.async(() -> context.optimization().analyse(selectedVersion.get()), findings -> {
            view.analyseButton().setDisable(false);
            showFindings(findings);
        }, error -> {
            view.analyseButton().setDisable(false);
            view.findingsStatus().setText(error.getMessage());
        });
    }

    private void showFindings(List<OptimizationService.Finding> findings) {
        view.findingsBox().getChildren().clear();
        pendingActions.clear();

        long problems = findings.stream()
                .filter(finding -> finding.level() == OptimizationService.Level.PROBLEM)
                .count();
        long advice = findings.stream()
                .filter(finding -> finding.level() == OptimizationService.Level.ADVICE)
                .count();

        view.findingsStatus().setText(problems == 0 && advice == 0
                ? I18n.tr("perf.allGood")
                : I18n.tr("perf.summary", problems, advice));

        findings.forEach(finding -> view.findingsBox().getChildren().add(findingRow(finding)));

        findings.stream().filter(OptimizationService.Finding::hasAction)
                .forEach(finding -> pendingActions.add(finding.action()));
        view.applyAllButton().setDisable(pendingActions.isEmpty());
    }

    /** Une ligne de constat : pastille de gravite, texte, et correction s'il y en a une. */
    private Node findingRow(OptimizationService.Finding finding) {
        Label title = new Label(finding.title());
        title.getStyleClass().add("setting-label");

        Label detail = Ui.hint(finding.detail());
        detail.setWrapText(true);
        detail.setMaxWidth(620);

        VBox texts = new VBox(3, title, detail);

        HBox row = new HBox(12, severityDot(finding.level()), texts, Ui.growSpacer());
        row.setAlignment(Pos.CENTER_LEFT);

        if (finding.hasAction()) {
            Button apply = Ui.secondaryButton(finding.actionLabel(), Icons.CHECK);
            apply.setOnAction(event -> {
                finding.action().run();
                context.config().save();
                apply.setDisable(true);
                context.notifications().success(I18n.tr("perf.title"),
                        I18n.tr("perf.applied", finding.title()));
                analyse();
            });
            row.getChildren().add(apply);
        }
        row.getStyleClass().add("setting-row");
        return row;
    }

    private Node severityDot(OptimizationService.Level level) {
        Label dot = new Label();
        dot.getStyleClass().addAll("severity-dot", switch (level) {
            case GOOD -> "severity-good";
            case ADVICE -> "severity-advice";
            case PROBLEM -> "severity-problem";
        });
        return dot;
    }

    /** Applique toutes les corrections proposees, puis relance l'analyse. */
    private void applyAll() {
        pendingActions.forEach(Runnable::run);
        context.config().save();
        context.notifications().success(I18n.tr("perf.title"),
                I18n.tr("perf.appliedAll", pendingActions.size()));
        analyse();
    }

    /* ------------------------------------------------------------------ */
    /* Serveurs                                                            */
    /* ------------------------------------------------------------------ */

    /** Interroge les serveurs enregistres et affiche leur etat. */
    private void refreshServers() {
        view.serversBox().getChildren().setAll(Ui.hint(I18n.tr("perf.servers.loading")));
        Fx.async(() -> {
            List<ServerEntry> servers = context.serverList().loadAll();
            List<Object[]> rows = new ArrayList<>();
            for (ServerEntry server : servers) {
                rows.add(new Object[]{server, context.serverPing().ping(server)});
            }
            return rows;
        }, rows -> {
            view.serversBox().getChildren().clear();
            if (rows.isEmpty()) {
                view.serversBox().getChildren().add(Ui.hint(I18n.tr("perf.servers.none")));
                return;
            }
            rows.forEach(row -> view.serversBox().getChildren()
                    .add(serverRow((ServerEntry) row[0], (ServerStatus) row[1])));
        }, error -> view.serversBox().getChildren()
                .setAll(Ui.hint(error.getMessage())));
    }

    private Node serverRow(ServerEntry server, ServerStatus status) {
        Label name = new Label(server.getName());
        name.getStyleClass().add("setting-label");
        Label address = Ui.hint(server.getAddress());

        VBox texts = new VBox(2, name, address);

        boolean online = status != null && status.online();
        Label state = new Label(online
                ? I18n.tr("perf.servers.online", status.playersLabel(), status.pingLabel())
                : I18n.tr("perf.servers.offline"));
        state.getStyleClass().add(online ? "status-online" : "status-offline");

        HBox row = new HBox(12, texts, Ui.growSpacer(), state);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        return row;
    }
}
