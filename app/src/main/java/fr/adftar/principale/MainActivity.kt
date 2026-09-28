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
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

/** Une application affichée dans la fenêtre principale. */
data class AppCible(
    val nom: String,
    val packageName: String,
    /** Lien de téléchargement de l'APK quand l'application n'est pas sur le Play Store. */
    val telechargement: String?
)

class MainActivity : Activity() {

    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = AppsAdapter(chargerApplications())
        val grille = findViewById<GridView>(R.id.grille_apps)
        grille.adapter = adapter
        grille.emptyView = findViewById(R.id.texte_vide)
        grille.setOnItemClickListener { _, _, position, _ ->
            ouvrir(adapter.getItem(position))
        }

        findViewById<TextView>(R.id.texte_version).text =
            getString(R.string.version, BuildConfig.VERSION_NAME)
        findViewById<View>(R.id.logo).setOnClickListener { verifierMiseAJour() }
        signalerSiMiseAJourInstallee()
    }

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

    private fun verifierMiseAJour() {
        val vue = layoutInflater.inflate(R.layout.dialog_mise_a_jour, null)
        val texte = vue.findViewById<TextView>(R.id.texte_maj)
        val barre = vue.findViewById<ProgressBar>(R.id.progression_maj)
        val dialogue = AlertDialog.Builder(this)
            .setTitle(R.string.maj_titre)
            .setView(vue)
            .setCancelable(false)
            .show()

        Thread {
            try {
                val publiee = MiseAJour.versionPubliee()
                if (publiee == null || publiee <= BuildConfig.VERSION_CODE) {
                    runOnUiThread {
                        dialogue.dismiss()
                        Toast.makeText(
                            this,
                            if (publiee == null) R.string.maj_erreur else R.string.maj_aucune,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@Thread
                }
                runOnUiThread {
                    barre.isIndeterminate = false
                    texte.text = getString(R.string.maj_telechargement, publiee, 0)
                }
                val apk = MiseAJour.telecharger(this) { pourcent ->
                    runOnUiThread {
                        barre.progress = pourcent
                        texte.text = getString(R.string.maj_telechargement, publiee, pourcent)
                    }
                }
                runOnUiThread {
                    dialogue.dismiss()
                    startActivity(MiseAJour.intentInstallation(this, apk))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialogue.dismiss()
                    Toast.makeText(this, R.string.maj_erreur, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        // Rafraîchit l'état "installée / non installée" au retour dans l'application.
        adapter.notifyDataSetChanged()
    }

    /** Lit la liste des applications depuis assets/applications.json. */
    private fun chargerApplications(): List<AppCible> {
        val json = assets.open("applications.json").bufferedReader().use { it.readText() }
        val tableau = JSONArray(json)
        return (0 until tableau.length()).map { i ->
            val obj = tableau.getJSONObject(i)
            AppCible(
                obj.getString("nom"),
                obj.getString("package"),
                obj.optString("telechargement").ifBlank { null }
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
        // Application absente : proposer de l'installer (lien direct, sinon Play Store).
        Toast.makeText(this, getString(R.string.app_non_installee, app.nom), Toast.LENGTH_SHORT).show()
        if (app.telechargement != null) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.telechargement)))
            return
        }
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

    private inner class AppsAdapter(private val apps: List<AppCible>) : BaseAdapter() {
        private val filtreGris = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vue = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            val installee = estInstallee(app)

            val image = vue.findViewById<ImageView>(R.id.icone_app)
            image.setImageDrawable(icone(app) ?: getDrawable(R.drawable.ic_app_absente))
            image.colorFilter = if (installee) null else filtreGris

            vue.findViewById<TextView>(R.id.nom_app).text = app.nom
            vue.alpha = if (installee) 1f else 0.5f
            return vue
        }
    }
}
