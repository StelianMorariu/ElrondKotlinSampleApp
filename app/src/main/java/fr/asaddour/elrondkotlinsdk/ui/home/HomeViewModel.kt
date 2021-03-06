package fr.asaddour.elrondkotlinsdk.ui.home

import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.elrond.erdkotlin.Exceptions
import com.elrond.erdkotlin.domain.wallet.models.Address
import com.elrond.erdkotlin.domain.wallet.models.Wallet
import com.elrond.erdkotlin.domain.account.models.Account
import com.elrond.erdkotlin.domain.account.GetAccountUsecase
import com.elrond.erdkotlin.domain.networkconfig.GetNetworkConfigUsecase
import com.elrond.erdkotlin.domain.transaction.*
import com.elrond.erdkotlin.domain.transaction.models.Transaction
import fr.asaddour.elrondkotlinsdk.domain.wallet.DeleteCurrentWalletUsecase
import fr.asaddour.elrondkotlinsdk.domain.wallet.LoadCurrentWalletUsecase
import fr.asaddour.elrondkotlinsdk.extentions.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class HomeViewModel @ViewModelInject constructor(
    private val loadCurrentWalletUsecase: LoadCurrentWalletUsecase,
    private val deleteCurrentWalletUsecase: DeleteCurrentWalletUsecase,
    private val getAccountUsecase: GetAccountUsecase,
    private val sendTransactionUsecase: SendTransactionUsecase,
    private val estimateCostOfTransactionUsecase: EstimateCostOfTransactionUsecase,
    private val getTransactionStatusUsecase: GetTransactionStatusUsecase,
    private val getTransactionInfoUsecase: GetTransactionInfoUsecase,
    private val getAddressTransactionsUsecase: GetAddressTransactionsUsecase,
    private val getNetworkConfigUsecase: GetNetworkConfigUsecase
) : ViewModel() {

    private val _viewState = MutableLiveData<HomeViewState>(HomeViewState.Loading)
    val viewState: LiveData<HomeViewState> = _viewState

    private var wallet: Wallet? = null
    private var account: Account? = null

    fun refreshData() {
        launch(Dispatchers.IO) {
            refreshDataNow()
        }
    }

    private fun refreshDataNow() {
        // load wallet only once
        val wallet = wallet ?: loadCurrentWalletUsecase.execute()?.also { wallet = it }

        // we don't have any wallet
        // lets create one
        wallet ?: run {
            _viewState.postValue(HomeViewState.OpenCreateWalletScreen)
            return
        }
        // load account
        account = getAccountUsecase.execute(Address.fromHex(wallet.publicKeyHex))
        val state = _viewState.value as? HomeViewState.Content
        account?.let { account ->
            _viewState.postValue(
                HomeViewState.Content(
                    walletContent = WalletContent(
                        address = account.address.bech32(),
                        balance = account.balance.toString(),
                        nonce = account.nonce.toString(),
                    ),
                    state?.sentTransaction
                )
            )
            logTransactions(account.address)

        }


    }

    private fun logTransactions(address: Address) {
        val transactions = getAddressTransactionsUsecase.execute(address)
        if (transactions.isNotEmpty()){
            val transaction = transactions.first()
            Log.d("HomeViewModel", "transaction:$transaction")
        }
        else {
            Log.d("HomeViewModel", "no transaction for address:${address}")
        }
    }

    fun sendTransaction(
        toAddress: String,
        amount: String?, // 1 xEGLD is 1000000000000000000
        message: String?
    ) {
        val receiverAddress = extractAddress(toAddress) ?: run {
            _viewState.value = HomeViewState.InvalidReceiverAddress
            return
        }

        val wallet = requireNotNull(wallet)
        val value = when (amount) {
            null -> 0.toBigInteger()
            else -> amount.toBigInteger()
        }
        launch(Dispatchers.IO) {
            refreshDataNow() // make sure we have the right once
            val networkConfig = getNetworkConfigUsecase.execute()
            val transaction = Transaction(
                sender = Address.fromHex(wallet.publicKeyHex),
                receiver = receiverAddress,
                value = value,
                data = message,
                chainID = networkConfig.chainID,
                gasPrice = networkConfig.minGasPrice,
                gasLimit = networkConfig.minGasLimit * 2,
                nonce = requireNotNull(account).nonce
            )
            val gas = estimateCostOfTransactionUsecase.execute(transaction)
            Log.d("alex", "gas: $gas")
            val sentTransaction = sendTransactionUsecase.execute(
                transaction = transaction,
                wallet
            )
            delay(500) // delay the call the avoid a crash, ideally we would be polling
            fetchTransactionStatus(txHash = sentTransaction.txHash)
        }

    }

    private fun extractAddress(toAddress: String) = try {
        when {
            Address.isValidBech32(toAddress) -> Address.fromBech32(toAddress)
            else -> Address.fromHex(toAddress)
        }
    } catch (e: Exceptions.AddressException) {
        e.printStackTrace()
        null
    } catch (e: Exceptions.BadAddressHrpException) {
        e.printStackTrace()
        null
    }

    fun disconnect() {
        launch(Dispatchers.IO) {
            wallet = null
            deleteCurrentWalletUsecase.execute()
        }
        _viewState.postValue(HomeViewState.OpenCreateWalletScreen)
    }

    fun fetchTransactionStatus(txHash: String) {
        Log.d("HomeViewModel", "fetchTransactionStatus: txHash:$txHash")
        launch(Dispatchers.IO) {
            val transactionStatus = getTransactionStatusUsecase.execute(txHash)
            val transactionInfo = getTransactionInfoUsecase.execute(txHash)
            Log.d("HomeViewModel", "transactionInfo: $transactionInfo")
            val state = _viewState.value as HomeViewState.Content
            _viewState.postValue(
                state.copy(
                    sentTransaction = SentTransaction(
                        txHash = txHash,
                        status = transactionStatus
                    )
                )
            )
        }

    }

    sealed class HomeViewState {
        object OpenCreateWalletScreen : HomeViewState()
        object Loading : HomeViewState()
        object InvalidReceiverAddress : HomeViewState()
        data class Content(
            val walletContent: WalletContent,
            val sentTransaction: SentTransaction?
        ) : HomeViewState()

    }

    data class WalletContent(
        val address: String,
        val balance: String,
        val nonce: String
    )

    data class SentTransaction(
        val txHash: String,
        val status: String,
    )

}
