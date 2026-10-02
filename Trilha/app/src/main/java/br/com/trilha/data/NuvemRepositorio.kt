package br.com.trilha.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import br.com.trilha.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/** Host do Firebase Hosting onde o convite é publicado — mesmo valor do intent-filter em AndroidManifest.xml. */
const val CONVITE_HOST = "porki-41b23.web.app"

/**
 * Locais compartilhados vivem no Firestore, não no trilha.json local — são os únicos dados
 * do app que saem do aparelho, exatamente para permitir que outra pessoa os veja no dela.
 */
class NuvemRepositorio {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val usuarioAtual: FirebaseUser? get() = auth.currentUser

    fun observarUsuario(): Flow<FirebaseUser?> = callbackFlow {
        val ouvinte = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(ouvinte)
        awaitClose { auth.removeAuthStateListener(ouvinte) }
    }

    suspend fun entrarComGoogle(activity: Activity): Boolean {
        val webClientId = activity.getString(R.string.default_web_client_id)
        val opcaoGoogle = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val pedido = GetCredentialRequest.Builder().addCredentialOption(opcaoGoogle).build()
        val resposta = CredentialManager.create(activity).getCredential(activity, pedido)
        val credencial = resposta.credential
        if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleCred = GoogleIdTokenCredential.createFrom(credencial.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            auth.signInWithCredential(firebaseCred).await()
            return true
        }
        return false
    }

    fun sair() = auth.signOut()

    fun observarLocais(uid: String): Flow<List<Local>> = callbackFlow {
        val registro = firestore.collection("locais")
            .whereArrayContains("membros", uid)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) { close(erro); return@addSnapshotListener }
                val locais = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.data?.let { dados -> runCatching { mapaParaLocal(doc.id, dados) }.getOrNull() }
                }
                trySend(locais)
            }
        awaitClose { registro.remove() }
    }

    suspend fun criarLocal(nome: String, uid: String, nomeExibicao: String): Local {
        val local = Local(
            nome = nome.ifBlank { "Novo local" },
            pessoas = listOf(Pessoa(id = uid, nome = nomeExibicao)),
            membros = listOf(uid)
        )
        gravar(local)
        return local
    }

    suspend fun salvarLocal(local: Local) = gravar(local)

    private suspend fun gravar(local: Local) {
        @Suppress("UNCHECKED_CAST")
        val mapa = localParaMapa(local) as Map<String, Any>
        firestore.collection("locais").document(local.id).set(mapa).await()
    }

    suspend fun gerarConvite(localId: String): String {
        val token = novoId()
        firestore.collection("convites").document(token).set(mapOf("localId" to localId)).await()
        firestore.collection("locais").document(localId).update("conviteAtual", token).await()
        return "https://$CONVITE_HOST/convite/$token"
    }

    /** Retorna o id do Local em caso de sucesso, ou null se o token for inválido/expirado. */
    suspend fun entrarPeloConvite(token: String, uid: String, nomeExibicao: String): String? = runCatching {
        val convite = firestore.collection("convites").document(token).get().await()
        val localId = convite.getString("localId") ?: return@runCatching null

        @Suppress("UNCHECKED_CAST")
        val pessoaMapa = json.encodeToJsonElement(Pessoa.serializer(), Pessoa(id = uid, nome = nomeExibicao))
            .paraQualquer() as Map<String, Any>

        firestore.collection("locais").document(localId).update(
            mapOf(
                "membros" to FieldValue.arrayUnion(uid),
                "pessoas" to FieldValue.arrayUnion(pessoaMapa),
                "conviteAtual" to token
            )
        ).await()
        localId
    }.getOrNull()
}

/* ===================== conversão Local <-> mapa do Firestore ===================== */

private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

private fun JsonElement.paraQualquer(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> entries.associate { (k, v) -> k to v.paraQualquer() }
    is JsonArray -> map { it.paraQualquer() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> boolean
        longOrNull != null -> long
        else -> double
    }
}

private fun Any?.paraJson(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.paraJson() })
    is List<*> -> JsonArray(map { it.paraJson() })
    else -> JsonPrimitive(this.toString())
}

private fun localParaMapa(local: Local): Any? =
    json.encodeToJsonElement(Local.serializer(), local).paraQualquer()

private fun mapaParaLocal(id: String, dados: Map<String, Any?>): Local {
    val elemento = (dados + ("id" to id)).paraJson()
    return json.decodeFromJsonElement(Local.serializer(), elemento)
}
