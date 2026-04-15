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

// getCapabilities state
var myCapabilitiesResult: String = ""
var myCapabilitiesStatus: Int = 0  // 0=pending, 1=success, 2=failed

// signAndSendTransactions state
var mySignAndSendSignature: ByteArray? = null
var mySignAndSendStatus: Int = 0   // 0=pending, 1=success, 2=failure

// SIWS (authorize MWA 2.0) state
var mySiwsDomain: String = ""
var mySiwsStatement: String = ""
var mySiwsSignature: ByteArray? = null
var mySiwsSignedMessage: ByteArray? = null
var mySiwsPublicKey: ByteArray? = null
var mySiwsAccountLabel: String? = null
var mySiwsAccountChains: String = ""
var mySiwsAccountFeatures: String = ""
var mySiwsStatus: Int = 0   // 0=pending, 1=success, 2=failure

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
                authToken = result.authResult.authToken
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
                authToken = result.authResult.authToken
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

// ─── GET CAPABILITIES (MWA 2.0) ──────────────────────────────────────────────

@Composable
fun getWalletCapabilities(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        Log.i("godot", "[KotlinPlugin] getWalletCapabilities | START authToken_len=${authToken?.length ?: -1} cluster=$myConnectCluster identity=$myIdentityName")

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
            Log.i("godot", "[KotlinPlugin] getWalletCapabilities | using cached authToken (len=${authToken?.length})")
        } else {
            Log.i("godot", "[KotlinPlugin] getWalletCapabilities | no authToken — wallet will need to authorize")
        }

        Log.i("godot", "[KotlinPlugin] getWalletCapabilities | calling walletAdapter.transact { getCapabilities() }")
        val result = walletAdapter.transact(sender) {
            getCapabilities()
        }

        Log.i("godot", "[KotlinPlugin] getWalletCapabilities | transact returned result_type=${result?.javaClass?.simpleName}")

        when (result) {
            is TransactionResult.Success -> {
                val caps = result.successPayload
                Log.i("godot", "[KotlinPlugin] getWalletCapabilities | SUCCESS payload_null=${caps == null}")
                if (caps != null) {
                    Log.i("godot", "[KotlinPlugin] getWalletCapabilities | maxTransactions=${caps.maxTransactionsPerSigningRequest} maxMessages=${caps.maxMessagesPerSigningRequest} supportsCloneAuth=${caps.supportsCloneAuthorization} supportsSignAndSend=${caps.supportsSignAndSendTransactions}")
                    Log.i("godot", "[KotlinPlugin] getWalletCapabilities | supportedVersions=${caps.supportedTransactionVersions?.joinToString(";") ?: "null"} optionalFeatures=${caps.supportedOptionalFeatures?.joinToString(";") ?: "null"}")
                    myCapabilitiesResult = "maxTransactions=${caps.maxTransactionsPerSigningRequest}" +
                        ",maxMessages=${caps.maxMessagesPerSigningRequest}" +
                        ",supportsCloneAuth=${caps.supportsCloneAuthorization}" +
                        ",supportsSignAndSend=${caps.supportsSignAndSendTransactions}" +
                        ",supportedVersions=${caps.supportedTransactionVersions?.joinToString(";") ?: ""}" +
                        ",optionalFeatures=${caps.supportedOptionalFeatures?.joinToString(";") ?: ""}"
                    myCapabilitiesStatus = 1
                    Log.i("godot", "[KotlinPlugin] getWalletCapabilities | STORED result='$myCapabilitiesResult' status=1")
                } else {
                    myCapabilitiesResult = ""
                    myCapabilitiesStatus = 2
                    Log.i("godot", "[KotlinPlugin] getWalletCapabilities | FAIL payload was null despite Success result")
                }
            }
            is TransactionResult.NoWalletFound -> {
                myCapabilitiesStatus = 2
                Log.i("godot", "[KotlinPlugin] getWalletCapabilities | FAIL NoWalletFound — no MWA compatible wallet app on device")
            }
            is TransactionResult.Failure -> {
                myCapabilitiesStatus = 2
                Log.i("godot", "[KotlinPlugin] getWalletCapabilities | FAIL error=${result.e.message} exception=${result.e.javaClass?.simpleName}")
            }
        }

        Log.i("godot", "[KotlinPlugin] getWalletCapabilities | DONE status=$myCapabilitiesStatus finishing activity")
        activity?.finish()
    }
}

// ─── SIGN AND SEND TRANSACTIONS (MWA 2.0) ─────────────────────────────────

