package com.ryccoatika.contactmanager.di

import com.ryccoatika.contactmanager.data.AccountRepository
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.AndroidStringProvider
import com.ryccoatika.contactmanager.data.AppPrefs
import com.ryccoatika.contactmanager.data.BatchOperationManager
import com.ryccoatika.contactmanager.data.BatchRunner
import com.ryccoatika.contactmanager.data.ContactsRepository
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriteRepository
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.DataStoreAppPrefs
import com.ryccoatika.contactmanager.data.DataStoreDuplicatePrefs
import com.ryccoatika.contactmanager.data.DuplicatePrefs
import com.ryccoatika.contactmanager.data.SimAwareContactsWriter
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.data.billing.Billing
import com.ryccoatika.contactmanager.data.billing.BillingManager
import com.ryccoatika.contactmanager.data.ops.DefaultMoveAllContacts
import com.ryccoatika.contactmanager.data.ops.MoveAllContacts
import com.ryccoatika.contactmanager.data.sim.DataStoreSimCapabilityCache
import com.ryccoatika.contactmanager.data.sim.DefaultSimSubscriptionsSource
import com.ryccoatika.contactmanager.data.sim.IccSimAccountsIntegration
import com.ryccoatika.contactmanager.data.sim.IccSimSource
import com.ryccoatika.contactmanager.data.sim.SimAccountsIntegration
import com.ryccoatika.contactmanager.data.sim.SimCapabilityCache
import com.ryccoatika.contactmanager.data.sim.SimContactSource
import com.ryccoatika.contactmanager.data.sim.SimRepository
import com.ryccoatika.contactmanager.data.sim.SimStore
import com.ryccoatika.contactmanager.data.sim.SimSubscriptionsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** The raw ContactsContract writer, used as the inner delegate of the SIM-aware decorator. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ContactsContractWriter

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun bindContactsSource(impl: ContactsRepository): ContactsSource

    @Binds abstract fun bindAccountsSource(impl: AccountRepository): AccountsSource

    /** SIM-aware decorator; ContactsWriteRepository stays the ContactsContract inner impl. */
    @Binds abstract fun bindContactsWriter(impl: SimAwareContactsWriter): ContactsWriter

    @Binds
    @ContactsContractWriter
    abstract fun bindContactsContractWriter(impl: ContactsWriteRepository): ContactsWriter

    @Binds abstract fun bindDuplicatePrefs(impl: DataStoreDuplicatePrefs): DuplicatePrefs

    @Binds abstract fun bindAppPrefs(impl: DataStoreAppPrefs): AppPrefs

    @Binds abstract fun bindStringProvider(impl: AndroidStringProvider): StringProvider

    @Binds abstract fun bindSimContactSource(impl: IccSimSource): SimContactSource

    @Binds abstract fun bindSimCapabilityCache(impl: DataStoreSimCapabilityCache): SimCapabilityCache

    @Binds abstract fun bindSimSubscriptionsSource(impl: DefaultSimSubscriptionsSource): SimSubscriptionsSource

    @Binds abstract fun bindSimAccountsIntegration(impl: IccSimAccountsIntegration): SimAccountsIntegration

    @Binds abstract fun bindBatchRunner(impl: BatchOperationManager): BatchRunner

    @Binds abstract fun bindSimStore(impl: SimRepository): SimStore

    @Binds abstract fun bindBilling(impl: BillingManager): Billing

    @Binds abstract fun bindMoveAllContacts(impl: DefaultMoveAllContacts): MoveAllContacts
}
