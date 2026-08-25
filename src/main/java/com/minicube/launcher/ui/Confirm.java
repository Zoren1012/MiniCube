package com.minicube.launcher.ui;

import com.minicube.launcher.util.I18n;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Demande de confirmation avant une action irreversible.
 *
 * <p>Supprimer un mod ou un pack de shaders efface un fichier sur le disque, parfois
 * telecharge de longue date. Ces actions etaient jusqu'ici declenchees par un simple
 * clic sur une icone, sans retour en arriere possible.</p>
 */
public final class Confirm {

    private Confirm() {
    }

    /**
     * Affiche une demande de confirmation destructrice.
     *
     * @param owner   fenetre parente
     * @param title   titre de la boite
     * @param message question posee
     * @param action  libelle du bouton qui confirme, par exemple "Supprimer"
     * @return true si l'utilisateur a confirme
     */
    public static boolean destructive(Window owner, String title, String message,
                                      String action) {
        ButtonType confirm = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.tr("action.cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, confirm, cancel);
        alert.setHeaderText(title);
        alert.setTitle(title);
        if (owner != null) {
            alert.initOwner(owner);
        }
        // Le bouton neutre est selectionne par defaut : une validation au clavier ne
        // doit jamais supprimer quoi que ce soit par inadvertance.
        alert.getDialogPane().lookupButton(cancel).requestFocus();

        return alert.showAndWait().filter(response -> response == confirm).isPresent();
    }
}
