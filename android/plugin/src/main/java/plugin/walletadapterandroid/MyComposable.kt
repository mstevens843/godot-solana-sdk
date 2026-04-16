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
        Log.i("godot", "[connectWallet] ENTRY | cluster=$myConnectCluster identityName=$myIdentityName identityUri=$myIdentityUri authToken_len=${authToken?.length ?: 0}")

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

        Log.i("godot", "[connectWallet] POST_CONNECT | result_class=${result.javaClass.simpleName}")

        when (result) {
            is TransactionResult.Success -> {
                authToken = result.authResult.authToken
                myConnectedKey = result.authResult.publicKey
                Log.i("godot", "[connectWallet] SUCCESS | authToken_len=${result.authResult.authToken.length} pubkey_size=${result.authResult.publicKey?.size ?: 0} pubkey_hex=${result.authResult.publicKey?.joinToString("") { "%02x".format(it) } ?: "null"}")
            }
            is TransactionResult.NoWalletFound -> {
                Log.i("godot", "[connectWallet] NO_WALLET_FOUND")
            }
            is TransactionResult.Failure -> {
                Log.i("godot", "[connectWallet] FAILURE | error=${result.e.message} error_class=${result.e.javaClass.simpleName}")
            }
        }

        myResult = result
        Log.i("godot", "[connectWallet] EXIT | result_class=${result.javaClass.simpleName}")
        activity?.finish()
    }
}

@Composable
fun signTransaction(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        Log.i("godot", "[signTransaction] ENTRY | tx_size=${myStoredTransaction?.size ?: 0} authToken_len=${authToken?.length ?: 0} cluster=$myConnectCluster")

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

        Log.i("godot", "[signTransaction] POST_TRANSACT | result_class=${result.javaClass.simpleName}")

        when (result) {
            is TransactionResult.Success -> {
                authToken = result.authResult.authToken
                val signedTxBytes = result.successPayload?.signedPayloads?.first()
                signedTxBytes?.let {
                    myMessageSignature = signedTxBytes
                    myMessageSigningStatus = 1
                    Log.i("godot", "[signTransaction] SUCCESS | authToken_len=${result.authResult.authToken.length} signedTx_size=${signedTxBytes.size} signedTx_hex=${signedTxBytes.joinToString("") { "%02x".format(it) }.take(80)}")
                }
                if (signedTxBytes == null) {
                    myMessageSigningStatus = 2
                    Log.i("godot", "[signTransaction] SUCCESS_BUT_NO_PAYLOAD | signedPayloads_null=${result.successPayload?.signedPayloads == null}")
                }
            }
            is TransactionResult.NoWalletFound -> {
                myMessageSigningStatus = 2
                Log.i("godot", "[signTransaction] NO_WALLET_FOUND")
            }
            is TransactionResult.Failure -> {
                myMessageSigningStatus = 2
                Log.i("godot", "[signTransaction] FAILURE | error=${result.e.message} error_class=${result.e.javaClass.simpleName}")
            }
        }

        myResult = result
        Log.i("godot", "[signTransaction] EXIT | status=$myMessageSigningStatus")
        activity?.finish()
    }
}

