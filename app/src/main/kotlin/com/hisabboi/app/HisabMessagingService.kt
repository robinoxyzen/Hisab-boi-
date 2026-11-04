package com.hisabboi.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hisabboi.model.LocalNotifier
import com.hisabboi.model.TokenRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HisabMessagingService : FirebaseMessagingService() {
    @Inject lateinit var tokens: TokenRepository
    @Inject lateinit var notifier: LocalNotifier
    @Inject lateinit var auth: FirebaseAuth

    override fun onNewToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch { runCatching { tokens.save(uid, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "পেমেন্ট সফল"
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        notifier.show(title, body, message.data["txId"])
    }
}
