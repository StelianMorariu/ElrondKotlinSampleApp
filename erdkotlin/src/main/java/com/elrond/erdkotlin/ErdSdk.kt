package com.elrond.erdkotlin

import com.elrond.erdkotlin.data.api.ElrondClient
import com.elrond.erdkotlin.data.account.AccountRepositoryImpl
import com.elrond.erdkotlin.data.api.ElrondProxy
import com.elrond.erdkotlin.data.networkconfig.NetworkConfigRepositoryImpl
import com.elrond.erdkotlin.data.transaction.TransactionRepositoryImpl
import com.elrond.erdkotlin.domain.account.GetAccountUsecase
import com.elrond.erdkotlin.domain.account.GetAddressBalanceUsecase
import com.elrond.erdkotlin.domain.account.GetAddressNonceUsecase
import com.elrond.erdkotlin.domain.networkconfig.GetNetworkConfigUsecase
import com.elrond.erdkotlin.domain.transaction.GetAddressTransactionsUsecase
import com.elrond.erdkotlin.domain.transaction.SendTransactionUsecase
import com.elrond.erdkotlin.domain.transaction.SignTransactionUsecase
import com.google.gson.Gson

// Implemented as an `object` because we are not using any dependency injection library
// We don't want to force the host app to use a specific library.
object ErdSdk {

    fun setNetwork(elrondNetwork: ElrondNetwork) {
        elrondClient.url = elrondNetwork.url()
    }

    fun getAccountUsecase() = GetAccountUsecase(accountRepository)
    fun getAddressNonce() = GetAddressNonceUsecase(accountRepository)
    fun getAddressBalance() = GetAddressBalanceUsecase(accountRepository)

    fun getNetworkConfigUsecase() = GetNetworkConfigUsecase(networkConfigRepository)

    fun sendTransactionUsecase() = SendTransactionUsecase(
        SignTransactionUsecase(),
        transactionRepository
    )

    fun getTransactionsUsecase() = GetAddressTransactionsUsecase(transactionRepository)

    private val gson = Gson()
    private val elrondClient = ElrondClient(ElrondNetwork.DevNet.url(), gson)
    private val elrondService = ElrondProxy(elrondClient, gson)
    private val networkConfigRepository = NetworkConfigRepositoryImpl(elrondService)
    private val accountRepository = AccountRepositoryImpl(elrondService)
    private val transactionRepository = TransactionRepositoryImpl(elrondService)
}

sealed class ElrondNetwork {
    object MainNet : ElrondNetwork()
    object DevNet : ElrondNetwork()
    object TestNet : ElrondNetwork()
    data class Custom(val url: String) : ElrondNetwork()

    fun url() = when (this) {
        MainNet -> "https://api.elrond.com"
        DevNet -> "https://devnet-api.elrond.com"
        TestNet -> "https://testnet-api.elrond.com"
        is Custom -> url
    }
}