@Composable
fun signTextMessage(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        Log.i("godot", "[signTextMessage] ENTRY | message_len=${myStoredTextMessage.length} authToken_len=${authToken?.length ?: 0} connectedKey_size=${myConnectedKey?.size ?: 0} cluster=$myConnectCluster")

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
            Log.i("godot", "[signTextMessage] FAIL | connectedKey is null — cannot sign without a connected wallet")
            activity?.finish()
            return@LaunchedEffect
        }
        val result = walletAdapter.transact(sender){
            signMessagesDetached(arrayOf(myStoredTextMessage.toByteArray()), arrayOf(myConnectedKey!!))
        }

        Log.i("godot", "[signTextMessage] POST_TRANSACT | result_class=${result.javaClass.simpleName}")

        when (result) {
            is TransactionResult.Success -> {
                authToken = result.authResult.authToken
                val signedMessageBytes = result.successPayload?.messages?.first()?.signatures?.first()
                signedMessageBytes?.let {
                    myMessageSignature = signedMessageBytes
                    myMessageSigningStatus = 1
                    Log.i("godot", "[signTextMessage] SUCCESS | authToken_len=${result.authResult.authToken.length} sig_size=${signedMessageBytes.size} sig_hex=${signedMessageBytes.joinToString("") { "%02x".format(it) }.take(80)}")
                }
                if (signedMessageBytes == null) {
                    myMessageSigningStatus = 2
                    Log.i("godot", "[signTextMessage] SUCCESS_BUT_NO_SIG | messages_null=${result.successPayload?.messages == null}")
                }
            }
            is TransactionResult.NoWalletFound -> {
                myMessageSigningStatus = 2
                Log.i("godot", "[signTextMessage] NO_WALLET_FOUND")
            }
            is TransactionResult.Failure -> {
                myMessageSigningStatus = 2
                Log.i("godot", "[signTextMessage] FAILURE | error=${result.e.message} error_class=${result.e.javaClass.simpleName}")
            }
        }

        myResult = result
        Log.i("godot", "[signTextMessage] EXIT | status=$myMessageSigningStatus")
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
    val startTime = System.currentTimeMillis()
    Log.i("godot", "[signAndSendTransaction] ENTRY | elapsed=0ms thread=${Thread.currentThread().name} tx_size=${myStoredTransaction?.size ?: 0} tx_hex=${myStoredTransaction?.joinToString("") { "%02x".format(it) }?.take(80) ?: "null"} authToken_len=${authToken?.length ?: 0} cluster=$myConnectCluster identityName=$myIdentityName identityUri=$myIdentityUri scope=standalone activity_isDestroyed=${activity?.isDestroyed}")

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

    Log.i("godot", "[signAndSendTransaction] PRE_TRANSACT | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} blockchain=${walletAdapter.blockchain} authToken_set=${walletAdapter.authToken != null} authToken_len=${walletAdapter.authToken?.length ?: 0}")

    try {
        val txPayload = myStoredTransaction ?: ByteArray(0)
        val txPayloadBase64 = android.util.Base64.encodeToString(txPayload, android.util.Base64.NO_WRAP)
        Log.i("godot", "[signAndSendTransaction] TX_PAYLOAD | elapsed=${System.currentTimeMillis() - startTime}ms size=${txPayload.size} base64_len=${txPayloadBase64.length} base64=${txPayloadBase64.take(120)}")
        Log.i("godot", "[signAndSendTransaction] TX_PAYLOAD_HEX | full_hex=${txPayload.joinToString("") { "%02x".format(it) }}")
        Log.i("godot", "[signAndSendTransaction] CALLING_TRANSACT | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} activity_isDestroyed=${activity?.isDestroyed}")

        val result = walletAdapter.transact(sender) {
            Log.i("godot", "[signAndSendTransaction] INSIDE_TRANSACT | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} session_established calling signAndSendTransactions tx_size=${txPayload.size} skipPreflight=true commitment=confirmed activity_isDestroyed=${activity?.isDestroyed}")
            try {
                val signAndSendResult = signAndSendTransactions(arrayOf(txPayload), TransactionParams(null, "confirmed", true, null, null))
                Log.i("godot", "[signAndSendTransaction] SIGN_AND_SEND_RETURNED | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} result_class=${signAndSendResult.javaClass.simpleName}")
                Log.i("godot", "[signAndSendTransaction] SIGN_AND_SEND_RETURNED | signatures_null=${signAndSendResult.signatures == null} sig_count=${signAndSendResult.signatures?.size ?: 0}")
                if (signAndSendResult.signatures != null) {
                    for ((idx, sig) in signAndSendResult.signatures.withIndex()) {
                        Log.i("godot", "[signAndSendTransaction] SIGN_AND_SEND_SIG | index=$idx size=${sig?.size ?: 0} hex=${sig?.joinToString("") { "%02x".format(it) } ?: "null"}")
                    }
                }
                signAndSendResult
            } catch (innerEx: Exception) {
                Log.i("godot", "[signAndSendTransaction] INSIDE_TRANSACT_EXCEPTION | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} error=${innerEx.message} class=${innerEx.javaClass.simpleName}")
                Log.i("godot", "[signAndSendTransaction] INSIDE_TRANSACT_EXCEPTION_STACK | ${innerEx.stackTraceToString()}")
                throw innerEx
            }
        }

        Log.i("godot", "[signAndSendTransaction] POST_TRANSACT | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} result_class=${result.javaClass.simpleName}")

        when (result) {
            is TransactionResult.Success -> {
                authToken = result.authResult.authToken
                Log.i("godot", "[signAndSendTransaction] SUCCESS_AUTH | elapsed=${System.currentTimeMillis() - startTime}ms authToken_len=${result.authResult.authToken.length} pubkey_size=${result.authResult.publicKey?.size ?: 0}")
                val payload = result.successPayload
                val signatures = payload?.signatures
                val firstSig = signatures?.firstOrNull()
                Log.i("godot", "[signAndSendTransaction] SUCCESS | elapsed=${System.currentTimeMillis() - startTime}ms sig_count=${signatures?.size ?: 0} first_sig_size=${firstSig?.size ?: 0} first_sig_hex=${firstSig?.joinToString("") { "%02x".format(it) } ?: "null"}")
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
                Log.i("godot", "[signAndSendTransaction] NO_WALLET_FOUND | elapsed=${System.currentTimeMillis() - startTime}ms")
                mySignAndSendStatus = 2
            }
            is TransactionResult.Failure -> {
                Log.i("godot", "[signAndSendTransaction] FAILURE | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} error=${result.e.message} error_class=${result.e.javaClass.simpleName} cause=${result.e.cause?.message ?: "null"}")
                Log.i("godot", "[signAndSendTransaction] FAILURE_STACK | ${result.e.stackTraceToString()}")
                mySignAndSendStatus = 2
            }
        }

        Log.i("godot", "[signAndSendTransaction] EXIT | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} status=$mySignAndSendStatus sig_size=${mySignAndSendSignature?.size ?: 0} sig_hex=${mySignAndSendSignature?.joinToString("") { "%02x".format(it) } ?: "null"}")
        myResult = result
    } catch (e: Exception) {
        Log.i("godot", "[signAndSendTransaction] OUTER_EXCEPTION | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} error=${e.message} class=${e.javaClass.simpleName} cause=${e.cause?.message ?: "null"}")
        Log.i("godot", "[signAndSendTransaction] OUTER_EXCEPTION_STACK | ${e.stackTraceToString()}")
        mySignAndSendStatus = 2
    }
    Log.i("godot", "[signAndSendTransaction] PRE_FINISH | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} signAndSendStatus=$mySignAndSendStatus activity_isDestroyed=${activity?.isDestroyed}")
    activity?.finish()
}

