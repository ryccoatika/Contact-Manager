package com.ryccoatika.contactmanager.di

import com.ryccoatika.contactmanager.data.AccountRepository
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactsRepository
import com.ryccoatika.contactmanager.data.ContactsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun bindContactsSource(impl: ContactsRepository): ContactsSource
    @Binds abstract fun bindAccountsSource(impl: AccountRepository): AccountsSource
}
