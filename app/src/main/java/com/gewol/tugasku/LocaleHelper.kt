package com.gewol.tugasku

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * Bahasa aplikasi: ikuti sistem (bawaan), Indonesia, atau Inggris.
 * Jika bahasa HP bukan Indonesia/Inggris, aplikasi memakai bahasa Inggris.
 */
object LocaleHelper {
    private const val PREFS = "tugasku_prefs"
    private const val KEY_LANG = "app_language"

    const val SYSTEM = "system"
    const val INDONESIAN = "id"
    const val ENGLISH = "en"

    private val SUPPORTED = setOf("in", "id", "en")

    fun getSaved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, SYSTEM) ?: SYSTEM

    private fun save(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang).apply()
    }

    private fun systemLocale(): Locale =
        ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0)
            ?: Locale.getDefault()

    /**
     * Dipanggil di attachBaseContext() Activity, dan sebelum mengambil teks di
     * receiver / widget, supaya bahasa pilihan pengguna dipakai.
     * Sekaligus mengatur Locale.getDefault() agar format tanggal ikut berganti.
     */
    fun wrap(base: Context): Context {
        val locale: Locale = when (getSaved(base)) {
            INDONESIAN -> Locale.forLanguageTag("id-ID")
            ENGLISH -> Locale.ENGLISH
            else -> {
                val sys = systemLocale()
                if (sys.language in SUPPORTED) {
                    Locale.setDefault(sys)
                    return base
                }
                Locale.ENGLISH
            }
        }
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Simpan pilihan bahasa lalu muat ulang layar agar langsung berganti. */
    fun setLanguage(activity: Activity, lang: String) {
        save(activity, lang)
        activity.recreate()
    }
}
