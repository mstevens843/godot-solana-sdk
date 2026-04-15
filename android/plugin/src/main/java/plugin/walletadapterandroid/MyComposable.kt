package plugin.walletadapterandroid

import androidx.compose.runtime.Composable
import com.solana.mobilewalletadapter.clientlib.*
import androidx.compose.runtime.LaunchedEffect

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import androidx.activity.ComponentActivity
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import androidx.compose.ui.platform.LocalContext

import kotlinx.coroutines.runBlocking
import android.app.Activity
import android.content.Intent

import android.util.Log

import android.net.Uri

var myResult: TransactionResult<*>? = null
var myAction: Int = 0
var myStoredTransaction: ByteArray? = null
var myStoredTextMessage: String = ""
var myMessageSignature: ByteArray? = null
var myMessageSigningStatus: Int = 0
var myConnectedKey: ByteArray? = null
var myConnectCluster: Int = 0
var myIdentityUri: Uri = Uri.EMPTY
var myIconUri: Uri = Uri.EMPTY
var myIdentityName: String = ""

var authToken: String? = null

// MWA 2.0: signAndSendTransactions state
var mySignAndSendSignature: ByteArray? = null
var mySignAndSendStatus: Int = 0   // 0=pending, 1=success, 2=failure

@Composable
fun connectWallet(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        val connectionIdentity = ConnectionIdentity(
            identityUri = myIdentityUri,
            iconUri = myIconUri,
            identityName = myIdentityName
        )

        val walletAdapter = MobileWalletAdapter(connectionIdentity)
        when (myConnectCluster) {
            0 -> walletAdapter.blockchain = Solana.Devnet
            1 -> walletAdapter.blockchain = Solana.Mainnet
            2 -> walletAdapter.blockchain = Solana.Testnet
            else -> walletAdapter.blockchain = Solana.Devnet
        }
        val result = walletAdapter.connect(sender)

        when (result) {
            is TransactionResult.Success -> {
                // On success, an `AuthorizationResult` type is returned.
                authToken = result.authResult.authToken
                myConnectedKey = result.authResult.publicKey
            }
            is TransactionResult.NoWalletFound -> {
                println("No MWA compatible wallet app found on device.")
            }
            is TransactionResult.Failure -> {
                println("Error connecting to wallet: " + result.e.message)
            }
        }

        myResult = result
        activity?.finish()
    }
}

@Composable
fun signTransaction(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        
        val connectionIdentity = ConnectionIdentity(
            identityUri = myIdentityUri,
            iconUri = myIconUri,
            identityName = myIdentityName
        )

        val walletAdapter = MobileWalletAdapter(connectionIdentity)
        when (myConnectCluster) {
            0 -> walletAdapter.blockchain = Solana.Devnet
            1 -> walletAdapter.blockchain = Solana.Mainnet
            2 -> walletAdapter.blockchain = Solana.Testnet
            else -> walletAdapter.blockchain = Solana.Devnet
        }

        if(authToken != null){
            walletAdapter.authToken = authToken
        }

        val result = walletAdapter.transact(sender){
            signTransactions(arrayOf(myStoredTransaction ?: ByteArray(0)))
        }

        when (result) {
            is TransactionResult.Success -> {
                val signedTxBytes = result.successPayload?.signedPayloads?.first()
                signedTxBytes?.let {
                    myMessageSignature = signedTxBytes
                    myMessageSigningStatus = 1
                    println("Signed memo transaction:")
                }
            }
            is TransactionResult.NoWalletFound -> {
                myMessageSigningStatus = 2
                println("No MWA compatible wallet app found on device.")
            }
            is TransactionResult.Failure -> {
                myMessageSigningStatus = 2
                println("Error during transaction signing: " + result.e.message)
            }
        }

        myResult = result
        activity?.finish()
    }
}

