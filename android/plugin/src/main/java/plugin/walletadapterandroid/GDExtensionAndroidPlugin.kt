package plugin.walletadapterandroid

import plugin.walletadapterandroid.myResult
import plugin.walletadapterandroid.myAction
import plugin.walletadapterandroid.myStoredTransaction
import plugin.walletadapterandroid.myStoredTextMessage
import plugin.walletadapterandroid.myMessageSignature
import plugin.walletadapterandroid.myMessageSigningStatus
import plugin.walletadapterandroid.myConnectedKey
import plugin.walletadapterandroid.myConnectCluster
import plugin.walletadapterandroid.myIdentityName
import plugin.walletadapterandroid.myIdentityUri
import plugin.walletadapterandroid.myIconUri
import plugin.walletadapterandroid.mySignAndSendSignature
import plugin.walletadapterandroid.mySignAndSendStatus
import plugin.walletadapterandroid.mySiwsDomain
import plugin.walletadapterandroid.mySiwsStatement
import plugin.walletadapterandroid.mySiwsSignature
import plugin.walletadapterandroid.mySiwsSignedMessage
import plugin.walletadapterandroid.mySiwsPublicKey
import plugin.walletadapterandroid.mySiwsAccountLabel
import plugin.walletadapterandroid.mySiwsAccountChains
import plugin.walletadapterandroid.mySiwsAccountFeatures
import plugin.walletadapterandroid.mySiwsStatus
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult

import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import com.solana.mobilewalletadapter.clientlib.*

import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.os.Bundle
import android.content.Intent
import android.net.Uri

class GDExtensionAndroidPlugin(godot: Godot): GodotPlugin(godot) {

    companion object {
        val TAG = GDExtensionAndroidPlugin::class.java.simpleName

        init {
            try {
                Log.v(TAG, "Loading ${BuildConfig.GODOT_PLUGIN_NAME} library")
                System.loadLibrary(BuildConfig.GODOT_PLUGIN_NAME)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Unable to load ${BuildConfig.GODOT_PLUGIN_NAME} shared library")
            }
        }
    }

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginGDExtensionLibrariesPaths() = setOf("res://addons/${BuildConfig.GODOT_PLUGIN_NAME}/plugin.gdextension")

    @UsedByGodot
    fun connectWallet(cluster: Int, uri: String, icon: String, name: String) {
        if (myResult is TransactionResult.Success) {
            return
        }
        myIdentityUri = Uri.parse(uri);
        myIconUri = Uri.parse(icon);
        myIdentityName = name;
        myConnectCluster = cluster
        godot.getActivity()?.let {
            val intent = Intent(it, ComposeWalletActivity::class.java)
            it.startActivity(intent)
        }
    }

    @UsedByGodot
    fun getConnectionStatus(): Int{
        val myLocalResult = myResult
        if (myLocalResult == null) {
            return 0
        }
        else if(myLocalResult is TransactionResult.Success) {
            return 1
        }
        else{
            return 2
        }
    }

    @UsedByGodot
    fun getSigningStatus(): Int{
        return myMessageSigningStatus
    }

    @UsedByGodot
    fun getConnectedKey(): ByteArray?{
        myAction = 0
        return myConnectedKey?: ByteArray(0)
    }

    @UsedByGodot
    fun signTransaction(serializedTransaction: ByteArray){
        myAction = 1
        myStoredTransaction = serializedTransaction
        godot.getActivity()?.let {
            val intent = Intent(it, ComposeWalletActivity::class.java)
            it.startActivity(intent)
        }
    }
    
    @UsedByGodot
    fun signTextMessage(textMessage: String){
        myAction = 2
        myStoredTextMessage = textMessage
        godot.getActivity()?.let {
            val intent = Intent(it, ComposeWalletActivity::class.java)
            it.startActivity(intent)
        }
    }

    @UsedByGodot
    fun getMessageSignature(): ByteArray {
        return myMessageSignature?: ByteArray(0)
    }

    @UsedByGodot
    fun getLatestAction(): Int {
        return myAction
    }

