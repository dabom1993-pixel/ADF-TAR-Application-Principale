package fr.adftar.principale

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import java.security.MessageDigest

/** Une application affichée dans la fenêtre principale. */
data class AppCible(
    val nom: String,
    val packageName: String,
    /** Lien de téléchargement de l'APK quand l'application n'est pas sur le Play Store. */
    val telechargement: String?,
    /** Version bêta, installée à côté de la finale (autre identifiant de paquet). */
    val beta: AppCible? = null
)

class MainActivity : Activity() {

    private lateinit var apps: List<AppCible>
    private lateinit var appsBeta: List<AppCible>
    private val filtreGris = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apps = chargerApplications()
        appsBeta = apps.mapNotNull { it.beta }
        findViewById<View>(R.id.texte_vide).visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        afficherBeta(prefsBeta().getBoolean(CLE_BETA, false))

        val version = findViewById<TextView>(R.id.texte_version)
        version.text = getString(R.string.version, BuildConfig.VERSION_NAME)
        surAppuiLong(version, DUREE_APPUI_BETA_MS) { basculerBeta() }
        findViewById<View>(R.id.logo).setOnClickListener { verifierMiseAJour() }
        signalerSiMiseAJourInstallee()
    }

    /** Déclenche [action] quand [vue] reste appuyée pendant [dureeMs]. */
    private fun surAppuiLong(vue: View, dureeMs: Long, action: () -> Unit) {
        val declencheur = Runnable { action() }
        vue.setOnTouchListener { v, evenement ->
            when (evenement.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.postDelayed(declencheur, dureeMs)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.removeCallbacks(declencheur)
            }
            true
        }
    }

    private fun prefsBeta() = getSharedPreferences("beta", MODE_PRIVATE)

    private fun afficherBeta(visible: Boolean) {
        prefsBeta().edit().putBoolean(CLE_BETA, visible).apply()
        findViewById<View>(R.id.section_beta).visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Appui long sur la version : mot de passe pour afficher les bêtas, ou les masquer. */
    private fun basculerBeta() {
        if (prefsBeta().getBoolean(CLE_BETA, false)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.beta_masquer)
                .setPositiveButton(R.string.masquer) { _, _ -> afficherBeta(false) }
                .setNegativeButton(R.string.annuler, null)
                .show()
            return
        }
        val saisie = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val cadre = FrameLayout(this).apply {
            val marge = (24 * resources.displayMetrics.density).toInt()
            setPadding(marge, marge / 2, marge, 0)
            addView(saisie)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.beta_mot_de_passe)
            .setView(cadre)
            .setPositiveButton(R.string.valider) { _, _ ->
                if (empreinte(saisie.text.toString()) == EMPREINTE_MOT_DE_PASSE_BETA) {
                    afficherBeta(true)
                    Toast.makeText(this, R.string.beta_activees, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.beta_mauvais_mot_de_passe, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.annuler, null)
            .show()
    }

    private fun empreinte(texte: String): String =
        MessageDigest.getInstance("SHA-256").digest(texte.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Affiche un message au premier lancement qui suit une mise à jour. */
    private fun signalerSiMiseAJourInstallee() {
        val prefs = getSharedPreferences("maj", MODE_PRIVATE)
        val precedente = prefs.getInt("version_lancee", 0)
        if (precedente != 0 && BuildConfig.VERSION_CODE > precedente) {
            Toast.makeText(
                this, getString(R.string.maj_installee, BuildConfig.VERSION_NAME), Toast.LENGTH_LONG
            ).show()
        }
        prefs.edit().putInt("version_lancee", BuildConfig.VERSION_CODE).apply()
    }

    /** Sans cette autorisation, Samsung bloque l'installation : on ouvre le réglage. */
    private fun peutInstaller(): Boolean {
        if (packageManager.canRequestPackageInstalls()) return true
        Toast.makeText(this, R.string.maj_autorisation, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
        )
        return false
    }

    /** Une application qui peut être mise à jour (ADF TAR elle-même si [estAdfTar]). */
    private class Candidat(
        val nom: String,
        val icone: Drawable?,
        val url: String,
        val fichier: String,
        val disponible: Boolean,
        val estAdfTar: Boolean = false
    )

    /** Mises à jour choisies, installées l'une après l'autre (ADF TAR en dernier). */
    private val fileMisesAJour = ArrayDeque<Candidat>()

    /**
     * Appui sur le logo : vérifie ADF TAR et chaque application installée (finales et
     * bêtas), puis propose la liste des mises à jour à cocher.
     */
    private fun verifierMiseAJour() {
        if (!peutInstaller()) return
        val dialogue = DialogueTelechargement(getString(R.string.maj_titre), getString(R.string.maj_recherche))
        Thread {
            val candidats = try {
                rechercherMisesAJour()
            } catch (e: Exception) {
                null
            }
            when {
                candidats == null -> dialogue.fermer(R.string.maj_erreur)
                candidats.none { it.disponible } -> dialogue.fermer(R.string.maj_aucune)
                else -> {
                    dialogue.fermer()
                    runOnUiThread { choisirMisesAJour(candidats) }
                }
            }
        }.start()
    }

    /** Accès réseau : à appeler hors du thread principal. */
    private fun rechercherMisesAJour(): List<Candidat> {
        val publiee = MiseAJour.versionPubliee() ?: throw IllegalStateException("version.txt")
        val candidats = mutableListOf(
            Candidat(
                getString(R.string.app_name),
                getDrawable(R.mipmap.ic_launcher),
                MiseAJour.URL_APK,
                "ADF-TAR.apk",
                publiee > BuildConfig.VERSION_CODE,
                estAdfTar = true
            )
        )
        for (app in apps + appsBeta) {
            val url = app.telechargement ?: continue
            // Seules les applications déjà installées sont mises à jour.
            val paquet = try {
                packageManager.getPackageInfo(app.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
            // Même référence que l'application elle-même : le numéro de version de l'APK
            // installé, comparé au version.txt publié à côté de l'APK.
            val publiee = MiseAJour.versionPublieeApk(url)
            val disponible = if (publiee != null) {
                publiee > PackageInfoCompat.getLongVersionCode(paquet)
            } else {
                // Ancienne release sans version.txt : date de l'APK contre date d'installation.
                val publication = MiseAJour.datePublication(url)
                publication != null && publication > paquet.lastUpdateTime
            }
            candidats += Candidat(app.nom, icone(app), url, "${app.packageName}.apk", disponible)
        }
        return candidats
    }

    /** Fenêtre listant chaque application, avec une case à cocher pour sa mise à jour. */
    private fun choisirMisesAJour(candidats: List<Candidat>) {
        val marge = (24 * resources.displayMetrics.density).toInt()
        val liste = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(marge, marge / 2, marge, 0)
        }
        val cases = candidats.map { candidat ->
            val ligne = layoutInflater.inflate(R.layout.item_mise_a_jour, liste, false)
            ligne.findViewById<ImageView>(R.id.maj_icone).setImageDrawable(candidat.icone)
            ligne.findViewById<TextView>(R.id.maj_nom).text = candidat.nom
            ligne.findViewById<TextView>(R.id.maj_statut).apply {
                setText(if (candidat.disponible) R.string.maj_statut_disponible else R.string.maj_statut_a_jour)
                setTextColor(getColor(if (candidat.disponible) R.color.beta else R.color.texte_secondaire))
            }
            val case = ligne.findViewById<CheckBox>(R.id.maj_case).apply {
                isChecked = candidat.disponible
                isEnabled = candidat.disponible
            }
            if (candidat.disponible) ligne.setOnClickListener { case.toggle() }
            liste.addView(ligne)
            candidat to case
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.maj_choix_titre)
            .setView(ScrollView(this).apply { addView(liste) })
            .setPositiveButton(R.string.maj_valider) { _, _ ->
                fileMisesAJour.clear()
                // ADF TAR en dernier : son installation ferme l'application.
                fileMisesAJour.addAll(cases.filter { it.second.isChecked }.map { it.first }.sortedBy { it.estAdfTar })
                installerSuivante()
            }
            .setNegativeButton(R.string.annuler, null)
            .show()
    }

    /** Télécharge et installe la mise à jour suivante ; reprend au retour de l'installeur. */
    private fun installerSuivante() {
        val candidat = fileMisesAJour.removeFirstOrNull() ?: run {
            remplirLigne(findViewById(R.id.ligne_apps), apps)
            remplirLigne(findViewById(R.id.ligne_beta), appsBeta)
            return
        }
        val dialogue = DialogueTelechargement(
            getString(R.string.installation_titre, candidat.nom),
            getString(R.string.installation_progression, candidat.nom, 0)
        )
        Thread {
            try {
                val apk = MiseAJour.telecharger(this, candidat.url, candidat.fichier) {
                    dialogue.progression(getString(R.string.installation_progression, candidat.nom, it), it)
                }
                dialogue.installer(apk, DEMANDE_INSTALLATION)
            } catch (e: Exception) {
                dialogue.fermer(R.string.installation_erreur)
                runOnUiThread { installerSuivante() }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Installation acceptée ou refusée : on passe à la suivante.
        if (requestCode == DEMANDE_INSTALLATION) installerSuivante()
    }

    /** Tuile d'une application absente : téléchargement dans l'application puis installation. */
    private fun installer(app: AppCible, url: String) {
        if (!peutInstaller()) return
        val dialogue = DialogueTelechargement(
            getString(R.string.installation_titre, app.nom),
            getString(R.string.installation_progression, app.nom, 0)
        )
        Thread {
            try {
                val apk = MiseAJour.telecharger(this, url, "${app.packageName}.apk") {
                    dialogue.progression(getString(R.string.installation_progression, app.nom, it), it)
                }
                dialogue.installer(apk)
            } catch (e: Exception) {
                dialogue.fermer(R.string.installation_erreur)
            }
        }.start()
    }

    /** Fenêtre avec barre de progression, pilotable depuis un thread de téléchargement. */
    private inner class DialogueTelechargement(titre: String, messageInitial: String) {
        private val vue = layoutInflater.inflate(R.layout.dialog_mise_a_jour, null)
        private val texte = vue.findViewById<TextView>(R.id.texte_maj)
        private val barre = vue.findViewById<ProgressBar>(R.id.progression_maj)
        private val dialogue = AlertDialog.Builder(this@MainActivity)
            .setTitle(titre)
            .setView(vue)
            .setCancelable(false)
            .show()

        init {
            texte.text = messageInitial
        }

        fun progression(message: String, pourcent: Int) = runOnUiThread {
            barre.isIndeterminate = false
            barre.progress = pourcent
            texte.text = message
        }

        fun fermer(message: Int? = null) = runOnUiThread {
            dialogue.dismiss()
            if (message != null) Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }

        /** Lance l'installeur ; avec [demande], le résultat revient dans onActivityResult. */
        fun installer(apk: java.io.File, demande: Int? = null) = runOnUiThread {
            dialogue.dismiss()
            val intent = MiseAJour.intentInstallation(this@MainActivity, apk)
            if (demande != null) startActivityForResult(intent, demande) else startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Rafraîchit l'état "installée / non installée" au retour dans l'application.
        remplirLigne(findViewById(R.id.ligne_apps), apps)
        remplirLigne(findViewById(R.id.ligne_beta), appsBeta)
    }

    /** Lit la liste des applications depuis assets/applications.json. */
    private fun chargerApplications(): List<AppCible> {
        val json = assets.open("applications.json").bufferedReader().use { it.readText() }
        val tableau = JSONArray(json)
        return (0 until tableau.length()).map { i ->
            val obj = tableau.getJSONObject(i)
            val nom = obj.getString("nom")
            val beta = obj.optJSONObject("beta")?.let {
                AppCible(
                    getString(R.string.beta_nom, nom),
                    it.getString("package"),
                    it.optString("telechargement").ifBlank { null }
                )
            }
            AppCible(
                nom,
                obj.getString("package"),
                obj.optString("telechargement").ifBlank { null },
                beta
            )
        }
    }

    private fun estInstallee(app: AppCible): Boolean =
        packageManager.getLaunchIntentForPackage(app.packageName) != null

    private fun ouvrir(app: AppCible) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        }
        // Application absente : l'installer directement (APK), sinon via le Play Store.
        if (app.telechargement != null) {
            installer(app, app.telechargement)
            return
        }
        Toast.makeText(this, getString(R.string.app_non_installee, app.nom), Toast.LENGTH_SHORT).show()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")
                )
            )
        }
    }

    private fun icone(app: AppCible): Drawable? = try {
        packageManager.getApplicationIcon(app.packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Remplit une ligne de tuiles. La ligne a une hauteur fixe (part de l'écran) et ne
     * défile pas : les tuiles se partagent sa largeur, et l'icône s'adapte à la place
     * disponible. On réserve au moins [CASES_PAR_LIGNE] cases pour que les tuiles gardent
     * une taille raisonnable quand il y a peu d'applications.
     */
    private fun remplirLigne(ligne: LinearLayout, liste: List<AppCible>) {
        ligne.removeAllViews()
        val marge = (8 * resources.displayMetrics.density).toInt()
        for (i in 0 until maxOf(CASES_PAR_LIGNE, liste.size)) {
            val app = liste.getOrNull(i)
            val tuile = if (app == null) View(this) else creerTuile(ligne, app)
            ligne.addView(tuile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(marge, marge, marge, marge)
            })
        }
    }

    private fun creerTuile(parent: ViewGroup, app: AppCible): View {
        val vue = layoutInflater.inflate(R.layout.item_app, parent, false)
        val installee = estInstallee(app)
        val image = vue.findViewById<ImageView>(R.id.icone_app)
        image.setImageDrawable(icone(app) ?: getDrawable(R.drawable.ic_app_absente))
        image.colorFilter = if (installee) null else filtreGris
        vue.findViewById<TextView>(R.id.nom_app).text = app.nom
        vue.alpha = if (installee) 1f else 0.5f
        vue.setOnClickListener { ouvrir(app) }
        return vue
    }

    private companion object {
        const val CLE_BETA = "beta_visible"
        const val DUREE_APPUI_BETA_MS = 5_000L
        const val CASES_PAR_LIGNE = 5
        const val DEMANDE_INSTALLATION = 1

        /** Empreinte SHA-256 du mot de passe : le mot de passe lui-même n'est pas dans le code. */
        const val EMPREINTE_MOT_DE_PASSE_BETA =
            "62800fcd73f34e5e45b78eab27aed58084e1a363dfd802616dfbc40668c195b7"
    }
}
