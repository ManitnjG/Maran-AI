package ai.maran.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context:Context){
 private val prefs=context.getSharedPreferences("protected_connection",Context.MODE_PRIVATE)
 private val alias="maran_connection_token"
 private fun key():SecretKey{
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (store.getKey(alias,null) as? SecretKey)?.let{return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
   init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun save(value:String){
  if(value.isBlank()){prefs.edit().clear().apply();return}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
  val encrypted=cipher.doFinal(value.toByteArray(Charsets.UTF_8))
  prefs.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("value",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
 }
 fun read():String=try{
  val value=prefs.getString("value",null)
  val iv=prefs.getString("iv",null)
  if(value==null||iv==null)"" else {
   val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)))}
   String(cipher.doFinal(Base64.decode(value,Base64.NO_WRAP)),Charsets.UTF_8)
  }
 }catch(_:Exception){""}
}