// signAndSendTransaction as a suspend function — NOT tied to Compose lifecycle.
// The ComposeWalletActivity is transparent and gets destroyed by Android after ~19s
// when Phantom opens. LaunchedEffect coroutines die with the activity. This suspend
// function runs in a standalone CoroutineScope that survives activity destruction.
// The MWA WebSocket session continues independently — we just need the coroutine alive
// to receive the result.
suspend fun signAndSendTransactionAsync(sender: ActivityResultSender, activity: Activity?) {
    Log.i("godot", "[signAndSendTransaction] ENTRY | tx_size=${myStoredTransaction?.size ?: 0} tx_hex=${myStoredTransaction?.joinToString("") { "%02x".format(it) }?.take(80) ?: "null"} authToken_len=${authToken?.length ?: 0} cluster=$myConnectCluster identityName=$myIdentityName identityUri=$myIdentityUri scope=standalone")

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

    Log.i("godot", "[signAndSendTransaction] PRE_TRANSACT | blockchain=${walletAdapter.blockchain} authToken_set=${walletAdapter.authToken != null} authToken_len=${walletAdapter.authToken?.length ?: 0}")

    try {
        val txPayload = myStoredTransaction ?: ByteArray(0)
        val txPayloadBase64 = android.util.Base64.encodeToString(txPayload, android.util.Base64.NO_WRAP)
        Log.i("godot", "[signAndSendTransaction] TX_PAYLOAD | size=${txPayload.size} base64_len=${txPayloadBase64.length} base64=${txPayloadBase64.take(120)}")
        Log.i("godot", "[signAndSendTransaction] TX_PAYLOAD_HEX | full_hex=${txPayload.joinToString("") { "%02x".format(it) }}")

        val result = walletAdapter.transact(sender) {
            Log.i("godot", "[signAndSendTransaction] INSIDE_TRANSACT | session_established calling signAndSendTransactions tx_size=${txPayload.size} skipPreflight=true commitment=confirmed")
            try {
                val signAndSendResult = signAndSendTransactions(arrayOf(txPayload), TransactionParams(null, "confirmed", true, null, null))
                Log.i("godot", "[signAndSendTransaction] SIGN_AND_SEND_RETURNED | result_class=${signAndSendResult.javaClass.simpleName} result=$signAndSendResult")
                Log.i("godot", "[signAndSendTransaction] SIGN_AND_SEND_RETURNED | signatures_null=${signAndSendResult.signatures == null} sig_count=${signAndSendResult.signatures?.size ?: 0}")
                if (signAndSendResult.signatures != null) {
                    for ((idx, sig) in signAndSendResult.signatures.withIndex()) {
                        Log.i("godot", "[signAndSendTransaction] SIGN_AND_SEND_SIG | index=$idx size=${sig?.size ?: 0} hex=${sig?.joinToString("") { "%02x".format(it) } ?: "null"}")
                    }
                }
                signAndSendResult
            } catch (innerEx: Exception) {
                Log.i("godot", "[signAndSendTransaction] INSIDE_TRANSACT_EXCEPTION | error=${innerEx.message} class=${innerEx.javaClass.simpleName} stack=${innerEx.stackTraceToString().take(500)}")
                throw innerEx
            }
        }

        Log.i("godot", "[signAndSendTransaction] POST_TRANSACT | result_class=${result.javaClass.simpleName}")

        when (result) {
            is TransactionResult.Success -> {
                authToken = result.authResult.authToken
                Log.i("godot", "[signAndSendTransaction] SUCCESS_AUTH | authToken_len=${result.authResult.authToken.length} pubkey_size=${result.authResult.publicKey?.size ?: 0}")
                val payload = result.successPayload
                Log.i("godot", "[signAndSendTransaction] SUCCESS_PAYLOAD | payload_null=${payload == null} payload_class=${payload?.javaClass?.simpleName ?: "null"}")
                val signatures = payload?.signatures
                val firstSig = signatures?.firstOrNull()
                Log.i("godot", "[signAndSendTransaction] SUCCESS | sig_count=${signatures?.size ?: 0} first_sig_size=${firstSig?.size ?: 0} first_sig_hex=${firstSig?.joinToString("") { "%02x".format(it) } ?: "null"}")
                firstSig?.let {
                    mySignAndSendSignature = it
                    mySignAndSendStatus = 1
                }
                if (firstSig == null) {
                    Log.i("godot", "[signAndSendTransaction] SUCCESS_BUT_NO_SIG | signatures_null=${signatures == null} signatures_empty=${signatures?.isEmpty()}")
                    mySignAndSendStatus = 2
                }
            }
            is TransactionResult.NoWalletFound -> {
                Log.i("godot", "[signAndSendTransaction] NO_WALLET_FOUND")
                mySignAndSendStatus = 2
            }
            is TransactionResult.Failure -> {
                Log.i("godot", "[signAndSendTransaction] FAILURE | error=${result.e.message} error_class=${result.e.javaClass.simpleName} stacktrace=${result.e.stackTraceToString().take(500)}")
                mySignAndSendStatus = 2
            }
        }

        Log.i("godot", "[signAndSendTransaction] EXIT | status=$mySignAndSendStatus sig_size=${mySignAndSendSignature?.size ?: 0} sig_hex=${mySignAndSendSignature?.joinToString("") { "%02x".format(it) } ?: "null"}")
        myResult = result
    } catch (e: Exception) {
        Log.i("godot", "[signAndSendTransaction] OUTER_EXCEPTION | error=${e.message} class=${e.javaClass.simpleName} cause=${e.cause?.message ?: "null"} cause_class=${e.cause?.javaClass?.simpleName ?: "null"} stacktrace=${e.stackTraceToString().take(600)}")
        mySignAndSendStatus = 2
    }
    activity?.finish()
}

