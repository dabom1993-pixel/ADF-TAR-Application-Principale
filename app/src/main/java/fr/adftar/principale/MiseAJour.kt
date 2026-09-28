package fr.adftar.principale

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mises à jour depuis la release GitHub à tag fixe "tablette-latest" du dépôt public
 * ADF-TAR-Versions, republiée à chaque compilation (voir .github/workflows/build-apk.yml).
 * Le code source reste privé ; seul l'APK est public, donc aucun compte n'est nécessaire.
 *
 * Ces fonctions font des accès réseau : à appeler hors du thread principal.
 */
object MiseAJour {

    private const val BASE =
        "https://github.com/dabom1993-pixel/ADF-TAR-Versions/releases/download/tablette-latest"
    private const val URL_VERSION = "$BASE/version.txt"
    private const val URL_APK = "$BASE/ADF-TAR.apk"

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

    /** Télécharge l'APK dans le stockage privé de l'application, progression de 0 à 100. */
    fun telecharger(context: Context, progression: (Int) -> Unit): File {
        val dossier = File(context.filesDir, "maj").apply { mkdirs() }
        val apk = File(dossier, "ADF-TAR.apk")
        val conn = ouvrir(URL_APK)
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

    /** Intent de l'installeur Android (la confirmation système reste obligatoire). */
    fun intentInstallation(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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
