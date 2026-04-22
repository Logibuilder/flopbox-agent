package univ.flopbox.service;

import univ.flopbox.model.Server;

import java.io.File;

public class SyncService {

    private static final  String ROOT_SYNC_DIR = "flopbox_data";

    public String createServerDirectory(Server server) {
        String host = server.host();
        File dir = new File(ROOT_SYNC_DIR, host);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                System.out.println("[LOCAL] Dossier créé pour le serveur : " + host);
            } else {
                System.err.println("[ERREUR] Impossible de créer le dossier pour : " + host);
            }
        }


        return dir.getAbsolutePath();
    }
}
