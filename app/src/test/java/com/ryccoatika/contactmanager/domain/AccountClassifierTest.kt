package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.AccountCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountClassifierTest {
    @Test fun `google is full crud`() =
        assertEquals(AccountCapability.FULL_CRUD, AccountClassifier.classify("com.google"))

    @Test fun `device local null type is full crud`() =
        assertEquals(AccountCapability.FULL_CRUD, AccountClassifier.classify(null))

    @Test fun `samsung account is full crud`() =
        assertEquals(AccountCapability.FULL_CRUD, AccountClassifier.classify("com.osp.app.signin"))

    @Test fun `whatsapp is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("com.whatsapp"))

    @Test fun `telegram is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("org.telegram.messenger"))

    @Test fun `viber is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("com.viber.voip"))

    @Test fun `signal is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("org.thoughtcrime.securesms"))

    @Test fun `samsung sim account is sim`() =
        assertEquals(AccountCapability.SIM, AccountClassifier.classify("vnd.sec.contact.sim"))

    @Test fun `generic sim account is sim`() =
        assertEquals(AccountCapability.SIM, AccountClassifier.classify("com.android.contacts.sim"))

    @Test fun `unknown app account defaults to read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("com.some.random.app"))

    @Test fun `unknown but contains sim keyword is sim`() =
        assertEquals(AccountCapability.SIM, AccountClassifier.classify("vnd.xiaomi.contact.usim"))
}
