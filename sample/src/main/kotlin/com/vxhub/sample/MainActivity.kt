package com.vxhub.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxhub.VxHub
import com.vxhub.providers.VxBannerType
import com.vxhub.purchases.VxPurchaseResult
import com.vxhub.ui.VxHubUI
import com.vxhub.ui.auth.VxAuthResult
import com.vxhub.ui.auth.showAuth
import com.vxhub.ui.paywall.VxMainPaywallV4Configuration
import com.vxhub.ui.paywall.showPaywall
import com.vxhub.ui.promo.showPromoOffer
import com.vxhub.ui.store.VxStoreV1Configuration
import com.vxhub.ui.store.showStore
import com.vxhub.ui.support.showSupport
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SampleScreen(this) } }
    }
}

@Composable
private fun SampleScreen(activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    val state by VxHub.state.collectAsStateWithLifecycle()
    val isPremium by VxHub.isPremium.collectAsStateWithLifecycle()
    val balance by VxHub.balance.collectAsStateWithLifecycle()
    val products by VxHub.products.collectAsStateWithLifecycle()
    val connected by VxHub.isConnected.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("VxHub ${VxHub.SDK_VERSION}", style = MaterialTheme.typography.titleMedium)
                        Text("State: $state")
                        Text("Premium: $isPremium · Balance: $balance · Online: $connected")
                        Text("Device id: ${VxHub.deviceId}")
                    }
                }
            }
            item { Text("Products", style = MaterialTheme.typography.titleMedium) }
            items(products, key = { it.id }) { product ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            val message = when (val result = VxHub.purchase(activity, product)) {
                                is VxPurchaseResult.Verified -> "Purchased · premium=${result.isPremium}"
                                VxPurchaseResult.PendingVerification -> "Charged; verification will retry"
                                VxPurchaseResult.PaymentPending -> "Payment pending"
                                VxPurchaseResult.Cancelled -> "Cancelled"
                                is VxPurchaseResult.Failed -> "Failed: ${result.message}"
                            }
                            VxHub.showBanner(message, VxBannerType.INFO)
                        }
                    },
                ) { Text("${product.storeProduct.title} — ${product.storeProduct.formattedPrice}") }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ready-made screens", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { VxHubUI.showPaywall(activity, VxMainPaywallV4Configuration(), onResult = { VxHub.showBanner("Paywall: $it") }) }) {
                        Text("Paywall (V4)")
                    }
                    Button(onClick = { VxHubUI.showPromoOffer(activity, productToCompareIdentifier = null) { VxHub.showBanner("Promo purchased: $it") } }) {
                        Text("Promo offer")
                    }
                    Button(onClick = { VxHubUI.showStore(activity, VxStoreV1Configuration()) { purchased, id -> VxHub.showBanner("Store: $purchased $id") } }) {
                        Text("Coin store")
                    }
                    Button(onClick = { VxHubUI.showSupport(activity) }) { Text("Contact support") }
                    Button(
                        onClick = {
                            VxHubUI.showAuth(activity) { result ->
                                if (result is VxAuthResult.SignedIn) VxHub.showBanner("Signed in as ${result.user.email}")
                            }
                        },
                    ) { Text("Sign in") }
                    Text("Core", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { scope.launch { VxHub.showBanner("Restore: ${VxHub.restorePurchases()}", VxBannerType.INFO) } }) {
                        Text("Restore purchases")
                    }
                    OutlinedButton(onClick = { scope.launch { VxHub.showBanner("Spend: ${VxHub.spendCoins(1, "sample")}", VxBannerType.INFO) } }) {
                        Text("Spend 1 coin")
                    }
                    OutlinedButton(onClick = { VxHub.showEula(activity) }) { Text("Terms of use") }
                    OutlinedButton(onClick = { VxHub.showPrivacy(activity) }) { Text("Privacy policy") }
                    OutlinedButton(onClick = { scope.launch { VxHub.requestReview(activity) } }) { Text("Request review") }
                    OutlinedButton(onClick = { VxHub.trackEvent("sample_button", mapOf("source" to "sample")) }) { Text("Track event") }
                    OutlinedButton(onClick = { VxHub.showBanner("Hello from VxHub", VxBannerType.SUCCESS) }) { Text("Show banner") }
                }
            }
        }
    }
}
