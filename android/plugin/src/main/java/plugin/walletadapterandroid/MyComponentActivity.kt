
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
        Log.i("godot", "[ComposeWalletActivity] onCreate | START myAction=$myAction thread=${Thread.currentThread().name} activity=${this.hashCode()} intent_data=$uri")

        super.onCreate(savedInstanceState)

        if (myAction == 0) {
            Log.i("godot", "[ComposeWalletActivity] onCreate | myAction=0 connectWallet — creating ActivityResultSender thread=${Thread.currentThread().name}")
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                connectWallet(sender)
            }
        }
        else if (myAction == 1) {
            Log.i("godot", "[ComposeWalletActivity] onCreate | myAction=1 signTransaction — creating ActivityResultSender thread=${Thread.currentThread().name}")
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                signTransaction(sender)
            }
        }
        else if (myAction == 2) {
            Log.i("godot", "[ComposeWalletActivity] onCreate | myAction=2 signTextMessage — creating ActivityResultSender thread=${Thread.currentThread().name}")
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                signTextMessage(sender)
            }
        }
        else if (myAction == 3) {
            Log.i("godot", "[ComposeWalletActivity] onCreate | myAction=3 getWalletCapabilities — creating ActivityResultSender thread=${Thread.currentThread().name}")
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                getWalletCapabilities(sender)
            }
        }
        else if (myAction == 5) {
            Log.i("godot", "[ComposeWalletActivity] onCreate | myAction=5 signAndSendTransaction — standalone scope (survives activity destruction) thread=${Thread.currentThread().name}")
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                signAndSendTransactionAsync(sender, this@ComposeWalletActivity)
            }
        }
        else if (myAction == 4) {
            Log.i("godot", "[ComposeWalletActivity] onCreate | myAction=4 connectWalletSiws — creating ActivityResultSender thread=${Thread.currentThread().name}")
            hasConnectedWallet = true
            val sender = ActivityResultSender(this)
            setContent {
                connectWalletSiws(sender)
            }
        }
        else {
            Log.i("godot", "[ComposeWalletActivity] onCreate | UNKNOWN myAction=$myAction thread=${Thread.currentThread().name}")
        }
        Log.i("godot", "[ComposeWalletActivity] onCreate | END myAction=$myAction thread=${Thread.currentThread().name}")
    }

    override fun onPause() {
        super.onPause()
        Log.i("godot", "[ComposeWalletActivity] onPause | myAction=$myAction thread=${Thread.currentThread().name} myResult=${myResult?.javaClass?.simpleName} siwsStatus=$mySiwsStatus signAndSendStatus=$mySignAndSendStatus signingStatus=$myMessageSigningStatus")
    }

    override fun onStop() {
        super.onStop()
        Log.i("godot", "[ComposeWalletActivity] onStop | myAction=$myAction thread=${Thread.currentThread().name} myResult=${myResult?.javaClass?.simpleName} siwsStatus=$mySiwsStatus signAndSendStatus=$mySignAndSendStatus")
    }

    override fun onDestroy() {
        Log.i("godot", "[ComposeWalletActivity] onDestroy | myAction=$myAction thread=${Thread.currentThread().name} myResult=${myResult?.javaClass?.simpleName} siwsStatus=$mySiwsStatus signAndSendStatus=$mySignAndSendStatus authToken_len=${authToken?.length ?: 0} connectedKey_size=${myConnectedKey?.size ?: 0} activity=${this.hashCode()}")
        super.onDestroy()
    }
}
