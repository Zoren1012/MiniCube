package com.minicube.launcher.ui.dialog;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.Account;
import com.minicube.launcher.service.AccountService;
import com.minicube.launcher.service.MicrosoftAuthService;
import com.minicube.launcher.ui.Confirm;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fenetre de connexion : compte Microsoft en flux "device code" ou compte hors-ligne.
 *
 * <p>Le flux device code affiche un code court que l'utilisateur saisit sur la page
 * Microsoft ouverte dans son navigateur. Le launcher interroge Microsoft en tache de
 * fond jusqu'a la validation, sans jamais manipuler le mot de passe de l'utilisateur.</p>
 */
public class LoginDialog {

    private final LauncherContext context;
    private final Stage stage = new Stage();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final VBox accountList = new VBox(8);
    private final Label statusLabel = Ui.hint("");
    private final Label codeLabel = new Label();
    private final Button copyCodeButton = Ui.secondaryButton(I18n.tr("login.copyCode"), null);
    private final Button openBrowserButton =
            Ui.secondaryButton(I18n.tr("login.openBrowser"), null);
    private final VBox codeBox = new VBox(10);
    private final Button microsoftButton =
            Ui.primaryButton(I18n.tr("login.microsoft"), Icons.PERSON);
    private final TextField usernameField = new TextField();
    private final Button offlineButton = Ui.secondaryButton(I18n.tr("login.offline"), null);

    private Account result;

    public LoginDialog(LauncherContext context, Window owner) {
        this.context = context;

        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.tr("login.title"));
        stage.setOnCloseRequest(event -> cancelled.set(true));