// ─── AUTHORIZE MWA 2.0 (SIWS) ────────────────────────────────────────────────

@Composable
fun connectWalletSiws(sender: ActivityResultSender) {
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        Log.i("godot", "[connectWalletSiws] ENTRY | elapsed=0ms thread=${Thread.currentThread().name} domain=$mySiwsDomain statement=$mySiwsStatement cluster=$myConnectCluster identityName=$myIdentityName identityUri=$myIdentityUri authToken_len=${authToken?.length ?: 0} activity=${activity?.hashCode()} activity_isFinishing=${activity?.isFinishing} activity_isDestroyed=${activity?.isDestroyed}")

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
        Log.i("godot", "[connectWalletSiws] PRE_SIGNIN | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} blockchain=${walletAdapter.blockchain} payload_domain=${payload.domain} payload_statement=${payload.statement}")
        Log.i("godot", "[connectWalletSiws] CALLING_SIGNIN | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} sender=${sender.hashCode()} activity_isDestroyed=${activity?.isDestroyed}")

        try {
            val result = walletAdapter.signIn(sender, payload)

            Log.i("godot", "[connectWalletSiws] SIGNIN_RETURNED | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} result_class=${result.javaClass.simpleName} activity_isDestroyed=${activity?.isDestroyed}")

            when (result) {
                is TransactionResult.Success -> {
                    authToken = result.authResult.authToken
                    myConnectedKey = result.authResult.publicKey

                    val signInResult = result.authResult.signInResult ?: result.successPayload
                    Log.i("godot", "[connectWalletSiws] SIWS_SOURCE | elapsed=${System.currentTimeMillis() - startTime}ms authResult_signInResult_null=${result.authResult.signInResult == null} successPayload_null=${result.successPayload == null} using=${if (result.authResult.signInResult != null) "authResult" else "successPayload"}")
                    mySiwsSignature = signInResult?.signature
                    mySiwsSignedMessage = signInResult?.signedMessage
                    mySiwsPublicKey = signInResult?.publicKey

                    val firstAccount = result.authResult.accounts?.firstOrNull()
                    mySiwsAccountLabel = firstAccount?.accountLabel
                    mySiwsAccountChains = firstAccount?.chains?.joinToString(",") ?: ""
                    mySiwsAccountFeatures = firstAccount?.features?.joinToString(",") ?: ""

                    mySiwsStatus = 1

                    Log.i("godot", "[connectWalletSiws] SUCCESS | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} authToken_len=${result.authResult.authToken.length} pubkey_size=${result.authResult.publicKey?.size ?: 0} pubkey_hex=${result.authResult.publicKey?.joinToString("") { "%02x".format(it) } ?: "null"}")
                    Log.i("godot", "[connectWalletSiws] SUCCESS_SIWS | sig_size=${signInResult?.signature?.size ?: 0} sig_hex=${signInResult?.signature?.joinToString("") { "%02x".format(it) } ?: "null"} signedMsg_size=${signInResult?.signedMessage?.size ?: 0} signedMsg_hex=${signInResult?.signedMessage?.joinToString("") { "%02x".format(it) } ?: "null"}")
                    Log.i("godot", "[connectWalletSiws] SUCCESS_ACCOUNT | label=${firstAccount?.accountLabel} chains=${firstAccount?.chains?.joinToString(",") ?: "null"} features=${firstAccount?.features?.joinToString(",") ?: "null"} account_count=${result.authResult.accounts?.size ?: 0}")
                }
                is TransactionResult.NoWalletFound -> {
                    Log.i("godot", "[connectWalletSiws] NO_WALLET_FOUND | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name}")
                    mySiwsStatus = 2
                }
                is TransactionResult.Failure -> {
                    Log.i("godot", "[connectWalletSiws] FAILURE | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} error=${result.e.message} error_class=${result.e.javaClass.simpleName} cause=${result.e.cause?.message ?: "null"} cause_class=${result.e.cause?.javaClass?.simpleName ?: "null"}")
                    Log.i("godot", "[connectWalletSiws] FAILURE_STACK | ${result.e.stackTraceToString()}")
                    mySiwsStatus = 2
                }
            }

            Log.i("godot", "[connectWalletSiws] EXIT | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} status=$mySiwsStatus connectedKey_size=${myConnectedKey?.size ?: 0} authToken_len=${authToken?.length ?: 0} siwsSig_size=${mySiwsSignature?.size ?: 0}")
            myResult = result
        } catch (e: Exception) {
            Log.i("godot", "[connectWalletSiws] EXCEPTION | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} error=${e.message} class=${e.javaClass.simpleName} cause=${e.cause?.message ?: "null"} cause_class=${e.cause?.javaClass?.simpleName ?: "null"}")
            Log.i("godot", "[connectWalletSiws] EXCEPTION_STACK | ${e.stackTraceToString()}")
            mySiwsStatus = 2
        }
        Log.i("godot", "[connectWalletSiws] PRE_FINISH | elapsed=${System.currentTimeMillis() - startTime}ms thread=${Thread.currentThread().name} siwsStatus=$mySiwsStatus activity_isDestroyed=${activity?.isDestroyed}")
        activity?.finish()
    }
}