// ─── AUTHORIZE MWA 2.0 (SIWS) ────────────────────────────────────────────────

@Composable
fun connectWalletSiws(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        Log.i("godot", "[connectWalletSiws] ENTRY | domain=$mySiwsDomain statement=$mySiwsStatement cluster=$myConnectCluster identityName=$myIdentityName identityUri=$myIdentityUri authToken_len=${authToken?.length ?: 0}")

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

        val payload = SignInWithSolana.Payload(mySiwsDomain, mySiwsStatement)
        Log.i("godot", "[connectWalletSiws] PRE_SIGNIN | blockchain=${walletAdapter.blockchain} payload_domain=${payload.domain} payload_statement=${payload.statement}")

        try {
            val result = walletAdapter.signIn(sender, payload)

            Log.i("godot", "[connectWalletSiws] POST_SIGNIN | result_class=${result.javaClass.simpleName}")

            when (result) {
                is TransactionResult.Success -> {
                    authToken = result.authResult.authToken
                    myConnectedKey = result.authResult.publicKey

                    // SIWS data: try authResult.signInResult first, fall back to successPayload
                    val signInResult = result.authResult.signInResult ?: result.successPayload
                    Log.i("godot", "[connectWalletSiws] SIWS_SOURCE | authResult_signInResult_null=${result.authResult.signInResult == null} successPayload_null=${result.successPayload == null} using=${if (result.authResult.signInResult != null) "authResult" else "successPayload"}")
                    mySiwsSignature = signInResult?.signature
                    mySiwsSignedMessage = signInResult?.signedMessage
                    mySiwsPublicKey = signInResult?.publicKey

                    val firstAccount = result.authResult.accounts?.firstOrNull()
                    mySiwsAccountLabel = firstAccount?.accountLabel
                    mySiwsAccountChains = firstAccount?.chains?.joinToString(",") ?: ""
                    mySiwsAccountFeatures = firstAccount?.features?.joinToString(",") ?: ""

                    mySiwsStatus = 1

                    Log.i("godot", "[connectWalletSiws] SUCCESS | authToken_len=${result.authResult.authToken.length} pubkey_size=${result.authResult.publicKey?.size ?: 0} pubkey_hex=${result.authResult.publicKey?.joinToString("") { "%02x".format(it) } ?: "null"}")
                    Log.i("godot", "[connectWalletSiws] SUCCESS_SIWS | sig_size=${signInResult?.signature?.size ?: 0} sig_hex=${signInResult?.signature?.joinToString("") { "%02x".format(it) } ?: "null"} signedMsg_size=${signInResult?.signedMessage?.size ?: 0} signedMsg_hex=${signInResult?.signedMessage?.joinToString("") { "%02x".format(it) }?.take(80) ?: "null"}")
                    Log.i("godot", "[connectWalletSiws] SUCCESS_ACCOUNT | label=${firstAccount?.accountLabel} chains=${firstAccount?.chains?.joinToString(",") ?: "null"} features=${firstAccount?.features?.joinToString(",") ?: "null"} account_count=${result.authResult.accounts?.size ?: 0}")
                }
                is TransactionResult.NoWalletFound -> {
                    Log.i("godot", "[connectWalletSiws] NO_WALLET_FOUND")
                    mySiwsStatus = 2
                }
                is TransactionResult.Failure -> {
                    Log.i("godot", "[connectWalletSiws] FAILURE | error=${result.e.message} error_class=${result.e.javaClass.simpleName} stacktrace=${result.e.stackTraceToString().take(300)}")
                    mySiwsStatus = 2
                }
            }

            Log.i("godot", "[connectWalletSiws] EXIT | status=$mySiwsStatus connectedKey_size=${myConnectedKey?.size ?: 0} authToken_len=${authToken?.length ?: 0} siwsSig_size=${mySiwsSignature?.size ?: 0}")
            myResult = result
        } catch (e: Exception) {
            Log.i("godot", "[connectWalletSiws] EXCEPTION | error=${e.message} class=${e.javaClass.simpleName} stacktrace=${e.stackTraceToString().take(400)}")
            mySiwsStatus = 2
        }
        activity?.finish()
    }
}