    @UsedByGodot
    fun setIdentity(cluster: Int, uri: String, icon: String, name: String) {
        myConnectCluster = cluster
        myIdentityUri = Uri.parse(uri)
        myIconUri = Uri.parse(icon)
        myIdentityName = name
        Log.i("godot", "[KotlinPlugin] setIdentity | cluster=$cluster uri=$uri icon=$icon name=$name")
    }

    @UsedByGodot
    fun clearState() {
        Log.i("godot", "[KotlinPlugin] clearState | clearing status flags — keeping myResult/myConnectedKey/authToken for connection reuse")
        myMessageSigningStatus = 0
        mySignAndSendSignature = null
        mySignAndSendStatus = 0
        mySiwsSignature = null
        mySiwsSignedMessage = null
        mySiwsPublicKey = null
        mySiwsAccountLabel = null
        mySiwsAccountChains = ""
        mySiwsAccountFeatures = ""
        mySiwsStatus = 0
    }

    @UsedByGodot
    fun clearStateFullReset() {
        Log.i("godot", "[KotlinPlugin] clearStateFullReset | clearing myResult (was ${myResult?.javaClass?.simpleName}) + all status flags — next connectWallet will open fresh OS picker")
        myResult = null
        myMessageSigningStatus = 0
        mySignAndSendSignature = null
        mySignAndSendStatus = 0
        mySiwsSignature = null
        mySiwsSignedMessage = null
        mySiwsPublicKey = null
        mySiwsAccountLabel = null
        mySiwsAccountChains = ""
        mySiwsAccountFeatures = ""
        mySiwsStatus = 0
    }

    // ─── SIGN AND SEND TRANSACTIONS (MWA 2.0) ─────────────────────────────────

    @UsedByGodot
    fun signAndSendTransaction(serializedTransaction: ByteArray) {
        Log.i("godot", "[KotlinPlugin] signAndSendTransaction | ENTRY tx_size=${serializedTransaction.size} tx_hex=${serializedTransaction.joinToString("") { "%02x".format(it) }.take(80)} authToken_len=${authToken?.length ?: 0}")
        myAction = 3
        myStoredTransaction = serializedTransaction
        mySignAndSendSignature = null
        mySignAndSendStatus = 0
        godot.getActivity()?.let {
            Log.i("godot", "[KotlinPlugin] signAndSendTransaction | starting ComposeWalletActivity myAction=3")
            val intent = Intent(it, ComposeWalletActivity::class.java)
            it.startActivity(intent)
        } ?: Log.i("godot", "[KotlinPlugin] signAndSendTransaction | FAIL godot.getActivity() returned null")
    }

    @UsedByGodot
    fun getSignAndSendStatus(): Int {
        return mySignAndSendStatus
    }

    @UsedByGodot
    fun getSignAndSendResult(): ByteArray {
        val sig = mySignAndSendSignature ?: ByteArray(0)
        Log.i("godot", "[KotlinPlugin] getSignAndSendResult | sig_size=${sig.size} sig_hex=${sig.joinToString("") { "%02x".format(it) }.take(40)}")
        return sig
    }

    // ─── AUTHORIZE MWA 2.0 SIWS ───────────────────────────────────────────────

    @UsedByGodot
    fun connectWalletSiws(cluster: Int, uri: String, icon: String, name: String, domain: String, statement: String) {
        Log.i("godot", "[KotlinPlugin] connectWalletSiws | ENTRY cluster=$cluster uri=$uri icon=$icon name=$name domain=$domain statement=$statement")
        myConnectCluster = cluster
        myIdentityUri = Uri.parse(uri)
        myIconUri = Uri.parse(icon)
        myIdentityName = name
        mySiwsDomain = domain
        mySiwsStatement = statement
        myAction = 4
        // Reset all SIWS state
        mySiwsSignature = null
        mySiwsSignedMessage = null
        mySiwsPublicKey = null
        mySiwsAccountLabel = null
        mySiwsAccountChains = ""
        mySiwsAccountFeatures = ""
        mySiwsStatus = 0
        godot.getActivity()?.let {
            Log.i("godot", "[KotlinPlugin] connectWalletSiws | starting ComposeWalletActivity myAction=4")
            val intent = Intent(it, ComposeWalletActivity::class.java)
            it.startActivity(intent)
        } ?: Log.i("godot", "[KotlinPlugin] connectWalletSiws | FAIL godot.getActivity() returned null")
    }

