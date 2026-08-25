package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.Account;
import com.minicube.launcher.model.Cape;
import com.minicube.launcher.ui.component.DefaultSkin;
import com.minicube.launcher.ui.view.SkinView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Controleur de l'onglet Skin : import d'une texture, envoi vers Mojang et gestion
 * des capes.
 */
public class SkinController {

    private final LauncherContext context;
    private final SkinView view = new SkinView();
    private final Window owner;

    private Path pendingSkinFile;

    public SkinController(LauncherContext context, Window owner) {
        this.context = context;
        this.owner = owner;

        view.importButton().setOnAction(event -> chooseSkinFile());
        view.applyButton().setOnAction(event -> applySkin());
        view.refreshButton().setOnAction(event -> reloadFromAccount());
        view.resetViewButton().setOnAction(event -> view.viewer().resetView());
        view.autoRotate().selectedProperty().addListener(
                (observable, oldValue, newValue) -> view.viewer().setAutoRotate(newValue));
        view.classicModel().selectedProperty().addListener(
                (observable, oldValue, selected) -> updateModelVariant());
        view.applyCapeButton().setOnAction(event -> applyCape());
        view.removeCapeButton().setOnAction(event -> removeCape());

        context.accounts().addChangeListener(account -> reloadFromAccount());
        reloadFromAccount();
    }

    public Node root() {
        return view.root();
    }

    /** Libere le minuteur d'animation de la vue 3D. */
    public void dispose() {
        view.viewer().dispose();
    }

    /* ------------------------------------------------------------------ */
    /* Chargement                                                          */
    /* ------------------------------------------------------------------ */

    /** Recharge le skin et les capes du compte actif. */
    public final void reloadFromAccount() {
        Account account = context.accounts().active().orElse(null);
        if (account == null) {
            view.viewer().setSkin(DefaultSkin.image(), false);
            view.warningLabel().setText(I18n.tr("skin.noAccount"));
            setControlsEnabled(false);
            // Sans aucun compte, il n'y a rien a quoi rattacher une texture.
            view.applyButton().setDisable(true);
            return;
        }
        boolean online = !account.isOffline();
        setControlsEnabled(online);
        view.warningLabel().setText(online ? "" : I18n.tr("skin.offlineWarning"));
        view.slimModel().setSelected("slim".equals(account.getSkinModel()));
        refreshCapeList(account);

        if (account.hasLocalSkin()) {
            view.selectedFileLabel().setText(
                    Path.of(account.getLocalSkinPath()).getFileName().toString());
        }

        Fx.async(() -> {
            Path skin = context.skins().resolveDisplaySkin(account);
            Path cape = context.skins().downloadCapeTexture(account);
            return new Path[]{skin, cape};
        }, textures -> {
            Image skin = loadImage(textures[0]);
            view.viewer().setSkin(skin == null ? DefaultSkin.image() : skin,
                    view.slimModel().isSelected());
            view.viewer().setCape(loadImage(textures[1]));
        }, error -> view.viewer().setSkin(DefaultSkin.image(),
                view.slimModel().isSelected()));
    }

