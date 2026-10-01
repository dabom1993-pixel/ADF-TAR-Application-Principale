package fr.adftar.principale

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mises à jour depuis la release GitHub à tag fixe "tablette-latest", republiée à chaque
 * compilation (voir .github/workflows/build-apk.yml). Le dépôt est public : aucun compte
 * n'est nécessaire.
 *
 * Ces fonctions font des accès réseau : à appeler hors du thread principal.
 */
object MiseAJour {

    private const val BASE =
        "https://github.com/dabom1993-pixel/ADF-TAR-Application-Principale/releases/download/tablette-latest"
    private const val URL_VERSION = "$BASE/version.txt"
    const val URL_APK = "$BASE/ADF-TAR.apk"

    /** Numéro de la dernière version publiée, ou null si la release est introuvable. */
    fun versionPubliee(): Int? {
        val conn = ouvrir(URL_VERSION)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            return conn.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Date de publication (ms) d'un APK, lue dans l'en-tête Last-Modified du fichier,
     * sans le télécharger. Null si le lien ne répond pas.
     */
    fun datePublication(url: String): Long? {
        val conn = ouvrir(url).apply { requestMethod = "HEAD" }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            return conn.lastModified.takeIf { it > 0 }
        } finally {
            conn.disconnect()
        }
    }

    /** Télécharge un APK dans le stockage privé de l'application, progression de 0 à 100. */
    fun telecharger(context: Context, url: String, nomFichier: String, progression: (Int) -> Unit): File {
        val dossier = File(context.filesDir, "maj").apply { mkdirs() }
        val apk = File(dossier, nomFichier)
        val conn = ouvrir(url)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { entree ->
                apk.outputStream().use { sortie ->
                    val tampon = ByteArray(8 * 1024)
                    var lu = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n == -1) break
                        sortie.write(tampon, 0, n)
                        lu += n
                        if (total > 0) progression((lu * 100 / total).toInt())
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return apk
    }

    /**
     * Intent de l'installeur Android (la confirmation système reste obligatoire). Il renvoie
     * un résultat, ce qui permet d'enchaîner plusieurs installations l'une après l'autre.
     */
    @Suppress("DEPRECATION")
    fun intentInstallation(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    private fun ouvrir(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
        // Évite qu'un cache renvoie un ancien version.txt.
        useCaches = false
    }
}