        Scene scene = new Scene(buildContent(), 560, 520);
        ThemeManager.apply(scene, context.config().settings().getTheme());
        stage.setScene(scene);
    }

    /**
     * Affiche la fenetre et attend sa fermeture.
     *
     * @return le compte cree ou authentifie, ou un optional vide si l'utilisateur annule
     */
    public Optional<Account> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(result);
    }

    /** Carte listant les comptes deja enregistres, masquee lorsqu il n y en a aucun. */
    private VBox buildAccountsCard() {
        VBox card = Ui.card(I18n.tr("login.saved"), accountList);
        refreshAccountList(card);
        return card;
    }

    /**
     * Reconstruit la liste des comptes enregistres.
     *
     * <p>La carte entiere disparait quand la liste est vide : une section vide sur
     * l ecran de connexion n apporte rien et occupe de la place.</p>
     */
    private void refreshAccountList(VBox card) {
        accountList.getChildren().clear();
        var saved = context.accounts().accounts();
        boolean visible = !saved.isEmpty();
        card.setVisible(visible);
        card.setManaged(visible);
        if (!visible) {
            return;
        }
        var active = context.accounts().active().orElse(null);
        for (Account account : saved) {
            accountList.getChildren().add(buildAccountRow(account, account == active, card));
        }
    }

    /** Une ligne : identite du compte, bouton pour l activer, bouton pour l oublier. */
    private HBox buildAccountRow(Account account, boolean isActive, VBox card) {
        Label name = new Label(account.getUsername());
        name.getStyleClass().add("account-name");
        Label type = new Label(account.getType().label());
        type.getStyleClass().add("account-type");
        VBox texts = new VBox(1, name, type);

        Button use = isActive
                ? Ui.secondaryButton(I18n.tr("login.current"), Icons.CHECK)
                : Ui.primaryButton(I18n.tr("login.use"), null);
        use.setDisable(isActive);
        use.setOnAction(event -> {
            context.accounts().setActive(account);
            result = account;
            stage.close();
        });

        Button forget = Ui.iconButton(Icons.TRASH, I18n.tr("login.forget"));
        forget.setOnAction(event -> {
            if (Confirm.destructive(stage, I18n.tr("login.title"),
                    I18n.tr("login.forgetConfirm", account.getUsername()),
                    I18n.tr("login.forget"))) {
                context.accounts().remove(account);
                refreshAccountList(card);
            }
        });

        HBox row = new HBox(12, texts, Ui.growSpacer(), use, forget);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");
        row.setPadding(new Insets(10, 12, 10, 14));
        return row;
    }

    private VBox buildContent() {
        Label title = new Label(I18n.tr("login.heading"));
        title.getStyleClass().add("page-title");

        codeLabel.getStyleClass().add("device-code");
        HBox codeActions = new HBox(10, copyCodeButton, openBrowserButton);
        codeActions.setAlignment(Pos.CENTER);
        codeBox.setAlignment(Pos.CENTER);
        codeBox.getChildren().addAll(codeLabel, codeActions);
        codeBox.setVisible(false);
        codeBox.setManaged(false);

        microsoftButton.setOnAction(event -> startMicrosoftLogin());
        copyCodeButton.setOnAction(event -> copyCode());

        VBox microsoftCard = Ui.card(I18n.tr("login.microsoft.title"),
                Ui.hint(I18n.tr("login.microsoft.hint")),
                microsoftButton,
                codeBox,
                statusLabel);

        usernameField.setPromptText(I18n.tr("login.usernameHint"));
        usernameField.setPrefWidth(240);
        offlineButton.setOnAction(event -> createOfflineAccount());
        usernameField.setOnAction(event -> createOfflineAccount());

        HBox offlineRow = new HBox(12, usernameField, offlineButton);
        offlineRow.setAlignment(Pos.CENTER_LEFT);

        VBox offlineCard = Ui.card(I18n.tr("login.offline.title"),
                Ui.hint(I18n.tr("login.offline.hint")), offlineRow);

        VBox content = new VBox(18, title, buildAccountsCard(), microsoftCard, offlineCard);
        content.getStyleClass().addAll("page", "dialog-root");
        content.setPadding(new Insets(26));
        return content;
    }

    /* ------------------------------------------------------------------ */
    /* Compte Microsoft                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Demande un code d'appairage puis attend la validation de l'utilisateur.
     *
     * <p>Les deux etapes sont executees en tache de fond : l'interface reste reactive
     * et le bouton Annuler de la fenetre interrompt reellement l'attente.</p>
     */
    private void startMicrosoftLogin() {
        microsoftButton.setDisable(true);
        cancelled.set(false);
        statusLabel.setText(I18n.tr("login.requesting"));

        Fx.async(() -> context.auth().requestDeviceCode(), code -> {
            codeLabel.setText(code.userCode());
            codeBox.setVisible(true);
            codeBox.setManaged(true);
            statusLabel.setText(I18n.tr("login.enterCode", code.verificationUri()));
            openBrowserButton.setOnAction(event -> OsUtil.openUrl(code.verificationUri()));
            // Le navigateur est ouvert immediatement : l'utilisateur n'a que le code a saisir.
            OsUtil.openUrl(code.verificationUri());
            pollForAccount(code);
        }, error -> {
            microsoftButton.setDisable(false);
            statusLabel.setText(error.getMessage());
            context.notifications().error(I18n.tr("login.title"), error.getMessage());
        });
    }

    /** Interroge Microsoft jusqu'a validation, puis enregistre le compte. */
    private void pollForAccount(MicrosoftAuthService.DeviceCode code) {
        Fx.async(() -> context.auth().completeDeviceCodeFlow(code,
                message -> Fx.ui(() -> statusLabel.setText(message)),
                cancelled::get),
                account -> {
                    context.accounts().addOrReplace(account);
                    result = account;
                    context.notifications().success(I18n.tr("login.title"),
                            I18n.tr("login.welcome", account.getUsername()));
                    stage.close();
                },
                error -> {
                    microsoftButton.setDisable(false);
                    codeBox.setVisible(false);
                    codeBox.setManaged(false);
                    statusLabel.setText(error.getMessage());
                    Log.warn("Connexion Microsoft interrompue : " + error.getMessage());
                });
    }

    private void copyCode() {
        ClipboardContent content = new ClipboardContent();
        content.putString(codeLabel.getText());
        Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText(I18n.tr("login.codeCopied"));
    }

    /* ------------------------------------------------------------------ */
    /* Compte hors-ligne                                                   */
    /* ------------------------------------------------------------------ */

    /** Cree un compte local apres validation du pseudo. */
    private void createOfflineAccount() {
        String username = usernameField.getText();
        if (!AccountService.isValidUsername(username == null ? "" : username.trim())) {
            statusLabel.setText(I18n.tr("login.invalidUsername"));
            return;
        }
        try {
            result = context.accounts().createOfflineAccount(username.trim());
            context.notifications().info(I18n.tr("login.title"),
                    I18n.tr("login.welcome", result.getUsername()));
            stage.close();
        } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
        }
    }
}
