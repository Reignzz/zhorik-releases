package com.beamox.launcher.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

/**
 * Поиск рекламного и лишнего софта Motorola без root:
 *  1) известные пакеты (игры-«подарки», сборщики рекламы Moto, Facebook-сервисы, Amazon и т.п.);
 *  2) всё, что поставили рекламные установщики Digital Turbine / ironSource Aura (они и «спамят» играми).
 */
object Bloat {
    data class Junk(val app: AppEntry, val reason: String, val system: Boolean) { val pkg get() = app.pkg }

    private val known = mapOf(
        "com.king." to "Игра-реклама", "com.block.juggle" to "Игра-реклама", "com.playstrom." to "Игра-реклама",
        "com.candy." to "Игра-реклама", "in.playsimple." to "Игра-реклама", "com.ea.gp.fifa" to "Игра-реклама",
        "com.ea.game." to "Игра-реклама", "com.tripledot." to "Игра-реклама", "com.gamovation." to "Игра-реклама",
        "com.easybrain." to "Игра-реклама", "com.vitastudio." to "Игра-реклама", "com.mahjong" to "Игра-реклама",
        "com.dti.motorola" to "Рекламный установщик", "com.digitalturbine." to "Рекламный установщик",
        "com.ironsource.appcloud" to "Рекламный установщик", "com.aura.oobe" to "Рекламный установщик",
        "com.motorola.ccc.notification" to "Реклама Motorola (Hello You)", "com.motorola.brapps" to "Реклама Motorola",
        "com.motorola.genie" to "Справка Motorola", "com.motorola.help" to "Справка Motorola",
        "com.motorola.demo" to "Демо-режим", "com.motorola.spaces" to "Family Space", "com.motorola.familyspace" to "Family Space",
        "com.facebook.system" to "Сервис Facebook", "com.facebook.appmanager" to "Сервис Facebook",
        "com.facebook.services" to "Сервис Facebook", "com.facebook.lite" to "Facebook Lite",
        "com.amazon." to "Amazon", "com.booking" to "Booking", "com.einnovation.temu" to "Temu",
        "com.alibaba.aliexpresshd" to "AliExpress", "com.shein." to "Shein", "com.netflix." to "Netflix",
        "com.tripadvisor." to "Tripadvisor", "com.google.android.apps.subscriptions.red" to "Google One",
        "com.google.android.videos" to "Google TV", "com.google.android.apps.tachyon" to "Google Meet",
        "com.google.android.apps.youtube.music" to "YouTube Music",
    )
    private val adInstallers = setOf("com.dti.motorola", "com.digitalturbine.ignite", "com.ironsource.appcloud.oobe.motorola",
        "com.aura.oobe.motorola", "com.motorola.appdirectedsmspermission", "com.motorola.brapps")

    fun detect(ctx: Context, apps: List<AppEntry>): List<Junk> {
        val pm = ctx.packageManager
        return apps.mapNotNull { a ->
            val info = runCatching { pm.getApplicationInfo(a.pkg, 0) }.getOrNull()
            val system = info != null && (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val reason = known.entries.firstOrNull { a.pkg.startsWith(it.key) }?.value
                ?: runCatching {
                    val installer = if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(a.pkg).installingPackageName
                    else @Suppress("DEPRECATION") pm.getInstallerPackageName(a.pkg)
                    if (installer in adInstallers) "Поставлено рекламой Motorola" else null
                }.getOrNull()
            reason?.let { Junk(a, it, system) }
        }.sortedBy { it.app.label.lowercase() }
    }
}
