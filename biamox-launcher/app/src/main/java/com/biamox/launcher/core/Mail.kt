package com.biamox.launcher.core

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress

data class MailHeader(
    val uid: Long,
    val from: String,
    val subject: String,
    val date: String,
    val seen: Boolean,
)

data class MailboxStatus(val mailbox: Mailbox, val unread: Int?, val error: String?)

/** Чтение почты по IMAP (Gmail: нужен «пароль приложения»). */
class MailService(private val secrets: Secrets) {

    private fun connect(mb: Mailbox): Store {
        val pass = secrets.mailPassword(mb.address)
        if (pass.isBlank()) throw IllegalStateException("Нет пароля для ${mb.address}")
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", mb.imapHost)
            put("mail.imaps.port", mb.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "20000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(mb.imapHost, mb.imapPort, mb.address, pass)
        return store
    }

    suspend fun status(mb: Mailbox): MailboxStatus = withContext(Dispatchers.IO) {
        try {
            connect(mb).use { store ->
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                val n = inbox.unreadMessageCount
                inbox.close(false)
                MailboxStatus(mb, n, null)
            }
        } catch (e: Exception) {
            MailboxStatus(mb, null, e.message)
        }
    }

    suspend fun latest(mb: Mailbox, count: Int = 30): List<MailHeader> = withContext(Dispatchers.IO) {
        connect(mb).use { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val total = inbox.messageCount
            if (total == 0) return@use emptyList()
            val msgs = inbox.getMessages(maxOf(1, total - count + 1), total)
            val fp = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS); add(UIDFolder.FetchProfileItem.UID)
            }
            inbox.fetch(msgs, fp)
            val uf = inbox as UIDFolder
            val df = SimpleDateFormat("dd.MM HH:mm", Locale("ru"))
            val res = msgs.reversed().map { m ->
                MailHeader(
                    uid = uf.getUID(m),
                    from = (m.from?.firstOrNull() as? InternetAddress)?.let { it.personal ?: it.address } ?: "—",
                    subject = m.subject ?: "(без темы)",
                    date = m.sentDate?.let { df.format(it) } ?: "",
                    seen = m.isSet(Flags.Flag.SEEN),
                )
            }
            inbox.close(false)
            res
        }
    }

    suspend fun body(mb: Mailbox, uid: Long): String = withContext(Dispatchers.IO) {
        connect(mb).use { store ->
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE) // чтение письма пометит его прочитанным
            val m: Message = (inbox as UIDFolder).getMessageByUID(uid) ?: return@use "Письмо не найдено"
            val text = extract(m) ?: "(не удалось прочитать содержимое)"
            inbox.close(false)
            text.trim()
        }
    }

    private fun extract(p: Part): String? {
        if (p.isMimeType("text/plain")) return p.content as? String
        if (p.isMimeType("text/html")) {
            val html = p.content as? String ?: return null
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
        }
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as Multipart
            var html: String? = null
            for (i in 0 until mp.count) {
                val bp = mp.getBodyPart(i)
                if (bp.isMimeType("text/plain")) return bp.content as? String
                if (html == null) html = extract(bp)
            }
            return html
        }
        return null
    }

    private inline fun <T> Store.use(block: (Store) -> T): T = try { block(this) } finally { runCatching { close() } }
}
