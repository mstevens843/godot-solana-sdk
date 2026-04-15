
package plugin.walletadapterandroid

import plugin.walletadapterandroid.connectWallet
import plugin.walletadapterandroid.myAction
import com.solana.mobilewalletadapter.clientlib.*

import android.os.Bundle
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ComposeWalletActivity : ComponentActivity() {
    private var hasConnectedWallet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val uri = intent?.data

        super.onCreate(savedInstanceState)

        if (myAction == 0) {
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                connectWallet(sender)
            }
        }
        else if (myAction == 1) {
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                signTransaction(sender)
            }
        }
        else if (myAction == 2) {
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                signTextMessage(sender)
            }
        }
        else if (myAction == 3) {
            // MWA 2.0: signAndSendTransactions — standalone scope survives activity destruction
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                signAndSendTransactionAsync(sender, this@ComposeWalletActivity)
            }
        }
    }
}