    @UsedByGodot
    fun getSiwsStatus(): Int {
        return mySiwsStatus
    }

    @UsedByGodot
    fun getSiwsSignature(): ByteArray {
        val sig = mySiwsSignature ?: ByteArray(0)
        Log.i("godot", "[KotlinPlugin] getSiwsSignature | sig_size=${sig.size} sig_hex=${sig.joinToString("") { "%02x".format(it) }.take(40)}")
        return sig
    }

    @UsedByGodot
    fun getSiwsSignedMessage(): ByteArray {
        val msg = mySiwsSignedMessage ?: ByteArray(0)
        Log.i("godot", "[KotlinPlugin] getSiwsSignedMessage | msg_size=${msg.size} msg_hex=${msg.joinToString("") { "%02x".format(it) }.take(80)}")
        return msg
    }

    @UsedByGodot
    fun getSiwsPublicKey(): ByteArray {
        val pk = mySiwsPublicKey ?: ByteArray(0)
        Log.i("godot", "[KotlinPlugin] getSiwsPublicKey | pk_size=${pk.size} pk_hex=${pk.joinToString("") { "%02x".format(it) }}")
        return pk
    }

    @UsedByGodot
    fun getSiwsAccountLabel(): String {
        val label = mySiwsAccountLabel ?: ""
        Log.i("godot", "[KotlinPlugin] getSiwsAccountLabel | label=$label")
        return label
    }

    @UsedByGodot
    fun getSiwsAccountChains(): String {
        Log.i("godot", "[KotlinPlugin] getSiwsAccountChains | chains=$mySiwsAccountChains")
        return mySiwsAccountChains
    }

    @UsedByGodot
    fun getSiwsAccountFeatures(): String {
        Log.i("godot", "[KotlinPlugin] getSiwsAccountFeatures | features=$mySiwsAccountFeatures")
        return mySiwsAccountFeatures
    }

    // ─── PUBKEY HELPERS ──────────────────────────────────────────────────────

    @UsedByGodot
    fun getConnectedKeyBase58(): String {
        val key = myConnectedKey
        if (key == null || key.isEmpty()) {
            Log.i("godot", "[KotlinPlugin] getConnectedKeyBase58 | key_null=${key == null} key_size=${key?.size ?: 0} returning empty")
            return ""
        }
        val b58 = base58Encode(key)
        Log.i("godot", "[KotlinPlugin] getConnectedKeyBase58 | key_size=${key.size} key_hex=${key.joinToString("") { "%02x".format(it) }} base58=$b58")
        return b58
    }

    private fun base58Encode(input: ByteArray): String {
        val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        if (input.isEmpty()) return ""
        var zeros = 0
        for (b in input) { if (b.toInt() == 0) zeros++ else break }
        var value = java.math.BigInteger(1, input)
        val sb = StringBuilder()
        val fifty8 = java.math.BigInteger.valueOf(58)
        while (value > java.math.BigInteger.ZERO) {
            val divrem = value.divideAndRemainder(fifty8)
            value = divrem[0]
            sb.append(ALPHABET[divrem[1].toInt()])
        }
        repeat(zeros) { sb.append('1') }
        return sb.reverse().toString()
    }

    // ─── GET CAPABILITIES (STUB) ──────────────────────────────────────────────

    @UsedByGodot
    fun getCapabilitiesWallet() {
        Log.i("godot", "[KotlinPlugin] getCapabilitiesWallet | STUB — not implemented on this branch")
    }

    @UsedByGodot
    fun getCapabilitiesStatus(): Int {
        Log.i("godot", "[KotlinPlugin] getCapabilitiesStatus | STUB — returning 2 (error)")
        return 2
    }

    @UsedByGodot
    fun getCapabilitiesResult(): String {
        Log.i("godot", "[KotlinPlugin] getCapabilitiesResult | STUB — returning empty")
        return ""
    }
}