@Composable
fun signTextMessage(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        
        val connectionIdentity = ConnectionIdentity(
            identityUri = myIdentityUri,
            iconUri = myIconUri,
            identityName = myIdentityName
        )

        val walletAdapter = MobileWalletAdapter(connectionIdentity)
        when (myConnectCluster) {
            0 -> walletAdapter.blockchain = Solana.Devnet
            1 -> walletAdapter.blockchain = Solana.Mainnet
            2 -> walletAdapter.blockchain = Solana.Testnet
            else -> walletAdapter.blockchain = Solana.Devnet
        }

        if(authToken != null){
            walletAdapter.authToken = authToken
        }

        if(myConnectedKey == null){
            myMessageSigningStatus = 2
            println("No connected key available for signing.")
            activity?.finish()
            return@LaunchedEffect
            
        }
        val result = walletAdapter.transact(sender){
            signMessagesDetached(arrayOf(myStoredTextMessage.toByteArray()), arrayOf(myConnectedKey!!))
        }

        when (result) {
            is TransactionResult.Success -> {
                val signedMessageBytes = result.successPayload?.messages?.first()?.signatures?.first()
                signedMessageBytes?.let {
                    myMessageSignature = signedMessageBytes
                    myMessageSigningStatus = 1
                }
            }
            is TransactionResult.NoWalletFound -> {
                myMessageSigningStatus = 2
                println("No MWA compatible wallet app found on device.")
            }
            is TransactionResult.Failure -> {
                myMessageSigningStatus = 2
                println("Error during message signing: " + result.e.message)
            }
        }

        myResult = result
        activity?.finish()
    }
}

// MWA 2.0: signAndSendTransactions — wallet signs AND broadcasts.
// Uses standalone CoroutineScope (not Compose lifecycle) because the transparent
// ComposeWalletActivity gets destroyed by Android while the wallet processes.
suspend fun signAndSendTransactionAsync(sender: ActivityResultSender, activity: Activity?) {
    Log.i("godot", "[signAndSendTransaction] ENTRY | tx_size=${myStoredTransaction?.size ?: 0} authToken_len=${authToken?.length ?: 0} cluster=$myConnectCluster")

    val connectionIdentity = ConnectionIdentity(
        identityUri = myIdentityUri,
        iconUri = myIconUri,
        identityName = myIdentityName
    )

    val walletAdapter = MobileWalletAdapter(connectionIdentity)
    when (myConnectCluster) {
        0 -> walletAdapter.blockchain = Solana.Devnet
        1 -> walletAdapter.blockchain = Solana.Mainnet
        2 -> walletAdapter.blockchain = Solana.Testnet
        else -> walletAdapter.blockchain = Solana.Devnet
    }

    if (authToken != null) {
        walletAdapter.authToken = authToken
    }

    try {
        val txPayload = myStoredTransaction ?: ByteArray(0)
        val result = walletAdapter.transact(sender) {
            Log.i("godot", "[signAndSendTransaction] INSIDE_TRANSACT | tx_size=${txPayload.size} skipPreflight=true")
            signAndSendTransactions(arrayOf(txPayload), TransactionParams(null, "confirmed", true, null, null))
        }

        when (result) {
            is TransactionResult.Success -> {
                authToken = result.authResult.authToken
                val firstSig = result.successPayload?.signatures?.firstOrNull()
                Log.i("godot", "[signAndSendTransaction] SUCCESS | sig_size=${firstSig?.size ?: 0}")
                firstSig?.let {
                    mySignAndSendSignature = it
                    mySignAndSendStatus = 1
                }
                if (firstSig == null) {
                    mySignAndSendStatus = 2
                }
            }
            is TransactionResult.NoWalletFound -> {
                Log.i("godot", "[signAndSendTransaction] NO_WALLET_FOUND")
                mySignAndSendStatus = 2
            }
            is TransactionResult.Failure -> {
                Log.i("godot", "[signAndSendTransaction] FAILURE | error=${result.e.message}")
                mySignAndSendStatus = 2
            }
        }
        myResult = result
    } catch (e: Exception) {
        Log.i("godot", "[signAndSendTransaction] EXCEPTION | error=${e.message} class=${e.javaClass.simpleName}")
        mySignAndSendStatus = 2
    }
    activity?.finish()
}