package com.bangkit.bisamerchant.data.merchantsetting.datasource

import android.net.Uri
import com.bangkit.bisamerchant.data.utils.SharedPreferences
import com.bangkit.bisamerchant.domain.merchantsetting.model.Merchant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantSettingDataSource @Inject constructor(
    private val pref: SharedPreferences,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) {
    suspend fun getMerchantActive(callback: (Merchant) -> Unit): ListenerRegistration {
        return withContext(Dispatchers.IO) {
            val query = db.collection("merchant").whereEqualTo("merchantActive", true)
                .whereEqualTo("email", auth.currentUser?.email)

            val listenerRegistration = query.addSnapshotListener { querySnapshot, _ ->
                querySnapshot?.let {
                    var data = Merchant()

                    for (document in querySnapshot.documents) {
                        runBlocking {
                            pref.saveMerchantId(document.id)
                        }
                        val id = document.id
                        val balance = document.getLong("balance")
                        val merchantActive = document.getBoolean("merchantActive")
                        val merchantLogo = document.getString("merchantLogo")
                        val merchantAddress = document.getString("merchantAddress")
                        val merchantType = document.getString("merchantType")
                        val email = document.getString("email")
                        val merchantName = document.getString("merchantName")
                        val transactionCount = document.getLong("transactionCount")

                        data = Merchant(
                            id,
                            balance,
                            merchantActive,
                            merchantLogo,
                            merchantAddress,
                            merchantType,
                            email,
                            merchantName,
                            transactionCount
                        )
                    }

                    callback(data)
                }
            }

            return@withContext listenerRegistration
        }
    }

    suspend fun updateMerchantActive(name: String, address: String, type: String, newPhoto: Uri?): Flow<String> = flow {
        val merchantId = pref.getMerchantId().first()
        val merchantDocument = db.collection("merchant").document(merchantId)

        if (newPhoto != null) {
            val storageRef = storage.reference
            val imageRef = storageRef.child("merchant/logo/$merchantId.jpg")

            try {
                imageRef.delete().await()

                val uploadTask = imageRef.putFile(newPhoto).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await()

                val data = mapOf(
                    "merchantName" to name,
                    "merchantLogo" to downloadUrl.toString(),
                    "merchantAddress" to address,
                    "merchantType" to type
                )

                merchantDocument.update(data).await()
                emit("Update merchant successful")
            } catch (e: Exception) {
                emit(e.localizedMessage ?: "Failed to update merchant")
            }
        } else {
            try {
                val data = mapOf(
                    "merchantName" to name,
                    "merchantAddress" to address,
                    "merchantType" to type
                )
                merchantDocument.update(data).await()
                emit("Update merchant successful")
            } catch (e: Exception) {
                emit(e.localizedMessage ?: "Failed to update merchant")
            }
        }
    }
}