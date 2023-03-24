package fastcampus.part5.chapter2.ui.main

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import fastcampus.part5.chapter2.viewmodel.MainViewModel
import fastcampus.part5.domain.model.AccountInfo

@Composable
fun MyPageScreen(viewModel: MainViewModel, googleSignInClient: GoogleSignInClient) {
    val accountInfo by viewModel.accountInfo.collectAsState()
    val firebaseAuth by lazy { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val startForResult = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val indent = result.data
            if (indent != null) {
                val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(indent)
                handleSignInResult(context, task, viewModel, firebaseAuth)
            }
        }
    }
    val kakaoCallback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        when {
            error != null -> {
                Log.e("Kakao", "카카오 계정 로그인 실패", error)
            }
            token != null -> {
                loginWithKakaoNickName(token, viewModel)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp)
    ) {
        if (accountInfo != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "로그인 유저 : ${accountInfo?.name}",
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )

                Button(onClick = {
                    viewModel.signOut()
                    when (accountInfo?.type){
                        AccountInfo.Type.KAKAO -> {
                            UserApiClient.instance.logout { }
                        }
                        AccountInfo.Type.GOOGLE -> {
                            firebaseAuth.signOut()
                        }
                    }
                }) {
                    Text(text = "로그아웃")
                }
            }

        } else {
            Button(onClick = {
                startForResult.launch(googleSignInClient.signInIntent)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "구글 로그인")
            }

            Button(onClick = {
                loginKakao(context, kakaoCallback)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "카카오 로그인")
            }
        }

    }
}

private fun loginWithKakaoNickName(token: OAuthToken, viewModel: MainViewModel) {
    UserApiClient.instance.me { user, error ->
        when {
            error != null -> {
                Log.e("Kakao", "사용저 정보 실패", error)
            }
            user != null -> {
                viewModel.signIn(AccountInfo(token.accessToken, user.properties?.get("nickname").orEmpty(), AccountInfo.Type.KAKAO))
            }
        }
    }
}

private fun loginKakao(context: Context, kakaoCallback: (OAuthToken?, Throwable?) -> Unit) {
    if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
        //카카오톡 설치
        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
            if (error != null) {
                Log.e("Kakao", "카카오톡 로그인 실패", error)
            }

            if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                return@loginWithKakaoTalk
            }

            UserApiClient.instance.loginWithKakaoAccount(context, callback = kakaoCallback)
        }
    } else {
        //카카오톡 미설치
        UserApiClient.instance.loginWithKakaoAccount(context, callback = kakaoCallback)
    }

}

private fun handleSignInResult(context: Context, accountTask: Task<GoogleSignInAccount>, viewModel: MainViewModel, firebaseAuth: FirebaseAuth) {
    try {
        val account = accountTask.result ?: return
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(context as Activity) { task ->
                if (task.isSuccessful) {
                    viewModel.signIn(AccountInfo(account.idToken.orEmpty(), account.displayName.orEmpty(), AccountInfo.Type.GOOGLE))
                } else {
                    viewModel.signOut()
                    firebaseAuth.signOut()
                }
            }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}