    private Image loadImage(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            Image image = new Image(file.toUri().toString());
            return image.isError() ? null : image;
        } catch (Exception e) {
            Log.debug("Texture illisible : " + file);
            return null;
        }
    }

    private void refreshCapeList(Account account) {
        view.capeSelector().getItems().setAll(account.getCapes());
        account.getCapes().stream()
                .filter(Cape::isActive)
                .findFirst()
                .ifPresent(cape -> view.capeSelector().getSelectionModel().select(cape));
    }

    /**
     * Ajuste les controles selon le type de compte.
     *
     * <p>Le bouton Appliquer reste actif meme hors-ligne : il applique alors la texture
     * localement. Un bouton grise ne repond a aucun clic et n'explique rien, ce qui
     * laissait l'utilisateur devant une impasse apres avoir importe son skin.</p>
     *
     * <p>Les capes, elles, n'existent que du cote de Mojang : leurs controles restent
     * desactives sans compte Microsoft.</p>
     *
     * @param microsoft true si le compte actif est authentifie chez Microsoft
     */
    private void setControlsEnabled(boolean microsoft) {
        view.applyButton().setDisable(false);
        view.capeSelector().setDisable(!microsoft);
        view.applyCapeButton().setDisable(!microsoft);
        view.removeCapeButton().setDisable(!microsoft);
    }

    /* ------------------------------------------------------------------ */
    /* Actions                                                             */
    /* ------------------------------------------------------------------ */

    /** Ouvre un selecteur de fichier et pre-visualise immediatement le skin choisi. */
    private void chooseSkinFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.tr("skin.choose"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images PNG", "*.png"));
        java.io.File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        try {
            pendingSkinFile = context.skins().importSkin(file.toPath());
            view.selectedFileLabel().setText(pendingSkinFile.getFileName().toString());
            Image preview = loadImage(pendingSkinFile);
            if (preview != null) {
                view.viewer().setSkin(preview, view.slimModel().isSelected());
            }
            context.notifications().info(I18n.tr("skin.title"), I18n.tr("skin.imported"));
        } catch (Exception e) {
            context.notifications().error(I18n.tr("skin.invalid"), e.getMessage());
        }
    }

    /** Envoie le skin selectionne vers le compte Microsoft. */
    private void applySkin() {
        Account account = context.accounts().active().orElse(null);
        if (account == null || pendingSkinFile == null) {
            context.notifications().warning(I18n.tr("skin.title"), I18n.tr("skin.selectFirst"));
            return;
        }
        if (account.isOffline()) {
            applyLocally(account);
            return;
        }
        boolean slim = view.slimModel().isSelected();
        view.applyButton().setDisable(true);

        Fx.async(() -> {
            context.skins().uploadSkin(account, pendingSkinFile, slim);
            // La texture est aussi retenue localement : l'apercu reste juste meme si
            // les serveurs de Mojang mettent un moment a diffuser la nouvelle image.
            context.skins().applyLocalSkin(account, pendingSkinFile);
            return Boolean.TRUE;
        }, ignored -> {
            view.applyButton().setDisable(false);
            account.setSkinModel(slim ? "slim" : "classic");
            context.accounts().save();
            context.notifications().success(I18n.tr("skin.title"), I18n.tr("skin.applied"));
        }, error -> {
            view.applyButton().setDisable(false);
            context.notifications().error(I18n.tr("skin.title"), error.getMessage());
        });
    }

    /**
     * Rattache la texture au compte hors-ligne, sur cette machine.
     *
     * <p>Aucun appel reseau n'est possible ici : l'operation est immediate et ne
     * necessite pas de tache de fond.</p>
     */
    private void applyLocally(Account account) {
        try {
            context.skins().applyLocalSkin(account, pendingSkinFile);
            account.setSkinModel(view.slimModel().isSelected() ? "slim" : "classic");
            context.accounts().save();
            context.notifications().success(I18n.tr("skin.title"),
                    I18n.tr("skin.appliedLocally"));
        } catch (Exception e) {
            context.notifications().error(I18n.tr("skin.title"), e.getMessage());
        }
    }

    /** Bascule entre le modele classique et le modele a bras fins. */
    private void updateModelVariant() {
        boolean slim = view.slimModel().isSelected();
        Image current = pendingSkinFile != null ? loadImage(pendingSkinFile) : null;
        if (current != null) {
            view.viewer().setSkin(current, slim);
            return;
        }
        Account account = context.accounts().active().orElse(null);
        if (account == null) {
            view.viewer().setSkin(DefaultSkin.image(), slim);
            return;
        }
        Fx.async(() -> context.skins().downloadSkinTexture(account),
                path -> {
                    Image image = loadImage(path);
                    view.viewer().setSkin(image == null ? DefaultSkin.image() : image, slim);
                },
                error -> view.viewer().setSkin(DefaultSkin.image(), slim));
    }

    private void applyCape() {
        Account account = context.accounts().active().orElse(null);
        Cape cape = view.capeSelector().getSelectionModel().getSelectedItem();
        if (account == null || cape == null) {
            return;
        }
        Fx.async(() -> {
            context.skins().activateCape(account, cape.getId());
            return Boolean.TRUE;
        }, ignored -> {
            context.accounts().save();
            reloadFromAccount();
            context.notifications().success(I18n.tr("skin.title"), I18n.tr("skin.cape.applied"));
        }, error -> context.notifications().error(I18n.tr("skin.title"), error.getMessage()));
    }

    private void removeCape() {
        Account account = context.accounts().active().orElse(null);
        if (account == null) {
            return;
        }
        Fx.async(() -> {
            context.skins().disableCape(account);
            return Boolean.TRUE;
        }, ignored -> {
            context.accounts().save();
            reloadFromAccount();
            context.notifications().info(I18n.tr("skin.title"), I18n.tr("skin.cape.removed"));
        }, error -> context.notifications().error(I18n.tr("skin.title"), error.getMessage()));
    }
}
