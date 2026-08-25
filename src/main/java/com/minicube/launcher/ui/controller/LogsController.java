package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.ui.view.LogsView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.function.Consumer;

/**
 * Controleur de l'onglet Journal : branche la console sur le systeme de journalisation
 * et suit les nouvelles lignes en direct.
 */
public class LogsController {

    private final LauncherContext context;
    private final LogsView view = new LogsView();
    private final Consumer<Log.Entry> listener;

    public LogsController(LauncherContext context) {
        this.context = context;

        view.showDebug().setSelected(context.config().settings().isDebugMode());
        view.showDebug().selectedProperty().addListener((obs, old, enabled) -> {
            Log.setDebugEnabled(enabled);
            context.config().settings().setDebugMode(enabled);
            context.config().save();
            reload();
        });
        view.clearButton().setOnAction(event -> {
            Log.clearBuffer();
            view.setContent("");
        });
        view.copyButton().setOnAction(event -> copyToClipboard());
        view.openFolderButton().setOnAction(event -> OsUtil.openFolder(LauncherPaths.logsDir()));

        // Les lignes arrivent depuis des threads de fond : le passage sur le thread
        // JavaFX est indispensable avant de toucher a la console.
        listener = entry -> {
            if (isVisible(entry)) {
                Fx.ui(() -> view.appendLine(entry.format()));
            }
        };
        Log.addListener(listener);

        reload();
    }

    public Node root() {
        return view.root();
    }

    /** Detache l'abonnement au journal. */
    public void dispose() {
        Log.removeListener(listener);
    }

    private boolean isVisible(Log.Entry entry) {
        return view.showDebug().isSelected() || entry.level() != Log.Level.DEBUG;
    }

    /** Recharge la console depuis le tampon memoire. */
    private void reload() {
        StringBuilder builder = new StringBuilder();
        for (Log.Entry entry : Log.snapshot()) {
            if (isVisible(entry)) {
                builder.append(entry.format()).append(System.lineSeparator());
            }
        }
        view.setContent(builder.toString());
    }

    private void copyToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString(view.content());
        Clipboard.getSystemClipboard().setContent(content);
        context.notifications().info(I18n.tr("logs.title"), I18n.tr("logs.copied"));
    }
}
